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
package org.gms.client.inventory.manipulator;

import org.gms.client.BuffStat;
import org.gms.client.Character;
import org.gms.client.Ring;
import org.gms.client.Client;
import org.gms.client.inventory.Equip;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.ModifyInventory;
import org.gms.client.inventory.Pet;
import org.gms.model.pojo.NewYearCardRecord;
import org.gms.config.GameConfig;
import org.gms.constants.id.ItemId;
import org.gms.constants.inventory.ItemConstants;
import org.gms.util.I18nUtil;
import org.gms.util.InventoryOperationBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gms.server.ItemInformationProvider;
import org.gms.server.maps.MapleMap;
import org.gms.util.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matze
 * @author Ronan - improved check space feature and removed redundant object calls
 */
public class InventoryManipulator {
    private static final Logger log = LoggerFactory.getLogger(InventoryManipulator.class);

    public static boolean addById(Client c, int itemId, short quantity) {
        return addById(c, itemId, quantity, null, -1, -1);
    }

    public static boolean addById(Client c, int itemId, short quantity, long expiration) {
        return addById(c, itemId, quantity, null, -1, (byte) 0, expiration);
    }

    public static boolean addById(Client c, int itemId, short quantity, String owner, int petid) {
        return addById(c, itemId, quantity, owner, petid, -1);
    }

    public static boolean addById(Client c, int itemId, short quantity, String owner, int petid, long expiration) {
        return addById(c, itemId, quantity, owner, petid, (byte) 0, expiration);
    }

    public static boolean addById(Client c, int itemId, short quantity, String owner, int petid, short flag, long expiration) {
        Character chr = c.getPlayer();
        InventoryType type = ItemConstants.getInventoryType(itemId);

        Inventory inv = chr.getInventory(type);
        inv.lockInventory();
        try (var changes = new InventoryOperationBatch(c, true)) {
            return addByIdInternal(c, chr, inv, itemId, quantity, owner, petid, flag, expiration, changes);
        } finally {
            inv.unlockInventory();
        }
    }

    /** 调用方持有所属背包锁，直到批量变更入队。 */
    public static boolean addById(Client c, Inventory inv, int itemId, short quantity, long expiration, InventoryOperationBatch changes) {
        return addByIdInternal(c, c.getPlayer(), inv, itemId, quantity, "", -1, (short) 0, expiration, changes);
    }

    private static boolean addByIdInternal(Client c, Character chr, Inventory inv, int itemId, short quantity,
                                           String owner, int petid, short flag, long expiration, InventoryOperationBatch changes) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (inv.getType() != InventoryType.EQUIP) {
            if (ItemConstants.isRechargeable(itemId) || petid != -1) {
                Item item = new Item(itemId, (short) 0, quantity, petid);
                item.setFlag(flag);
                item.setExpiration(expiration);
                return addNewItem(c, chr, inv, item, changes);
            }

            short slotMax = ii.getSlotMax(c, itemId);
            if (quantity > 0 && slotMax <= 0) {
                changes.flush();
                c.sendPacket(PacketCreator.enableActions());
                return false;
            }
            for (Item stack : inv.listById(itemId)) {
                if (quantity <= 0) {
                    break;
                }
                short oldQuantity = stack.getQuantity();
                if (oldQuantity < slotMax && (owner == null || stack.getOwner().equals(owner)) && stack.getFlag() == flag) {
                    short newQuantity = (short) Math.min(oldQuantity + quantity, slotMax);
                    quantity -= newQuantity - oldQuantity;
                    long oldExpiration = stack.getExpiration();
                    stack.setQuantity(newQuantity);
                    stack.setExpiration(expiration);
                    // 083 的数量操作不带有效期；变化时用 mode 0 覆盖原槽。
                    changes.add(stack.getExpiration() == oldExpiration ? 1 : 0, stack);
                }
            }
            while (quantity > 0) {
                short newQuantity = (short) Math.min(quantity, slotMax);
                Item item = new Item(itemId, (short) 0, newQuantity);
                item.setFlag(flag);
                item.setExpiration(expiration);
                if (owner != null) {
                    item.setOwner(owner);
                }
                if (!addNewItem(c, chr, inv, item, changes)) {
                    return false;
                }
                quantity -= newQuantity;
            }
            return true;
        }

        if (quantity != 1) {
            throw new RuntimeException("Trying to create equip with non-one quantity");
        }
        Item equip = ii.getEquipById(itemId);
        equip.setFlag(flag);
        equip.setExpiration(expiration);
        if (owner != null) {
            equip.setOwner(owner);
        }
        return addNewItem(c, chr, inv, equip, changes);
    }

    private static boolean addNewItem(Client c, Character chr, Inventory inv, Item item, InventoryOperationBatch changes) {
        if (inv.addItem(item) == -1) {
            changes.flush();
            c.sendPacket(PacketCreator.getInventoryFull());
            c.sendPacket(PacketCreator.getShowInventoryFull());
            return false;
        }
        changes.add(0, item);
        if (isSandboxItem(item)) {
            chr.setHasSandboxItem();
        }
        return true;
    }

    public static boolean addFromDrop(Client c, Item item) {
        return addFromDrop(c, item, true);
    }

    public static boolean addFromDrop(Client c, Item item, boolean show) {
        return addFromDrop(c, item, show, item.getPetId());
    }

    public static boolean addFromDrop(Client c, Item item, boolean show, int petId) {
        Character chr = c.getPlayer();
        InventoryType type = item.getInventoryType();

        Inventory inv = chr.getInventory(type);
        inv.lockInventory();
        try {
            return addFromDropInternal(c, chr, type, inv, item, show, petId);
        } finally {
            inv.unlockInventory();
        }
    }

    private static boolean addFromDropInternal(Client c, Character chr, InventoryType type, Inventory inv, Item item, boolean show, int petId) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        int itemid = item.getItemId();
        if (ii.isPickupRestricted(itemid) && chr.haveItemWithId(itemid, true)) {
            c.sendPacket(PacketCreator.getInventoryFull());
            c.sendPacket(PacketCreator.showItemUnavailable());
            return false;
        }
        short quantity = item.getQuantity();

        if (!type.equals(InventoryType.EQUIP)) {
            if (!ItemConstants.isRechargeable(itemid) && petId == -1) {
                short slotMax = ii.getSlotMax(c, itemid);
                if (quantity > 0 && slotMax <= 0) {
                    c.sendPacket(PacketCreator.enableActions());
                    return false;
                }
                try (var changes = new InventoryOperationBatch(c, true)) {
                    for (Item eItem : inv.listById(itemid)) {
                        if (quantity <= 0) {
                            break;
                        }
                        short oldQ = eItem.getQuantity();
                        if (oldQ < slotMax && item.getFlag() == eItem.getFlag() && item.getOwner().equals(eItem.getOwner())) {
                            short newQ = (short) Math.min(oldQ + quantity, slotMax);
                            quantity -= (newQ - oldQ);
                            eItem.setQuantity(newQ);
                            item.setPosition(eItem.getPosition());
                            changes.add(1, eItem);
                        }
                    }
                    while (quantity > 0) {
                        short newQ = (short) Math.min(quantity, slotMax);
                        quantity -= newQ;
                        Item nItem = new Item(itemid, (short) 0, newQ, petId);
                        nItem.setExpiration(item.getExpiration());
                        nItem.setOwner(item.getOwner());
                        nItem.setFlag(item.getFlag());
                        short newSlot = inv.addItem(nItem);
                        if (newSlot == -1) {
                            changes.flush();
                            c.sendPacket(PacketCreator.getInventoryFull());
                            c.sendPacket(PacketCreator.getShowInventoryFull());
                            item.setQuantity((short) (quantity + newQ));
                            return false;
                        }
                        item.setPosition(newSlot);
                        changes.add(0, nItem);
                        if (InventoryManipulator.isSandboxItem(nItem)) {
                            chr.setHasSandboxItem();
                        }
                    }
                }
            } else {
                Item nItem = new Item(itemid, (short) 0, quantity, petId);
                nItem.setExpiration(item.getExpiration());
                nItem.setFlag(item.getFlag());

                short newSlot = inv.addItem(nItem);
                if (newSlot == -1) {
                    c.sendPacket(PacketCreator.getInventoryFull());
                    c.sendPacket(PacketCreator.getShowInventoryFull());
                    return false;
                }
                nItem.setPosition(newSlot);
                item.setPosition(newSlot);
                c.sendPacket(PacketCreator.modifyInventory(true, Collections.singletonList(new ModifyInventory(0, nItem))));
                if (InventoryManipulator.isSandboxItem(nItem)) {
                    chr.setHasSandboxItem();
                }
                c.sendPacket(PacketCreator.enableActions());
            }
        } else if (quantity == 1) {
            short newSlot = inv.addItem(item);
            if (newSlot == -1) {
                c.sendPacket(PacketCreator.getInventoryFull());
                c.sendPacket(PacketCreator.getShowInventoryFull());
                return false;
            }
            item.setPosition(newSlot);
            c.sendPacket(PacketCreator.modifyInventory(true, Collections.singletonList(new ModifyInventory(0, item))));
            if (InventoryManipulator.isSandboxItem(item)) {
                chr.setHasSandboxItem();
            }
        } else {
            log.warn("Tried to pickup Equip id {} containing more than 1 quantity --> {}", itemid, quantity);
            c.sendPacket(PacketCreator.getInventoryFull());
            c.sendPacket(PacketCreator.showItemUnavailable());
            return false;
        }
        if (show) {
            c.sendPacket(PacketCreator.getShowItemGain(itemid, item.getQuantity()));
        }
        return true;
    }

    private static boolean haveItemWithId(Inventory inv, int itemid) {
        return inv.findById(itemid) != null;
    }

    public static boolean checkSpace(Client c, int itemid, int quantity, String owner) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        InventoryType type = ItemConstants.getInventoryType(itemid);
        Character chr = c.getPlayer();
        Inventory inv = chr.getInventory(type);

        if (ii.isPickupRestricted(itemid)) {
            if (haveItemWithId(inv, itemid)) {
                return false;
            } else if (ItemConstants.isEquipment(itemid) && haveItemWithId(chr.getInventory(InventoryType.EQUIPPED), itemid)) {
                return false;
            }
        }

        if (!type.equals(InventoryType.EQUIP)) {
            short slotMax = ii.getSlotMax(c, itemid);
            List<Item> existing = inv.listById(itemid);

            final int numSlotsNeeded;
            if (ItemConstants.isRechargeable(itemid)) {
                numSlotsNeeded = 1;
            } else {
                if (existing.size() > 0) // first update all existing slots to slotMax
                {
                    for (Item eItem : existing) {
                        short oldQ = eItem.getQuantity();
                        if (oldQ < slotMax && owner.equals(eItem.getOwner())) {
                            short newQ = (short) Math.min(oldQ + quantity, slotMax);
                            quantity -= (newQ - oldQ);
                        }
                        if (quantity <= 0) {
                            break;
                        }
                    }
                }

                if (slotMax > 0) {
                    numSlotsNeeded = (int) (Math.ceil(((double) quantity) / slotMax));
                } else {
                    numSlotsNeeded = 1;
                }
            }

            return !inv.isFull(numSlotsNeeded - 1);
        } else {
            return !inv.isFull();
        }
    }

    public static int checkSpaceProgressively(Client c, int itemid, int quantity, String owner, int usedSlots, boolean useProofInv) {
        // return value --> bit0: if has space for this one;
        //                  value after: new slots filled;
        // assumption: equipments always have slotMax == 1.

        int returnValue;

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        InventoryType type = !useProofInv ? ItemConstants.getInventoryType(itemid) : InventoryType.CANHOLD;
        Character chr = c.getPlayer();
        Inventory inv = chr.getInventory(type);

        if (ii.isPickupRestricted(itemid)) {
            if (haveItemWithId(inv, itemid)) {
                return 0;
            } else if (ItemConstants.isEquipment(itemid) && haveItemWithId(chr.getInventory(InventoryType.EQUIPPED), itemid)) {
                return 0;   // thanks Captain & Aika & Vcoc for pointing out inventory checkup on player trades missing out one-of-a-kind items.
            }
        }

        if (!type.equals(InventoryType.EQUIP)) {
            short slotMax = ii.getSlotMax(c, itemid);
            final int numSlotsNeeded;

            if (ItemConstants.isRechargeable(itemid)) {
                numSlotsNeeded = 1;
            } else {
                List<Item> existing = inv.listById(itemid);

                if (existing.size() > 0) // first update all existing slots to slotMax
                {
                    for (Item eItem : existing) {
                        short oldQ = eItem.getQuantity();
                        if (oldQ < slotMax && owner.equals(eItem.getOwner())) {
                            short newQ = (short) Math.min(oldQ + quantity, slotMax);
                            quantity -= (newQ - oldQ);
                        }
                        if (quantity <= 0) {
                            break;
                        }
                    }
                }

                if (slotMax > 0) {
                    numSlotsNeeded = (int) (Math.ceil(((double) quantity) / slotMax));
                } else {
                    numSlotsNeeded = 1;
                }
            }

            returnValue = ((numSlotsNeeded + usedSlots) << 1);
            returnValue += (numSlotsNeeded == 0 || !inv.isFullAfterSomeItems(numSlotsNeeded - 1, usedSlots)) ? 1 : 0;
            //System.out.print(" needed " + numSlotsNeeded + " used " + usedSlots + " rval " + returnValue);
        } else {
            returnValue = ((quantity + usedSlots) << 1);
            returnValue += (!inv.isFullAfterSomeItems(0, usedSlots)) ? 1 : 0;
            //System.out.print(" eqpneeded " + 1 + " used " + usedSlots + " rval " + returnValue);
        }

        return returnValue;
    }

    public static void removeFromSlot(Client c, InventoryType type, short slot, short quantity, boolean fromDrop) {
        removeFromSlot(c, type, slot, quantity, fromDrop, false);
    }

    public static void removeFromSlot(Client c, InventoryType type, short slot, short quantity, boolean fromDrop, boolean consume) {
        Character chr = c.getPlayer();
        Inventory inv = chr.getInventory(type);
        if (!type.isStandardInventory()) {
            removeFromSlot(c, chr, inv, slot, quantity, fromDrop, consume, null);
            return;
        }
        inv.lockInventory();
        try {
            removeFromSlot(c, chr, inv, slot, quantity, fromDrop, consume, null);
        } finally {
            inv.unlockInventory();
        }
    }

    private static void removeFromSlot(Client c, Character chr, Inventory inv, short slot, short quantity,
                                       boolean fromDrop, boolean consume, InventoryOperationBatch changes) {
        InventoryType type = inv.getType();
        Item item = inv.getItem(slot);
        boolean allowZero = consume && ItemConstants.isRechargeable(item.getItemId());

        if (type == InventoryType.EQUIPPED) {
            inv.lockInventory();
            try {
                chr.unequippedItem((Equip) item);
                inv.removeItem(slot, quantity, allowZero);
            } finally {
                inv.unlockInventory();
            }
        } else {
            int petid = item.getPetId();
            if (petid > -1) {
                int petIdx = chr.getPetIndex(petid);
                if (petIdx > -1) {
                    Pet pet = chr.getPet(petIdx);
                    chr.unEquipPet(pet, true);
                }
            }
            inv.removeItem(slot, quantity, allowZero);
        }

        if (type != InventoryType.CANHOLD) {
            int mode = item.getQuantity() == 0 && !allowZero ? 3 : 1;
            if (changes != null) {
                changes.add(mode, item);
            } else {
                c.sendPacket(PacketCreator.modifyInventory(fromDrop, Collections.singletonList(new ModifyInventory(mode, item))));
            }
        }
    }

    public static void removeById(Client c, InventoryType type, int itemId, int quantity, boolean fromDrop, boolean consume) {
        Character chr = c.getPlayer();
        Inventory inv = chr.getInventory(type);
        if (!type.isStandardInventory()) {
            removeById(c, chr, inv, itemId, quantity, fromDrop, consume, null);
            return;
        }
        inv.lockInventory();
        try (var changes = new InventoryOperationBatch(c, fromDrop)) {
            removeById(c, chr, inv, itemId, quantity, fromDrop, consume, changes);
        } finally {
            inv.unlockInventory();
        }
    }

    /** 调用方持有普通背包锁，直到批量变更入队。 */
    public static void removeById(Client c, Inventory inv, int itemId, int quantity, InventoryOperationBatch changes) {
        removeById(c, c.getPlayer(), inv, itemId, quantity, true, false, changes);
    }

    private static void removeById(Client c, Character chr, Inventory inv, int itemId, int quantity,
                                   boolean fromDrop, boolean consume, InventoryOperationBatch changes) {
        int removeQuantity = quantity;
        InventoryType type = inv.getType();
        int slotLimit = type == InventoryType.EQUIPPED ? 128 : inv.getSlotLimit();

        for (short i = 0; i <= slotLimit; i++) {
            Item item = inv.getItem((short) (type == InventoryType.EQUIPPED ? -i : i));
            if (item != null && (item.getItemId() == itemId || item.getCashId() == itemId)) {
                short available = item.getQuantity();
                if (removeQuantity <= available) {
                    removeFromSlot(c, chr, inv, item.getPosition(), (short) removeQuantity, fromDrop, consume, changes);
                    return;
                }
                removeQuantity -= available;
                removeFromSlot(c, chr, inv, item.getPosition(), available, fromDrop, consume, changes);
            }
        }
        if (removeQuantity > 0 && type != InventoryType.CANHOLD) {
            throw new RuntimeException("[Hack] Not enough items available of Item:" + itemId + ", Quantity (After Quantity/Over Current Quantity): " + (quantity - removeQuantity) + "/" + quantity);
        }
    }

    private static boolean isSameOwner(Item source, Item target) {
        return source.getOwner().equals(target.getOwner());
    }

    public static void move(Client c, InventoryType type, short src, short dst) {
        Inventory inv = c.getPlayer().getInventory(type);
        inv.lockInventory();
        try (var changes = new InventoryOperationBatch(c, true)) {
            move(c, inv, src, dst, changes);
        } finally {
            inv.unlockInventory();
        }
    }

    /** 调用方持有背包锁，直到变更按顺序入发送队列。 */
    public static void move(Client c, Inventory inv, short src, short dst, InventoryOperationBatch changes) {
        if (src < 0 || dst < 0 || dst > inv.getSlotLimit()) {
            return;
        }
        if (src == dst) {
            c.sendPacket(PacketCreator.enableActions());
            return;
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Item source = inv.getItem(src);
        Item initialTarget = inv.getItem(dst);
        if (source == null) {
            return;
        }
        short olddstQ = initialTarget == null ? -1 : initialTarget.getQuantity();
        short oldsrcQ = source.getQuantity();
        short slotMax = ii.getSlotMax(c, source.getItemId());
        inv.move(src, dst, slotMax);
        InventoryType type = inv.getType();
        if (type != InventoryType.EQUIP && type != InventoryType.CASH && initialTarget != null && initialTarget.getItemId() == source.getItemId() && !ItemConstants.isRechargeable(source.getItemId()) && isSameOwner(source, initialTarget)) {
            int sourceMode = olddstQ + oldsrcQ > slotMax ? 1 : 3;
            changes.addPair(sourceMode, source, 1, initialTarget);
        } else if (initialTarget == null) {
            changes.move(source, src);
        } else {
            // 083 在包尾才更新最近获得槽位；互换完成后才能继续收拢。
            changes.flush();
            changes.move(source, src);
            changes.flush();
        }
        // 添加物品代码提示
        if (GameConfig.getServerBoolean("use_debug") && c.getPlayer().isGM()) { // 假设isGM()是检查玩家是否是管理员的方法
            changes.flush();
            int itemID = source.getItemId();
            c.getPlayer().dropMessage(5, I18nUtil.getMessage("InventoryManipulator.handlePacket.message1")  + itemID);
        }
    }

    /*
    穿上装备判断
     */
    public static void equip(Client c, short src, short dst) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        Character chr = c.getPlayer();
        Inventory eqpInv = chr.getInventory(InventoryType.EQUIP);
        Inventory eqpdInv = chr.getInventory(InventoryType.EQUIPPED);

        Equip source = (Equip) eqpInv.getItem(src);
        if (source == null || !ii.canWearEquipment(chr, source, dst)) {
            c.sendPacket(PacketCreator.enableActions());
            return;
        }
        int itemGender = ItemId.getGender(source.getItemId());
        //控制台参数为true时进行校验判断
        if(GameConfig.getServerBoolean("use_equipment_gender_limit") && itemGender != 2 && itemGender != chr.getGender()) {  //判断装备是否要求角色性别
            c.sendPacket(PacketCreator.enableActions());
            chr.dropMessage(1,I18nUtil.getMessage("InventoryManipulator.equip.message1"));    //发送弹窗提示性别不符
            log.warn(I18nUtil.getLogMessage("InventoryManipulator.warn.equip.message1"),      //后台记录信息
                    chr.getName(),
                    chr.getGender() <= 0 ? I18nUtil.getMessage("Character.Gender0") : I18nUtil.getMessage("Character.Gender1"),
                    ii.getName(source.getItemId()),
                    itemGender <= 0 ? I18nUtil.getMessage("Character.Gender0") : I18nUtil.getMessage("Character.Gender1"),
                    source.getItemId()
            );
            return;
        } else if ((ItemId.isExplorerMount(source.getItemId()) && chr.isCygnus()) ||
                ((ItemId.isCygnusMount(source.getItemId())) && !chr.isCygnus())) {// Adventurer taming equipment    //冒险家驯服设备
            return;
        }
        boolean itemChanged = false;

        if (ii.isUntradeableOnEquip(source.getItemId())) {
            short flag = source.getFlag();      // thanks BHB for noticing flags missing after equipping these      //感谢BHB在安装这些设备后发现旗帜丢失
            flag |= ItemConstants.UNTRADEABLE;
            source.setFlag(flag);

            itemChanged = true;
        }
        switch (dst) {
        case -6: // unequip the overall
            Item top = eqpdInv.getItem((short) -5);
            if (top != null && ItemConstants.isOverall(top.getItemId())) {
                if (eqpInv.isFull()) {
                    c.sendPacket(PacketCreator.getInventoryFull());
                    c.sendPacket(PacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -5, eqpInv.getNextFreeSlot());
            }
            break;
        case -5:
            final Item bottom = eqpdInv.getItem((short) -6);
            if (bottom != null && ItemConstants.isOverall(source.getItemId())) {
                if (eqpInv.isFull()) {
                    c.sendPacket(PacketCreator.getInventoryFull());
                    c.sendPacket(PacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -6, eqpInv.getNextFreeSlot());
            }
            break;
        case -10: // check if weapon is two-handed
            Item weapon = eqpdInv.getItem((short) -11);
            if (weapon != null && ii.isTwoHanded(weapon.getItemId())) {
                if (eqpInv.isFull()) {
                    c.sendPacket(PacketCreator.getInventoryFull());
                    c.sendPacket(PacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -11, eqpInv.getNextFreeSlot());
            }
            break;
        case -11:
            Item shield = eqpdInv.getItem((short) -10);
            if (shield != null && ii.isTwoHanded(source.getItemId())) {
                if (eqpInv.isFull()) {
                    c.sendPacket(PacketCreator.getInventoryFull());
                    c.sendPacket(PacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -10, eqpInv.getNextFreeSlot());
            }
            break;
        case -18:
            if (chr.getMapleMount() != null) {
                chr.getMapleMount().setItemId(source.getItemId());
            }
            break;
        }

        //1112413, 1112414, 1112405 (Lilin's Ring)
        source = (Equip) eqpInv.getItem(src);
        eqpInv.removeSlot(src);

        Equip target;
        eqpdInv.lockInventory();
        try {
            target = (Equip) eqpdInv.getItem(dst);
            if (target != null) {
                chr.unequippedItem(target);
                eqpdInv.removeSlot(dst);
            }
        } finally {
            eqpdInv.unlockInventory();
        }

        final List<ModifyInventory> mods = new ArrayList<>();
        if (itemChanged) {
            mods.add(new ModifyInventory(3, source));
            mods.add(new ModifyInventory(0, source.copy()));//to prevent crashes
        }

        source.setPosition(dst);

        eqpdInv.lockInventory();
        try {
            if (source.getRingId() > -1) {
                Ring ring = chr.getRingById(source.getRingId());
                if (ring != null) {
                    ring.equip();
                }
            }
            chr.equippedItem(source);
            eqpdInv.addItemFromDB(source);
        } finally {
            eqpdInv.unlockInventory();
        }

        if (target != null) {
            target.setPosition(src);
            eqpInv.addItemFromDB(target);
        }
        if (chr.getBuffedValue(BuffStat.BOOSTER) != null && ItemConstants.isWeapon(source.getItemId())) {
            chr.cancelBuffStats(BuffStat.BOOSTER);
        }

        int petIndex = ItemConstants.PETS_NAME_TAG.indexOf(dst);
        if (petIndex != -1) {
            Pet pet = chr.getPet(petIndex);
            if (pet != null) {
                chr.getMap().broadcastMessage(chr, PacketCreator.changePetName(chr, pet.getName(), (byte)petIndex), false);
            }
        }

        mods.add(new ModifyInventory(2, source, src));
        c.sendPacket(PacketCreator.modifyInventory(true, mods));
        chr.equipChanged();
    }

    public static void unequip(Client c, short src, short dst) {
        Character chr = c.getPlayer();
        Inventory eqpInv = chr.getInventory(InventoryType.EQUIP);
        Inventory eqpdInv = chr.getInventory(InventoryType.EQUIPPED);

        Equip source = (Equip) eqpdInv.getItem(src);
        Equip target = (Equip) eqpInv.getItem(dst);
        if (dst < 0) {
            return;
        }
        if (source == null) {
            return;
        }
        if (target != null && src <= 0) {
            c.sendPacket(PacketCreator.getInventoryFull());
            return;
        }

        eqpdInv.lockInventory();
        try {
            if (source.getRingId() > -1) {
                Ring ring = chr.getRingById(source.getRingId());
                if (ring != null) {
                    ring.unequip();
                }
            }
            chr.unequippedItem(source);
            eqpdInv.removeSlot(src);
        } finally {
            eqpdInv.unlockInventory();
        }

        if (target != null) {
            eqpInv.removeSlot(dst);
        }
        source.setPosition(dst);
        eqpInv.addItemFromDB(source);
        if (target != null) {
            target.setPosition(src);
            eqpdInv.addItemFromDB(target);
        }

        int petIndex = ItemConstants.PETS_NAME_TAG.indexOf(src);
        if (petIndex != -1) {
            Pet pet = chr.getPet(petIndex);
            if (pet != null) {
                chr.getMap().broadcastMessage(chr, PacketCreator.changePetName(chr, pet.getName(), (byte)petIndex), false);
            }
        }
        
        c.sendPacket(PacketCreator.modifyInventory(true, Collections.singletonList(new ModifyInventory(2, source, src))));
        chr.equipChanged();
    }

    private static boolean isDisappearingItemDrop(Item it) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (ii.isDropRestricted(it.getItemId())) {
            return true;
        } else if (ii.isCash(it.getItemId())) {
            if (GameConfig.getServerBoolean("use_enforce_unmerchable_cash")) {     // thanks Ari for noticing cash drops not available server-side
                return true;
            } else {
                return ItemConstants.isPet(it.getItemId()) && GameConfig.getServerBoolean("use_enforce_unmerchable_pet");
            }
        } else if (isDroppedItemRestricted(it)) {
            return true;
        } else {
            return ItemId.isWeddingRing(it.getItemId());
        }
    }

    public static void drop(Client c, InventoryType type, short src, short quantity) {
        if (src < 0) {
            type = InventoryType.EQUIPPED;
        }

        Character chr = c.getPlayer();
        Inventory inv = chr.getInventory(type);
        Item source = inv.getItem(src);

        if (source == null || chr.getTrade() != null || chr.getMiniGame() != null) { //Only check needed would prob be merchants (to see if the player is in one)
            return;
        }

        if (chr.isGM() && chr.gmLevel() < GameConfig.getServerInt("minimum_gm_level_to_drop")) {
            chr.message("你的GM等级不允许丢弃物品。");
            log.info("GM {} tried to drop item id {}", chr.getName(), source.getItemId());
            return;
        }
        int itemId = source.getItemId();
        boolean rechargeable = ItemConstants.isRechargeable(itemId);
        MapleMap map = chr.getMap();
        if ((!rechargeable && source.getQuantity() < quantity) || quantity < 0) {
            return;
        }

        int petid = source.getPetId();
        if (petid > -1) {
            int petIdx = chr.getPetIndex(petid);
            if (petIdx > -1) {
                Pet pet = chr.getPet(petIdx);
                chr.unEquipPet(pet, true);
            }
        }

        Point dropPos = new Point(chr.getPosition());
        Item dropped;
        boolean partial;
        inv.lockInventory();
        try {
            if (inv.getItem(src) != source || (!rechargeable && source.getQuantity() < quantity)) {
                c.sendPacket(PacketCreator.enableActions());
                return;
            }
            partial = !rechargeable && quantity < source.getQuantity();
            if (partial) {
                dropped = source.copy();
                dropped.setQuantity(quantity);
                source.setQuantity((short) (source.getQuantity() - quantity));
                c.sendPacket(PacketCreator.modifyInventory(true, Collections.singletonList(new ModifyInventory(1, source))));
            } else {
                if (type == InventoryType.EQUIPPED) {
                    chr.unequippedItem((Equip) source);
                }
                inv.removeSlot(src);
                dropped = source;
                c.sendPacket(PacketCreator.modifyInventory(true, Collections.singletonList(new ModifyInventory(3, source))));
            }
        } finally {
            inv.unlockInventory();
        }

        if (!partial && src < 0) {
            chr.equipChanged();
        } else if (ItemConstants.isNewYearCardEtc(itemId)) {
            boolean sentCard = itemId == ItemId.NEW_YEARS_CARD_SEND;
            NewYearCardRecord.removeAllNewYearCard(sentCard, chr);
            c.getAbstractPlayerInteraction().removeAll(sentCard ? ItemId.NEW_YEARS_CARD_SEND : ItemId.NEW_YEARS_CARD_RECEIVED);
        }
        if (isDisappearingItemDrop(dropped)) {
            map.disappearingItemDrop(chr, chr, dropped, dropPos);
        } else {
            map.spawnItemDrop(chr, chr, dropped, dropPos, true, true);
        }

        int quantityNow = chr.getItemQuantity(itemId, false);
        if (itemId == chr.getItemEffect()) {
            if (quantityNow <= 0) {
                chr.setItemEffect(0);
                map.broadcastMessage(PacketCreator.itemEffect(chr.getId(), 0));
            }
        } else if (itemId == ItemId.CHALKBOARD_1 || itemId == ItemId.CHALKBOARD_2) {
            if (source.getQuantity() <= 0) {
                chr.setChalkboard(null);
            }
        } else if (itemId == ItemId.ARPQ_SPIRIT_JEWEL) {
            chr.updateAriantScore(quantityNow);
        }
    }

    private static boolean isDroppedItemRestricted(Item it) {
        return GameConfig.getServerBoolean("use_erase_untradeable_drop") && it.isUntradeable();
    }

    public static boolean isSandboxItem(Item it) {
        return (it.getFlag() & ItemConstants.SANDBOX) == ItemConstants.SANDBOX;
    }
}
