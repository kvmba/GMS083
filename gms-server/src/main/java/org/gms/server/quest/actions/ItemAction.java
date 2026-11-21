/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.server.quest.actions;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.constants.inventory.ItemConstants;
import org.gms.util.I18nUtil;
import org.gms.util.InventoryOperationBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.provider.Data;
import org.gms.provider.DataTool;
import org.gms.server.ItemInformationProvider;
import org.gms.server.quest.Quest;
import org.gms.server.quest.QuestActionType;
import org.gms.util.PacketCreator;
import org.gms.util.Pair;
import org.gms.util.Randomizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Tyler (Twdtwd)
 * @author Ronan
 */
public class ItemAction extends AbstractQuestAction {
    private static final Logger log = LoggerFactory.getLogger(ItemAction.class);
    List<ItemData> items = new ArrayList<>();

    public ItemAction(Quest quest, Data data) {
        super(QuestActionType.ITEM, quest);
        processData(data);
    }


    @Override
    public void processData(Data data) {
        for (Data iEntry : data.getChildren()) {
            int id = DataTool.getInt(iEntry.getChildByPath("id"));
            int count = DataTool.getInt(iEntry.getChildByPath("count"), 1);
            int period = DataTool.getInt(iEntry.getChildByPath("period"), 0);

            Integer prop = null;
            Data propData = iEntry.getChildByPath("prop");
            if (propData != null) {
                prop = DataTool.getInt(propData);
            }

            int gender = 2;
            if (iEntry.getChildByPath("gender") != null) {
                gender = DataTool.getInt(iEntry.getChildByPath("gender"));
            }

            int job = -1;
            if (iEntry.getChildByPath("job") != null) {
                job = DataTool.getInt(iEntry.getChildByPath("job"));
            }

            items.add(new ItemData(Integer.parseInt(iEntry.getName()), id, count, prop, job, gender, period));
        }

        items.sort((o1, o2) -> o1.map - o2.map);
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        List<ItemData> takeItem = new ArrayList<>();
        List<ItemData> giveItem = new ArrayList<>();

        int props = 0, rndProps = 0, accProps = 0;
        for (ItemData item : items) {
            if (item.getProp() != null && item.getProp() != -1 && canGetItem(item, chr)) {
                props += item.getProp();
            }
        }

        int extNum = 0;
        if (props > 0) {
            rndProps = Randomizer.nextInt(props);
        }
        for (ItemData iEntry : items) {
            if (!canGetItem(iEntry, chr)) {
                continue;
            }

            if (iEntry.getProp() != null) {
                if (iEntry.getProp() == -1) {
                    if (extSelection == null || extSelection != extNum++) {
                        continue;
                    }
                } else {
                    accProps += iEntry.getProp();

                    if (accProps <= rndProps) {
                        continue;
                    } else {
                        accProps = Integer.MIN_VALUE;
                    }
                }
            }

            if (iEntry.getCount() < 0) { // Remove Item
                takeItem.add(iEntry);
            } else {                    // Give Item
                giveItem.add(iEntry);
            }
        }

        // 扣除和发放各自结算，避免在同一个包内吞掉道具数量净变化。
        applyItems(chr, takeItem);
        applyItems(chr, giveItem);
    }

    private static void applyItems(Character chr, List<ItemData> items) {
        Client c = chr.getClient();
        List<ItemData> notices = new ArrayList<>();
        int next = 0;
        while (next < items.size()) {
            ItemData item = items.get(next);
            InventoryType type = ItemConstants.getInventoryType(item.getId());
            if (!canBatch(type, item)) {
                if (applySingleItem(chr, c, type, item)) {
                    announceItem(chr, item);
                }
                next++;
                continue;
            }

            Inventory inventory = chr.getInventory(type);
            inventory.lockInventory();
            try (var changes = new InventoryOperationBatch(c, true)) {
                do {
                    int count = item.getCount();
                    if (count < 0) {
                        InventoryManipulator.removeById(c, inventory, item.getId(), -count, changes);
                        notices.add(item);
                    } else if (InventoryManipulator.addById(c, inventory, item.getId(), (short) count, grantExpiration(item), changes)) {
                        notices.add(item);
                    }
                    if (++next == items.size()) {
                        break;
                    }
                    item = items.get(next);
                } while (ItemConstants.getInventoryType(item.getId()) == type && canBatch(type, item));
            } finally {
                inventory.unlockInventory();
                // 只通知已处理的条目，且同步包先于提示；后续条目失败时也不丢前面的通知。
                for (ItemData notice : notices) {
                    announceItem(chr, notice);
                }
                notices.clear();
            }
        }
    }

    private static boolean canBatch(InventoryType type, ItemData item) {
        // 装备扣除可能回退到负槽；现金栏可能涉及宠物，保留原流程。
        return type.isStandardInventory() && (type != InventoryType.EQUIP || item.getCount() >= 0);
    }

    private static boolean applySingleItem(Character chr, Client c, InventoryType type, ItemData item) {
        int itemId = item.getId();
        int count = item.getCount();
        if (count < 0) {
            int quantity = -count;
            if (type == InventoryType.EQUIP && chr.getInventory(type).countById(itemId) < quantity
                    && chr.getInventory(InventoryType.EQUIPPED).countById(itemId) >= quantity) {
                type = InventoryType.EQUIPPED;
            }
            InventoryManipulator.removeById(c, type, itemId, quantity, true, false);
            return true;
        }
        return InventoryManipulator.addById(c, itemId, (short) count, "", -1, grantExpiration(item));
    }

    private static long grantExpiration(ItemData item) {
        int period = item.getPeriod();
        return period > 0 ? System.currentTimeMillis() + MINUTES.toMillis(period) : -1;
    }

    private static void announceItem(Character chr, ItemData item) {
        chr.sendPacket(PacketCreator.getShowItemGain(item.getId(), (short) item.getCount(), true));
    }

    @Override
    public boolean check(Character chr, Integer extSelection) {
        List<Pair<Item, InventoryType>> gainList = new LinkedList<>();
        List<Pair<Item, InventoryType>> selectList = new LinkedList<>();
        List<Pair<Item, InventoryType>> randomList = new LinkedList<>();

        List<Integer> allSlotUsed = new ArrayList(5);
        for (byte i = 0; i < 5; i++) {
            allSlotUsed.add(0);
        }

        for (ItemData item : items) {
            if (!canGetItem(item, chr)) {
                continue;
            }

            InventoryType type = ItemConstants.getInventoryType(item.getId());
            if (item.getProp() != null) {
                Item toItem = new Item(item.getId(), (short) 0, (short) item.getCount());

                if (item.getProp() < 0) {
                    selectList.add(new Pair<>(toItem, type));
                } else {
                    randomList.add(new Pair<>(toItem, type));
                }

            } else {
                // Make sure they can hold the item.
                Item toItem = new Item(item.getId(), (short) 0, (short) item.getCount());
                gainList.add(new Pair<>(toItem, type));

                if (item.getCount() < 0) {
                    // Make sure they actually have the item.
                    int quantity = item.getCount() * -1;

                    int freeSlotCount = chr.getInventory(type).freeSlotCountById(item.getId(), quantity);
                    if (freeSlotCount == -1) {
                        if (type.equals(InventoryType.EQUIP) && chr.getInventory(InventoryType.EQUIPPED).countById(item.getId()) >= quantity) {
                            continue;
                        }

                        announceInventoryLimit(Collections.singletonList(item.getId()), chr);
                        return false;
                    } else {
                        int idx = type.getType() - 1;   // more slots available from the given items!
                        allSlotUsed.set(idx, allSlotUsed.get(idx) - freeSlotCount);
                    }
                }
            }
        }

        if (!randomList.isEmpty()) {
            int result;
            Client c = chr.getClient();

            List<Integer> rndUsed = new ArrayList(5);
            for (byte i = 0; i < 5; i++) {
                rndUsed.add(allSlotUsed.get(i));
            }

            for (Pair<Item, InventoryType> it : randomList) {
                int idx = it.getRight().getType() - 1;

                result = InventoryManipulator.checkSpaceProgressively(c, it.getLeft().getItemId(), it.getLeft().getQuantity(), "", rndUsed.get(idx), false);
                if (result % 2 == 0) {
                    announceInventoryLimit(Collections.singletonList(it.getLeft().getItemId()), chr);
                    return false;
                }

                allSlotUsed.set(idx, Math.max(allSlotUsed.get(idx), result >> 1));
            }
        }

        if (!selectList.isEmpty()) {
            if (extSelection != null && extSelection >= 0 && extSelection < selectList.size()) {
                Pair<Item, InventoryType> selected = selectList.get(extSelection);
                gainList.add(selected);
            }
        }

        if (!canHold(chr, gainList)) {
            List<Integer> gainItemids = new LinkedList<>();
            for (Pair<Item, InventoryType> it : gainList) {
                gainItemids.add(it.getLeft().getItemId());
            }

            announceInventoryLimit(gainItemids, chr);
            return false;
        }
        return true;
    }

    private void announceInventoryLimit(List<Integer> itemids, Character chr) {
        for (Integer id : itemids) {
            if (ItemInformationProvider.getInstance().isPickupRestricted(id) && chr.haveItemWithId(id, true)) {
                chr.dropMessage(1, "请检查你的背包中是否已有类似的唯一物品。");
                return;
            }
        }

        chr.dropMessage(1, I18nUtil.getMessage("ItemAction.Message1"));
    }

    private boolean canHold(Character chr, List<Pair<Item, InventoryType>> gainList) {
        List<Integer> toAddItemids = new LinkedList<>();
        List<Integer> toAddQuantity = new LinkedList<>();
        List<Integer> toRemoveItemids = new LinkedList<>();
        List<Integer> toRemoveQuantity = new LinkedList<>();

        for (Pair<Item, InventoryType> item : gainList) {
            Item it = item.getLeft();

            if (it.getQuantity() > 0) {
                toAddItemids.add(it.getItemId());
                toAddQuantity.add((int) it.getQuantity());
            } else {
                toRemoveItemids.add(it.getItemId());
                toRemoveQuantity.add(-1 * ((int) it.getQuantity()));
            }
        }

        // thanks onechord for noticing quests unnecessarily giving out "full inventory" from quests that also takes items from players
        return chr.getAbstractPlayerInteraction().canHoldAllAfterRemoving(toAddItemids, toAddQuantity, toRemoveItemids, toRemoveQuantity);
    }

    private boolean canGetItem(ItemData item, Character chr) {
        if (item.getGender() != 2 && item.getGender() != chr.getGender()) {
            return false;
        }

        if (item.job > 0) {
            final List<Integer> code = getJobBy5ByteEncoding(item.getJob());
            boolean jobFound = false;
            for (int codec : code) {
                if (codec / 100 == chr.getJob().getId() / 100) {
                    jobFound = true;
                    break;
                }
            }
            return jobFound;
        }

        return true;
    }

    public boolean restoreLostItem(Character chr, int itemid) {
        if (!ItemInformationProvider.getInstance().isQuestItem(itemid)) {
            return false;
        }

        // thanks danielktran (MapleHeroesD)
        for (ItemData item : items) {
            if (item.getId() == itemid) {
                int missingQty = item.getCount() - chr.countItem(itemid);
                if (missingQty > 0) {
                    if (!chr.canHold(itemid, missingQty)) {
                        chr.dropMessage(1, I18nUtil.getMessage("ItemAction.Message1"));
                        return false;
                    }

                    InventoryManipulator.addById(chr.getClient(), item.getId(), (short) missingQty);
                    log.debug("Chr {} obtained {}x {} from questId {}", chr, itemid, missingQty, questID);
                }
                return true;
            }
        }

        return false;
    }

    private class ItemData {
        private final int map, id, count, job, gender, period;
        private final Integer prop;

        public ItemData(int map, int id, int count, Integer prop, int job, int gender, int period) {
            this.map = map;
            this.id = id;
            this.count = count;
            this.prop = prop;
            this.job = job;
            this.gender = gender;
            this.period = period;
        }

        public int getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public Integer getProp() {
            return prop;
        }

        public int getJob() {
            return job;
        }

        public int getGender() {
            return gender;
        }

        public int getPeriod() {
            return period;
        }
    }
} 
