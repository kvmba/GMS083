package org.gms.server;

import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.config.GameConfig;
import org.gms.dao.entity.GameConfigDO;
import org.gms.dao.mapper.GameConfigMapper;
import org.gms.exception.BizException;
import org.gms.manager.ServerManager;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.InPacket;
import org.gms.net.packet.Packet;
import org.gms.net.server.channel.handlers.CashOperationHandler;
import org.gms.net.server.channel.handlers.EnterCashShopHandler;
import org.gms.net.server.Server;
import org.gms.property.ServiceProperty;
import org.gms.service.ConfigService;
import org.gms.service.LangResourceService;
import org.gms.service.NoteService;
import org.gms.util.PacketCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageExpansionTest {
    @BeforeAll
    static void bootHostContext() throws ReflectiveOperationException {
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
        var setter = ServerManager.class.getDeclaredMethod("setApplicationContext", ApplicationContext.class);
        setter.setAccessible(true);
        setter.invoke(new ServerManager(), context);
    }

    @BeforeEach
    void setDefaultLimit() {
        GameConfig.add(config("48"));
    }

    @AfterEach
    void clearLimit() {
        GameConfig.remove(config("48"));
    }

    @Test
    void missingConfigKeepsTheOriginal48SlotLimit() {
        GameConfig.remove(config("48"));
        assertEquals(48, Storage.getSlotLimit());
    }

    @ParameterizedTest
    @CsvSource({
            "48,44,4,true", "48,48,4,false",
            "96,92,4,true", "124,120,4,true",
            "124,116,8,true", "124,120,8,false", "124,124,4,false"
    })
    void purchasesRespectTheConfiguredLimit(int limit, int owned, int quantity, boolean accepted)
            throws ReflectiveOperationException {
        GameConfig.add(config(Integer.toString(limit)));
        Storage storage = storage(owned);
        assertEquals(accepted, storage.canGainSlots(quantity));
        assertEquals(accepted, storage.gainSlots(quantity));
        assertEquals(owned + (accepted ? quantity : 0), storage.getSlots());
    }

    @Test
    void loweringTheLimitDoesNotShrinkOrHideAlreadyPurchasedSlots() throws ReflectiveOperationException {
        Storage storage = storage(124);
        for (int i = 0; i < 124; i++) {
            assertTrue(storage.store(new Item(4_000_000 + i, (short) 0, (short) 1)));
        }
        assertFalse(storage.canGainSlots(4));
        assertEquals(124, storage.getSlots());
        assertEquals(124, storage.toSnapshot().slots);
        assertTrue(storage.isFull());
        assertFalse(storage.store(new Item(4_000_124, (short) 0, (short) 1)));
        assertEquals(123, storage.getSlot(InventoryType.ETC, (byte) 123));
        assertEquals(4_000_123, storage.getItem((byte) 123).getItemId());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 48, 96, 124})
    void theClientLimitPacketUsesTheAgreedOpcodeAndOneByte(int limit) {
        GameConfig.add(config(Integer.toString(limit)));
        ByteBuffer packet = packet(PacketCreator.storageSlotLimit());
        assertEquals(0x1001, Short.toUnsignedInt(packet.getShort()));
        assertEquals(limit, Byte.toUnsignedInt(packet.get()));
        assertFalse(packet.hasRemaining());
    }

    @Test
    void theLimitIsSentBeforeCreatingTheCashShopAndLoadingItsInventory() {
        Client client = mock(Client.class, RETURNS_DEEP_STUBS);
        when(client.getPlayer().getEventInstance()).thenReturn(null);
        Packet limit = mock(Packet.class);
        Packet opened = mock(Packet.class);
        Packet inventory = mock(Packet.class);
        try (var server = mockStatic(Server.class); var packets = mockStatic(PacketCreator.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class, RETURNS_DEEP_STUBS));
            packets.when(PacketCreator::storageSlotLimit).thenReturn(limit);
            packets.when(() -> PacketCreator.openCashShop(client, false)).thenReturn(opened);
            packets.when(() -> PacketCreator.showCashInventory(client)).thenReturn(inventory);
            new EnterCashShopHandler().handlePacket(mock(InPacket.class), client);
            ArgumentCaptor<Packet> sent = ArgumentCaptor.forClass(Packet.class);
            verify(client, times(6)).sendPacket(sent.capture());
            assertEquals(List.of(limit, opened, inventory), sent.getAllValues().subList(0, 3));
            verify(client.getPlayer().getCashShop()).open(true);
            verify(client.getPlayer()).saveCharToDB();
        }
    }

    @Test
    void directDatabaseValuesCannotExceedTheSupportedCeiling() {
        GameConfig.add(config("128"));
        assertEquals(124, Storage.getSlotLimit());
    }

    @Test
    void existingStoragePacketFormatsStillCarry124Slots() {
        byte slots = 124;
        ByteBuffer opened = packet(PacketCreator.getStorage(9_010_000, slots, List.of(), 0));
        assertEquals(SendOpcode.STORAGE.getValue(), Short.toUnsignedInt(opened.getShort()));
        assertEquals(0x16, Byte.toUnsignedInt(opened.get()));
        assertEquals(9_010_000, opened.getInt());
        assertEquals(124, Byte.toUnsignedInt(opened.get()));
        for (Packet packet : List.of(
                PacketCreator.mesoStorage(slots, 0),
                PacketCreator.storeStorage(slots, InventoryType.ETC, List.of()),
                PacketCreator.takeOutStorage(slots, InventoryType.ETC, List.of()),
                PacketCreator.arrangeStorage(slots, List.of()))) {
            assertEquals(124, Byte.toUnsignedInt(packet.getBytes()[3]));
        }
        ByteBuffer bought = packet(PacketCreator.showBoughtStorageSlots((short) slots));
        bought.position(3);
        assertEquals(124, Short.toUnsignedInt(bought.getShort()));
        assertFalse(bought.hasRemaining());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "3", "125", "128", "not-an-integer"})
    void theConfigApiRejectsUnsupportedLimitsBeforeWriting(String value) {
        GameConfigMapper mapper = mock(GameConfigMapper.class);
        when(mapper.selectOneById(1L)).thenReturn(config("48"));
        LangResourceService languages = mock(LangResourceService.class);
        ConfigService service = new ConfigService(mapper, mock(ServiceProperty.class), languages);
        assertThrows(BizException.class, () -> service.updateConfig(config(value)));
        verifyNoInteractions(languages);
    }

    @Test
    void theConfigApiAccepts124AndUpdatesTheLiveLimit() {
        GameConfigMapper mapper = mock(GameConfigMapper.class);
        when(mapper.selectOneById(1L)).thenReturn(config("48"));
        ConfigService service = new ConfigService(mapper, mock(ServiceProperty.class),
                mock(LangResourceService.class));
        service.updateConfig(config("124"));
        assertEquals(124, Storage.getSlotLimit());
    }

    @Test
    void theFinalFourSlotsCanBeBoughtAndConfirmed() throws ReflectiveOperationException {
        GameConfig.add(config("124"));
        Storage storage = storage(120);
        Client client = client(storage);
        new CashOperationHandler(mock(NoteService.class)).handlePacket(purchase(), client);
        assertEquals(124, storage.getSlots());
        verify(client.getPlayer().getCashShop()).gainCash(1, -4000);
        verify(client.getPlayer()).setUsedStorage();
        ArgumentCaptor<Packet> sent = ArgumentCaptor.forClass(Packet.class);
        verify(client, times(2)).sendPacket(sent.capture());
        ByteBuffer confirmation = packet(sent.getAllValues().getFirst());
        assertEquals(SendOpcode.CASHSHOP_OPERATION.getValue(), Short.toUnsignedInt(confirmation.getShort()));
        assertEquals(0x62, Byte.toUnsignedInt(confirmation.get()));
        assertEquals(124, Short.toUnsignedInt(confirmation.getShort()));
    }

    @Test
    void aRejectedPurchaseDoesNotChargeCash() throws ReflectiveOperationException {
        GameConfig.add(config("124"));
        Client client = client(storage(124));
        new CashOperationHandler(mock(NoteService.class)).handlePacket(purchase(), client);
        verify(client.getPlayer().getCashShop(), never()).gainCash(anyInt(), anyInt());
        verify(client).enableCSActions();
    }

    @Test
    void aLimitChangeBetweenCheckingAndGrantingDoesNotChargeCash() {
        Storage storage = mock(Storage.class);
        when(storage.canGainSlots(4)).thenReturn(true);
        when(storage.gainSlots(4)).thenReturn(false);
        Client client = client(storage);
        new CashOperationHandler(mock(NoteService.class)).handlePacket(purchase(), client);
        verify(client.getPlayer().getCashShop(), never()).gainCash(anyInt(), anyInt());
        verify(client).enableCSActions();
    }

    private static GameConfigDO config(String value) {
        return GameConfigDO.builder().id(1L).configType("server").configSubType("Game Mechanics")
                .configCode(Storage.SLOT_LIMIT_CONFIG).configClazz("java.lang.Integer")
                .configValue(value).configDesc(Storage.SLOT_LIMIT_CONFIG).build();
    }

    private static Storage storage(int slots) throws ReflectiveOperationException {
        var constructor = Storage.class.getDeclaredConstructor(int.class, byte.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, (byte) slots, 0);
    }

    private static Client client(Storage storage) {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        CashShop shop = mock(CashShop.class);
        when(client.getPlayer()).thenReturn(player);
        when(client.tryacquireClient()).thenReturn(true);
        when(player.getName()).thenReturn("storage-test");
        when(player.getStorage()).thenReturn(storage);
        when(player.getCashShop()).thenReturn(shop);
        when(shop.isOpened()).thenReturn(true);
        when(shop.getCash(1)).thenReturn(10_000);
        return client;
    }

    private static InPacket purchase() {
        InPacket packet = mock(InPacket.class);
        when(packet.readByte()).thenReturn((byte) 7, (byte) 0);
        when(packet.readInt()).thenReturn(1);
        return packet;
    }

    private static ByteBuffer packet(Packet packet) {
        return ByteBuffer.wrap(packet.getBytes()).order(ByteOrder.LITTLE_ENDIAN);
    }
}
