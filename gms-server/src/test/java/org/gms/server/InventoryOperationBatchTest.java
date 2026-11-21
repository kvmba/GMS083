package org.gms.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.local.LocalChannel;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.Skill;
import org.gms.client.autoban.AutobanManager;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.ModifyInventory;
import org.gms.client.inventory.manipulator.InventoryManipulator;
import org.gms.config.GameConfig;
import org.gms.manager.ServerManager;
import org.gms.model.dto.InventorySearchRtnDTO;
import org.gms.model.pojo.SkillEntry;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.InPacket;
import org.gms.net.packet.Packet;
import org.gms.net.server.PlayerStorage;
import org.gms.net.server.Server;
import org.gms.net.server.channel.handlers.InventoryMergeHandler;
import org.gms.net.server.world.World;
import org.gms.property.ServiceProperty;
import org.gms.provider.Data;
import org.gms.server.maps.MapleMap;
import org.gms.server.quest.Quest;
import org.gms.server.quest.actions.ItemAction;
import org.gms.service.ConfigService;
import org.gms.service.InventoryService;
import org.gms.util.InventoryOperationBatch;
import org.gms.util.PacketCreator;
import org.gms.util.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import javax.sql.DataSource;
import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryOperationBatchTest {
    private static final int POTION = 2_000_000;
    private static final int OTHER_POTION = 2_000_001;
    private static final int ETC_ITEM = 4_000_000;
    private static final int THROWING_STAR = 2_070_000;
    private static final byte USE = 2;
    private final List<Packet> sent = new ArrayList<>();
    private Client client;
    private Character character;
    private Inventory inventory;
    private ItemInformationProvider items;

    @BeforeAll
    static void bootHostContext() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class, RETURNS_DEEP_STUBS);
        ConfigService config = mock(ConfigService.class);
        when(config.loadGameConfigs()).thenReturn(List.of());
        when(context.getBean(ConfigService.class)).thenReturn(config);
        ServiceProperty properties = mock(ServiceProperty.class);
        when(properties.getLanguage()).thenReturn("zh-CN");
        when(context.getBean(ServiceProperty.class)).thenReturn(properties);
        MessageSource messages = mock(MessageSource.class);
        when(messages.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        for (String name : List.of("messageSource", "logSource", "exceptionSource")) {
            when(context.getBean(name, MessageSource.class)).thenReturn(messages);
        }
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class, RETURNS_DEEP_STUBS));
        when(context.getBean(DataSource.class)).thenReturn(dataSource);
        new ServerManager().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        character = mock(Character.class);
        client = mock(Client.class);
        when(client.getPlayer()).thenReturn(character);
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(client).sendPacket(any(Packet.class));
        inventory = new Inventory(character, InventoryType.USE, (byte) 8);
        when(character.getInventory(InventoryType.USE)).thenReturn(inventory);
        items = mock(ItemInformationProvider.class);
        when(items.getSlotMax(client, POTION)).thenReturn((short) 10);
    }

    @Test
    void grantFillsExistingStacksAndAddsNewSlotInOnePacket() {
        put(POTION, 1, 6);
        put(POTION, 2, 8);
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            assertTrue(InventoryManipulator.addById(client, POTION, (short) 13));
        }

        assertEquals(1, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 3);
        assertOperation(data, 1, USE, 1);
        assertEquals(10, data.getShort());
        assertOperation(data, 1, USE, 2);
        assertEquals(10, data.getShort());
        assertAddition(data, USE, 3, POTION, 7, 0);
        assertFalse(data.hasRemaining());
        assertEquals(27, inventory.countById(POTION));
    }

    @Test
    void mergingAGrantWithANewExpirationRefreshesTheOriginalSlot() {
        Item stack = item(POTION, 1, 6);
        long expiration = 1_900_000_000_000L;
        stack.setExpiration(expiration - 60_000);
        inventory.addItemFromDB(stack);
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            assertTrue(InventoryManipulator.addById(client, POTION, (short) 2, expiration));
        }

        assertSame(stack, inventory.getItem((short) 1));
        assertEquals(expiration, stack.getExpiration());
        assertEquals(8, stack.getQuantity());
        assertEquals(1, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 1);
        assertOperation(data, 0, USE, 1);
        assertEquals(2, data.get());
        assertEquals(POTION, data.getInt());
        assertEquals(0, data.get());
        assertEquals(PacketCreator.getTime(expiration), data.getLong());
        assertEquals(8, data.getShort());
        assertEquals(0, data.getShort()); // owner length
        assertEquals(0, data.getShort()); // flags
        assertFalse(data.hasRemaining());
    }

    @Test
    void refreshingACashStackKeepsItsSerialNumber() {
        int cashItemId = 5_070_000;
        Item stack = item(cashItemId, 1, 2);
        long serialNumber = stack.getCashId();
        Inventory cash = new Inventory(character, InventoryType.CASH, (byte) 2);
        cash.addItemFromDB(stack);
        when(character.getInventory(InventoryType.CASH)).thenReturn(cash);
        when(items.getSlotMax(client, cashItemId)).thenReturn((short) 10);
        when(items.isCash(cashItemId)).thenReturn(true);
        long expiration = 1_900_000_000_000L;
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            assertTrue(InventoryManipulator.addById(client, cashItemId, (short) 3, expiration));
        }

        assertEquals(1, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 1);
        assertOperation(data, 0, (byte) 5, 1);
        assertEquals(2, data.get());
        assertEquals(cashItemId, data.getInt());
        assertEquals(1, data.get());
        assertEquals(serialNumber, data.getLong());
        assertEquals(PacketCreator.getTime(expiration), data.getLong());
        assertEquals(5, data.getShort());
    }

    @Test
    void pickupWithoutAValidStackLimitDoesNotCreateZeroQuantitySlots() {
        Item drop = item(POTION, 0, 5);
        when(items.getSlotMax(client, POTION)).thenReturn((short) 0);
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            assertFalse(InventoryManipulator.addFromDrop(client, drop));
        }
        assertEquals(5, drop.getQuantity());
        assertTrue(inventory.list().isEmpty());
        assertEquals(1, sent.size());
        packet(0, 0x1f);
    }

    @Test
    void partialPickupSendsAcceptedQuantityBeforeTheFullResponse() {
        inventory.setSlotLimit(2);
        put(POTION, 1, 7);
        put(OTHER_POTION, 2, 10);
        Item drop = item(POTION, 0, 5);
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            assertFalse(InventoryManipulator.addFromDrop(client, drop));
        }

        assertEquals(2, drop.getQuantity());
        assertEquals(10, inventory.getItem((short) 1).getQuantity());
        assertEquals(3, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 1);
        assertOperation(data, 1, USE, 1);
        assertEquals(10, data.getShort());
        assertFalse(data.hasRemaining());
        assertFalse(inventoryPacket(1, true, 0).hasRemaining());
        ByteBuffer full = packet(2, 0x27);
        assertEquals(0, full.get());
        assertEquals(0xff, Byte.toUnsignedInt(full.get()));
    }

    @Test
    void partialRemovalIsSyncedBeforeInsufficientQuantityIsReported() {
        put(POTION, 1, 4);
        put(POTION, 3, 6);
        assertThrows(RuntimeException.class, () ->
                InventoryManipulator.removeById(client, InventoryType.USE, POTION, 11, false, false));

        assertEquals(0, inventory.countById(POTION));
        assertEquals(1, sent.size());
        ByteBuffer data = inventoryPacket(0, false, 2);
        assertOperation(data, 3, USE, 1);
        assertOperation(data, 3, USE, 3);
        assertFalse(data.hasRemaining());
    }

    @Test
    void consumingRechargeableToZeroKeepsTheSlot() {
        put(THROWING_STAR, 1, 4);
        InventoryManipulator.removeById(client, InventoryType.USE, THROWING_STAR, 4, true, true);

        assertEquals(0, inventory.getItem((short) 1).getQuantity());
        assertEquals(1, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 1);
        assertOperation(data, 1, USE, 1);
        assertEquals(0, data.getShort());
        assertFalse(data.hasRemaining());
    }

    @Test
    void questUsesASingleBatchPerPhaseAndFreesSlotsBeforeGranting() {
        int firstReward = 2_000_002, secondReward = 2_000_003;
        inventory.setSlotLimit(2);
        put(POTION, 1, 1);
        put(OTHER_POTION, 2, 2);
        when(character.getClient()).thenReturn(client);
        doAnswer(invocation -> {
            client.sendPacket(invocation.getArgument(0));
            return null;
        }).when(character).sendPacket(any(Packet.class));
        when(items.getSlotMax(client, firstReward)).thenReturn((short) 10);
        when(items.getSlotMax(client, secondReward)).thenReturn((short) 10);
        Data data = questItems(questItem(0, firstReward, 1), questItem(1, POTION, -1),
                questItem(2, OTHER_POTION, -2), questItem(3, secondReward, 2));
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            new ItemAction(mock(Quest.class), data).run(character, null);
        }

        assertEquals(6, sent.size());
        ByteBuffer debit = inventoryPacket(0, true, 2);
        assertOperation(debit, 3, USE, 1);
        assertOperation(debit, 3, USE, 2);
        assertFalse(debit.hasRemaining());
        assertQuestItemNotice(1, POTION, -1);
        assertQuestItemNotice(2, OTHER_POTION, -2);
        ByteBuffer credit = inventoryPacket(3, true, 2);
        assertAddition(credit, USE, 1, firstReward, 1, 0);
        assertAddition(credit, USE, 2, secondReward, 2, 0);
        assertFalse(credit.hasRemaining());
        assertQuestItemNotice(4, firstReward, 1);
        assertQuestItemNotice(5, secondReward, 2);
        assertEquals(1, inventory.countById(firstReward));
        assertEquals(2, inventory.countById(secondReward));
    }

    @Test
    void questDoesNotAnnounceAnItemThatCouldNotBeGranted() {
        inventory.setSlotLimit(1);
        when(character.getClient()).thenReturn(client);
        doAnswer(invocation -> {
            client.sendPacket(invocation.getArgument(0));
            return null;
        }).when(character).sendPacket(any(Packet.class));
        when(items.getSlotMax(client, OTHER_POTION)).thenReturn((short) 10);
        Data data = questItems(questItem(0, POTION, 1), questItem(1, OTHER_POTION, 1));
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            new ItemAction(mock(Quest.class), data).run(character, null);
        }

        assertEquals(4, sent.size());
        ByteBuffer applied = inventoryPacket(0, true, 1);
        assertAddition(applied, USE, 1, POTION, 1, 0);
        assertFalse(applied.hasRemaining());
        assertFalse(inventoryPacket(1, true, 0).hasRemaining());
        packet(2, 0x27);
        assertQuestItemNotice(3, POTION, 1);
        assertEquals(0, inventory.countById(OTHER_POTION));
    }

    @Test
    void expiredSlotsAreRemovedTogetherEvenWhenSkillsExpireInTheSameTick() throws Exception {
        Character player = spy(new Character());
        player.setClient(client);
        when(client.getPlayer()).thenReturn(player);
        Inventory use = player.getInventory(InventoryType.USE);
        long now = 1_800_000_000_000L;
        Skill expiredSkill = new Skill(1);
        Skill currentSkill = new Skill(2);
        var skills = new LinkedHashMap<Skill, SkillEntry>();
        skills.put(expiredSkill, new SkillEntry((byte) 1, 0, now - 1));
        skills.put(currentSkill, new SkillEntry((byte) 1, 0, -1));
        when(player.getSkills()).thenReturn(Collections.unmodifiableMap(skills));
        doAnswer(invocation -> {
            skills.remove(expiredSkill);
            return null;
        }).when(player).changeSkillLevel(expiredSkill, (byte) -1, 0, -1);
        Item first = item(POTION, 1, 2);
        Item second = item(OTHER_POTION, 2, 3);
        Item current = item(POTION, 3, 4);
        first.setExpiration(now - 1);
        second.setExpiration(now - 2);
        current.setExpiration(now);
        use.addItemFromDB(first);
        use.addItemFromDB(second);
        use.addItemFromDB(current);
        int replacement = 2_000_002;
        when(items.getReplaceOnExpire(POTION)).thenReturn(new Pair<>(replacement, ""));
        when(items.getReplaceOnExpire(OTHER_POTION)).thenReturn(new Pair<>(0, ""));
        when(items.getSlotMax(client, replacement)).thenReturn((short) 10);
        var expire = Character.class.getDeclaredMethod("expireTimedState", long.class);
        expire.setAccessible(true);
        try (var catalog = mockStatic(ItemInformationProvider.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            expire.invoke(player, now);
        }

        assertEquals(List.of(currentSkill), new ArrayList<>(skills.keySet()));
        assertEquals(4, sent.size());
        assertExpirationNotice(0, POTION);
        assertExpirationNotice(1, OTHER_POTION);
        ByteBuffer removal = inventoryPacket(2, false, 2);
        assertOperation(removal, 3, USE, 1);
        assertOperation(removal, 3, USE, 2);
        assertFalse(removal.hasRemaining());
        ByteBuffer add = inventoryPacket(3, true, 1);
        assertAddition(add, USE, 1, replacement, 1, 0);
        assertFalse(add.hasRemaining());
        assertNull(use.getItem((short) 2));
        assertSame(current, use.getItem((short) 3));
    }

    @Test
    void dropDoesNotModifyASlotReplacedBeforeItAcquiresTheLock() {
        put(POTION, 1, 5);
        var map = mock(MapleMap.class);
        var replacement = item(OTHER_POTION, 1, 4);
        when(character.getMap()).thenAnswer(invocation -> {
            inventory.removeSlot((short) 1);
            inventory.addItemFromDB(replacement);
            return map;
        });
        when(character.getPosition()).thenReturn(new Point(0, 0));
        InventoryManipulator.drop(client, InventoryType.USE, (short) 1, (short) 2);

        assertSame(replacement, inventory.getItem((short) 1));
        assertEquals(4, replacement.getQuantity());
        assertEquals(1, sent.size());
        packet(0, 0x1f);
        verifyNoInteractions(map);
    }

    @Test
    void movingToTheSameSlotDoesNotModifyTheInventory() {
        put(POTION, 1, 5);
        InventoryManipulator.move(client, InventoryType.USE, (short) 1, (short) 1);

        assertEquals(5, inventory.getItem((short) 1).getQuantity());
        assertEquals(1, sent.size());
        packet(0, 0x1f);
    }

    @Test
    void mergeAndCompactAppliesOrderedChangesBeforeTheCompletionPackets() {
        put(POTION, 2, 4);
        put(POTION, 4, 3);
        put(OTHER_POTION, 6, 5);
        gather();

        assertEquals(7, inventory.getItem((short) 1).getQuantity());
        assertEquals(OTHER_POTION, inventory.getItem((short) 2).getItemId());
        assertNull(inventory.getItem((short) 4));
        assertNull(inventory.getItem((short) 6));
        assertEquals(3, sent.size());
        ByteBuffer data = inventoryPacket(0, true, 4);
        assertOperation(data, 3, USE, 4);
        assertOperation(data, 1, USE, 2);
        assertEquals(7, data.getShort());
        assertOperation(data, 2, USE, 2);
        assertEquals(1, data.getShort());
        assertOperation(data, 2, USE, 6);
        assertEquals(2, data.getShort());
        assertFalse(data.hasRemaining());
        ByteBuffer finished = packet(1, 0x34);
        assertEquals(0, finished.get());
        assertEquals(USE, finished.get());
        assertFalse(finished.hasRemaining());
        packet(2, 0x1f);
    }

    @Test
    void gatherSendsAnOccupiedSlotSwapBeforeCompactingThoseSlots() {
        Item first = item(THROWING_STAR, 2, 4);
        Item latest = item(THROWING_STAR, 4, 3);
        inventory.addItemFromDB(first);
        inventory.addItemFromDB(latest);
        when(items.getSlotMax(client, THROWING_STAR)).thenReturn((short) 10);
        gather();

        assertSame(latest, inventory.getItem((short) 1));
        assertSame(first, inventory.getItem((short) 2));
        assertEquals(4, sent.size());
        ByteBuffer swap = inventoryPacket(0, true, 1);
        assertOperation(swap, 2, USE, 4);
        assertEquals(2, swap.getShort());
        assertFalse(swap.hasRemaining());
        ByteBuffer compact = inventoryPacket(1, true, 2);
        assertOperation(compact, 2, USE, 2);
        assertEquals(1, compact.getShort());
        assertOperation(compact, 2, USE, 4);
        assertEquals(2, compact.getShort());
        assertFalse(compact.hasRemaining());
        packet(2, 0x34);
        packet(3, 0x1f);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlineAdminWriteKeepsTheInventoryLockedThroughPacketEnqueue(boolean delete) {
        int characterId = 42;
        short slot = 1;
        short quantity = 7;
        Item stack = spy(item(POTION, slot, 6));
        Inventory tracked = spy(inventory);
        tracked.addItemFromDB(stack);
        when(character.getInventory(InventoryType.USE)).thenReturn(tracked);

        Server server = mock(Server.class);
        World world = mock(World.class);
        PlayerStorage players = mock(PlayerStorage.class);
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getPlayerStorage()).thenReturn(players);
        when(players.getCharacterById(characterId)).thenReturn(character);
        InventorySearchRtnDTO edit = InventorySearchRtnDTO.builder().characterId(characterId)
                .itemId(POTION).position(slot).inventoryType(USE).online(true).quantity(quantity).build();
        InventoryService service = new InventoryService(null, null, null, null, null);
        try (var servers = mockStatic(Server.class);
             var catalog = mockStatic(ItemInformationProvider.class)) {
            servers.when(Server::getInstance).thenReturn(server);
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            if (delete) {
                service.deleteInventory(edit);
            } else {
                service.updateInventory(edit);
            }
        }

        var order = inOrder(tracked, stack, character);
        order.verify(tracked).lockInventory();
        order.verify(tracked).getItem(slot);
        if (delete) {
            order.verify(tracked).removeSlot(slot);
        } else {
            order.verify(stack).setQuantity(quantity);
        }
        var packet = ArgumentCaptor.forClass(Packet.class);
        order.verify(character).sendPacket(packet.capture());
        order.verify(tracked).unlockInventory();
        sent.add(packet.getValue());
        inventoryPacket(0, false, delete ? 1 : 2);
    }

    @Test
    void gameplayWritesDoNotOvertakeAnAlreadyEnqueuedAdminWrite() throws Exception {
        Client connection = Client.createMock();
        Packet admin = PacketCreator.modifyInventory(false, List.of(new ModifyInventory(1, item(POTION, 1, 7))));
        Packet gameplay = PacketCreator.modifyInventory(true, List.of(new ModifyInventory(1, item(POTION, 1, 10))));
        Packet finished = PacketCreator.finishedSort(USE);
        List<Packet> written = new ArrayList<>();
        var channel = new LocalChannel();
        var eventLoop = new DefaultEventLoop();
        try {
            channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                    written.add((Packet) message);
                    promise.setSuccess();
                }
            });
            eventLoop.register(channel).sync();
            var ioChannel = Client.class.getDeclaredField("ioChannel");
            ioChannel.setAccessible(true);
            ioChannel.set(connection, channel);

            var gameplayRunning = new CountDownLatch(1);
            var adminEnqueued = new CountDownLatch(1);
            var gameplayTask = eventLoop.submit(() -> {
                gameplayRunning.countDown();
                adminEnqueued.await();
                connection.sendPacket(gameplay);
                connection.sendPacket(finished);
                assertEquals(List.of(admin, gameplay, finished), written);
                return null;
            });
            try {
                gameplayRunning.await();
                connection.sendPacket(admin);
            } finally {
                adminEnqueued.countDown();
            }
            gameplayTask.sync();
            Packet later = PacketCreator.finishedSort2(USE);
            connection.sendPacket(later);
            eventLoop.submit(() -> {}).sync();
            assertEquals(List.of(admin, gameplay, finished, later), written);
        } finally {
            channel.close().syncUninterruptibly();
            eventLoop.shutdownGracefully(0, 0, TimeUnit.NANOSECONDS).syncUninterruptibly();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {254, 255})
    void countLimitDoesNotSeparateAnOperationPair(int firstCount) {
        Item source = item(POTION, 1, 0);
        Item target = item(POTION, 2, 10);
        try (var changes = new InventoryOperationBatch(client, true)) {
            for (int i = 0; i < firstCount; i++) {
                changes.add(1, target);
            }
            changes.addPair(3, source, 1, target);
        }
        assertEquals(2, sent.size());
        ByteBuffer data = inventoryPacket(0, false, firstCount);
        for (int i = 0; i < firstCount; i++) {
            assertOperation(data, 1, USE, 2);
            assertEquals(10, data.getShort());
        }
        assertFalse(data.hasRemaining());
        ByteBuffer pair = inventoryPacket(1, true, 2);
        assertOperation(pair, 3, USE, 1);
        assertOperation(pair, 1, USE, 2);
        assertEquals(10, pair.getShort());
        assertFalse(pair.hasRemaining());
    }

    @Test
    void byteLimitAlsoKeepsReplacementTogether() {
        String owner = "a".repeat(255);
        try (var catalog = mockStatic(ItemInformationProvider.class);
             var changes = new InventoryOperationBatch(client, true)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            for (int pos = 1; pos <= 117; pos++) {
                Item use = item(POTION, pos, 1);
                Item etc = item(ETC_ITEM, pos, 1);
                use.setOwner(owner);
                etc.setOwner(owner);
                changes.add(0, use);
                changes.add(0, etc);
            }
            Item replacement = item(POTION, 1, 2);
            replacement.setOwner(owner);
            changes.addPair(3, replacement, 0, replacement);
        }

        assertEquals(2, sent.size());
        assertTrue(sent.getFirst().getBytes().length + Integer.BYTES <= 0xffff);
        ByteBuffer data = inventoryPacket(0, false, 234);
        for (int pos = 1; pos <= 117; pos++) {
            assertAddition(data, USE, pos, POTION, 1, 255);
            assertAddition(data, (byte) 4, pos, ETC_ITEM, 1, 255);
        }
        assertFalse(data.hasRemaining());
        ByteBuffer replacement = inventoryPacket(1, true, 2);
        assertOperation(replacement, 3, USE, 1);
        assertAddition(replacement, USE, 1, POTION, 2, 255);
        assertFalse(replacement.hasRemaining());
    }

    @Test
    void singlePacketEncoderRejectsOverflowAndKeepsTheEquipmentTrailer() {
        ModifyInventory move = new ModifyInventory(2, item(1_000_000, -5, 1), (short) 1);
        assertThrows(IllegalArgumentException.class,
                () -> PacketCreator.modifyInventory(true, Collections.nCopies(256, move)));
        sent.add(PacketCreator.modifyInventory(true, List.of(move)));
        ByteBuffer data = inventoryPacket(0, true, 1);
        assertOperation(data, 2, (byte) 1, 1);
        assertEquals(-5, data.getShort());
        assertEquals(2, data.get());
        assertFalse(data.hasRemaining());
    }

    private static Data questItems(Data... items) {
        Data data = mock(Data.class);
        when(data.getChildren()).thenReturn(List.of(items));
        return data;
    }

    private static Data questItem(int index, int itemId, int count) {
        Data item = mock(Data.class);
        Data id = mock(Data.class);
        Data quantity = mock(Data.class);
        when(item.getName()).thenReturn(Integer.toString(index));
        when(item.getChildByPath("id")).thenReturn(id);
        when(item.getChildByPath("count")).thenReturn(quantity);
        when(id.getData()).thenReturn(itemId);
        when(quantity.getData()).thenReturn(count);
        return item;
    }

    private void assertQuestItemNotice(int index, int itemId, int quantity) {
        ByteBuffer data = packet(index, SendOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
        assertEquals(3, data.get());
        assertEquals(1, data.get());
        assertEquals(itemId, data.getInt());
        assertEquals(quantity, data.getInt());
        assertFalse(data.hasRemaining());
    }

    private void assertExpirationNotice(int index, int itemId) {
        ByteBuffer data = packet(index, 0x27);
        assertEquals(2, data.get());
        assertEquals(itemId, data.getInt());
        assertFalse(data.hasRemaining());
    }

    private void gather() {
        InPacket request = mock(InPacket.class);
        when(request.readByte()).thenReturn(USE);
        when(character.getAutoBanManager()).thenReturn(mock(AutobanManager.class));
        Server server = mock(Server.class);
        try (var catalog = mockStatic(ItemInformationProvider.class);
             var gameConfig = mockStatic(GameConfig.class);
             var servers = mockStatic(Server.class)) {
            catalog.when(ItemInformationProvider::getInstance).thenReturn(items);
            gameConfig.when(() -> GameConfig.getServerBoolean("use_item_sort")).thenReturn(true);
            servers.when(Server::getInstance).thenReturn(server);
            new InventoryMergeHandler().handlePacket(request, client);
        }
    }

    private void put(int itemId, int slot, int quantity) {
        inventory.addItemFromDB(item(itemId, slot, quantity));
    }

    private static Item item(int itemId, int slot, int quantity) {
        return new Item(itemId, (short) slot, (short) quantity);
    }

    private ByteBuffer packet(int index, int opcode) {
        ByteBuffer data = ByteBuffer.wrap(sent.get(index).getBytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(opcode, Short.toUnsignedInt(data.getShort()));
        return data;
    }

    private ByteBuffer inventoryPacket(int index, boolean releaseRequest, int count) {
        ByteBuffer data = packet(index, 0x1d);
        assertEquals(releaseRequest ? 1 : 0, data.get());
        assertEquals(count, Byte.toUnsignedInt(data.get()));
        return data;
    }

    private static void assertOperation(ByteBuffer data, int mode, byte type, int position) {
        assertEquals(mode, data.get());
        assertEquals(type, data.get());
        assertEquals(position, data.getShort());
    }

    private static void assertAddition(ByteBuffer data, byte type, int position, int itemId, int quantity, int ownerLength) {
        assertOperation(data, 0, type, position);
        assertEquals(2, data.get());
        assertEquals(itemId, data.getInt());
        assertEquals(0, data.get());
        data.getLong(); // expiration
        assertEquals(quantity, data.getShort());
        assertEquals(ownerLength, Short.toUnsignedInt(data.getShort()));
        data.position(data.position() + ownerLength);
        assertEquals(0, data.getShort());
    }
}
