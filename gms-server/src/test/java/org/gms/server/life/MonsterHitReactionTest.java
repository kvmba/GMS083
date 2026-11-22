package org.gms.server.life;

import io.netty.buffer.Unpooled;
import org.gms.client.Character;
import org.gms.client.Client;
import org.gms.client.status.MonsterStatusEffect;
import org.gms.extension.runtime.HostHooks;
import org.gms.manager.ServerManager;
import org.gms.net.opcodes.SendOpcode;
import org.gms.net.packet.Packet;
import org.gms.net.packet.ByteBufInPacket;
import org.gms.net.server.Server;
import org.gms.net.server.channel.handlers.AbstractMovementPacketHandler;
import org.gms.net.server.channel.handlers.MoveLifeHandler;
import org.gms.property.ServiceProperty;
import org.gms.server.TimerManager;
import org.gms.server.maps.MapleMap;
import org.gms.service.ConfigService;
import org.gms.util.PacketCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonsterHitReactionTest {
    private final AtomicLong now = new AtomicLong(10_000);
    private final List<Runnable> pending = new ArrayList<>();
    private MockedStatic<Server> serverClock;
    private MockedStatic<TimerManager> timers;
    private TimerManager timer;
    private MapleMap map;
    private Character attacker;
    private Character controller;
    private Client client;
    private Monster mob;

    @BeforeAll
    static void bootHostContext() throws ReflectiveOperationException {
        ApplicationContext context = mock(ApplicationContext.class, RETURNS_DEEP_STUBS);
        ConfigService config = mock(ConfigService.class);
        when(config.loadGameConfigs()).thenReturn(List.of());
        when(context.getBean(ConfigService.class)).thenReturn(config);
        ServiceProperty properties = mock(ServiceProperty.class);
        when(properties.getLanguage()).thenReturn("zh-CN");
        when(context.getBean(ServiceProperty.class)).thenReturn(properties);
        var messages = mock(org.springframework.context.MessageSource.class);
        when(messages.getMessage(anyString(), any(Object[].class), any(java.util.Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        for (String name : List.of("messageSource", "logSource", "exceptionSource")) {
            when(context.getBean(name, org.springframework.context.MessageSource.class)).thenReturn(messages);
        }
        var setter = ServerManager.class.getDeclaredMethod("setApplicationContext", ApplicationContext.class);
        setter.setAccessible(true);
        setter.invoke(new ServerManager(), context);
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Server server = mock(Server.class);
        when(server.getCurrentTime()).thenAnswer(inv -> now.get());
        serverClock = mockStatic(Server.class);
        serverClock.when(Server::getInstance).thenReturn(server);
        timer = mock(TimerManager.class);
        doAnswer(inv -> {
            pending.add(inv.getArgument(0));
            return null;
        }).when(timer).schedule(any(Runnable.class), anyLong());
        timers = mockStatic(TimerManager.class);
        timers.when(TimerManager::getInstance).thenReturn(timer);

        map = mock(MapleMap.class);
        attacker = mock(Character.class);
        when(attacker.getMap()).thenReturn(map);
        client = mock(Client.class);
        controller = controller(client);
        MonsterStats stats = new MonsterStats();
        stats.setHp(1000);
        stats.setPushedDamage(100);
        stats.setHitAnimationTimes(new int[]{400});
        mob = new Monster(100100, stats);
        mob.setObjectId(123);
        mob.setPosition(new Point(40, -20));
        mob.setFh(9);
        mob.setMap(map);
        useController(controller);
        when(map.getMonsterByOid(123)).thenReturn(mob);
    }

    @AfterEach
    void closeMocks() {
        timers.close();
        serverClock.close();
    }

    @Test
    void aPeriodicBotSpellCannotApplyPoisonToAFriendlyEscort() {
        mob.getStats().setFriendly(true);
        MonsterStatusEffect poison = mock(MonsterStatusEffect.class);
        try (var host = mockStatic(HostHooks.class)) {
            host.when(() -> HostHooks.isArtificial(attacker)).thenReturn(true);
            assertFalse(mob.applyStatus(attacker, poison, true, 5_000));
            verifyNoInteractions(poison);
        }
    }

    @ParameterizedTest
    @CsvSource({"6,false", "6,true", "7,false", "8,true"})
    void hitPacketUsesTheV83OneTimeActionAndAValidMovementPath(int action, boolean left) {
        ByteBuffer p = packet(PacketCreator.monsterHit(mob, action, left, 400));
        assertEquals(SendOpcode.MOVE_MONSTER.getValue(), p.getShort() & 0xffff);
        assertEquals(123, p.getInt());
        assertEquals(0, p.get());
        assertEquals(1, p.get(), "OnMove must not discard the packet on its controlling client");
        assertEquals((action << 1) | (left ? 1 : 0), p.get());
        assertEquals(0, p.getInt());
        assertPosition(p);
        assertEquals(1, p.get());
        assertEquals(0, p.get());
        assertPosition(p);
        assertEquals(0, p.getInt(), "zero velocity; do not teleport or fabricate a foothold");
        assertEquals(9, p.getShort());
        assertEquals(left ? 5 : 4, p.get());
        assertEquals(400, p.getShort());
        assertFalse(p.hasRemaining());
    }

    @Test
    void reactionRestoresTheSameControllerOnlyAfterTheHitWithoutApplyingDamageAgain() {
        mob.showHitReaction(attacker, 100, 0);

        Packet movement = movement();
        assertEquals(13, movement.getBytes()[8] & 0xff, "v83 hit1 = (6 << 1) | left");
        verify(client, never()).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
        now.addAndGet(400);
        pending.removeFirst().run();
        var order = inOrder(map, client);
        order.verify(client).sendPacket(argThat(p ->
                (packet(p).getShort() & 0xffff) == SendOpcode.SPAWN_MONSTER_CONTROL.getValue()
                        && p.getBytes()[2] == 0));
        order.verify(map).broadcastMessage(movement, mob.getPosition());
        order.verify(client).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
        assertSame(controller, mob.getController());
        assertEquals(1000, mob.getHp());
        verify(controller, never()).stopControllingMonster(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void aHitReplacesAWalkingStanceWithAStationaryPath(int movementType) {
        mob.getStats().setMovetype(movementType);
        mob.setStance(2);
        mob.showHitReaction(attacker, 100, 0);
        int stance = movementType == 1 ? 13 : 5;
        assertEquals(stance, mob.getStance());
        assertEquals(stance, movement().getBytes()[29] & 0xff);
    }

    @Test
    void inFlightMovementCannotOverwriteTheHitAndMovementResumesAfterIt() {
        mob = spy(mob);
        doReturn(0).when(mob).canUseAttack(anyInt(), anyBoolean());
        when(map.getMonsterByOid(123)).thenReturn(mob);
        when(client.getPlayer()).thenReturn(controller);
        when(map.getMapObject(123)).thenReturn(mob);
        mob.showHitReaction(attacker, 100, 0);
        Point hitPosition = new Point(mob.getPosition());
        MoveLifeHandler handler = new MoveLifeHandler();

        handler.handlePacket(walkingPacket(), client);

        assertEquals(hitPosition, mob.getPosition());
        verify(map, never()).moveMonster(any(), any());
        verify(client, never()).sendPacket(argThat(p ->
                (packet(p).getShort() & 0xffff) == SendOpcode.MOVE_MONSTER_RESPONSE.getValue()));
        now.addAndGet(400);
        pending.removeFirst().run();
        handler.handlePacket(walkingPacket(), client);
        assertEquals(new Point(120, -20), mob.getPosition());
        verify(map).moveMonster(mob, new Point(120, -20));
        verify(client).sendPacket(argThat(p ->
                (packet(p).getShort() & 0xffff) == SendOpcode.MOVE_MONSTER_RESPONSE.getValue()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aMoveCannotReactivateAMobRemovedWhileWaitingForItsLock(int change) {
        mob = spy(mob);
        doReturn(0).when(mob).canUseAttack(anyInt(), anyBoolean());
        AtomicReference<Monster> mapped = new AtomicReference<>(mob);
        when(client.getPlayer()).thenReturn(controller);
        when(map.getMapObject(123)).thenAnswer(inv -> mapped.get());
        doAnswer(inv -> {
            inv.callRealMethod();
            switch (change) {
                case 0 -> mob.disposeMapObject();
                case 1 -> mapped.set(null);
                case 2 -> mob.setMap(mock(MapleMap.class));
            }
            return null;
        }).when(mob).lockMonster();

        new MoveLifeHandler().handlePacket(walkingPacket(), client);

        assertEquals(new Point(40, -20), mob.getPosition());
        verify(client, never()).sendPacket(any(Packet.class));
        verify(map, never()).moveMonster(any(), any());
    }

    @Test
    void anAggroRefreshCannotReactivateLocalPhysicsDuringTheHit() {
        mob.showHitReaction(attacker, 100, 0);
        try (var config = mockStatic(org.gms.config.GameConfig.class)) {
            mob.aggroAutoAggroUpdate(controller);
        }
        verify(client, never()).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
        now.addAndGet(400);
        pending.removeFirst().run();
        verify(client).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
    }

    @ParameterizedTest
    @CsvSource({"100100,600", "9400101,120", "9870029,120", "2220000,600"})
    void realWzHitTimingsMatchClientDefaults(int templateId, int duration) {
        try (var information = mockStatic(MonsterInformationProvider.class)) {
            information.when(MonsterInformationProvider::getInstance)
                    .thenReturn(mock(MonsterInformationProvider.class));
            MonsterStats stats = LifeFactory.getMonster(templateId).getStats();
            assertEquals(duration, stats.getHitAnimationTime(0),
                    "missing frame delays default to 120ms; non-frame metadata is not an animation");
        }
    }

    @Test
    void anExpiredResumeCannotInterruptANewerHit() {
        mob.showHitReaction(attacker, 100, 0);
        Runnable firstResume = pending.removeFirst();
        now.addAndGet(400);
        mob.showHitReaction(attacker, 100, 0);
        firstResume.run();
        verify(client, never()).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
        now.addAndGet(400);
        pending.removeFirst().run();
        verify(client).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void resumingAHitCannotReviveARemovedMobOrFollowTheMobAcrossMaps(int change) {
        mob.showHitReaction(attacker, 100, 0);
        switch (change) {
            case 0 -> mob.disposeMapObject();
            case 1 -> when(map.getMonsterByOid(123)).thenReturn(null);
            case 2 -> mob.setMap(mock(MapleMap.class));
        }
        now.addAndGet(400);
        pending.removeFirst().run();
        verify(client, never()).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
    }

    @Test
    void resumingUsesTheCurrentControllerEvenWhenTheAttackerHasLeft() throws Exception {
        mob.showHitReaction(attacker, 100, 0);
        Client replacementClient = mock(Client.class);
        useController(controller(replacementClient));
        when(attacker.getMap()).thenReturn(mock(MapleMap.class));
        now.addAndGet(400);
        pending.removeFirst().run();
        verify(replacementClient).sendPacket(argThat(MonsterHitReactionTest::isStartControl));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15})
    void aHitUsesTheCurrentFootholdRatherThanTheSpawnFoothold(int command) throws Exception {
        ByteBuffer movement = ByteBuffer.allocate(command == 15 ? 17 : 15).order(ByteOrder.LITTLE_ENDIAN);
        movement.put((byte) 1).put((byte) command);
        movement.putShort((short) 40).putShort((short) -20).putInt(0);
        movement.putShort((short) 12);
        if (command == 15) movement.putShort((short) 9);
        movement.put((byte) 4).putShort((short) 100);
        AbstractMovementPacketHandler.updatePosition(
                new ByteBufInPacket(Unpooled.wrappedBuffer(movement.array())), mob, 0);
        assertEquals(12, mob.getFh());
        assertEquals(12, packet(PacketCreator.monsterHit(mob, 6, true, 400)).getShort(27));
    }

    @Test
    void actionWaitsForTheAttackHitDelayWithoutSleeping() {
        mob.showHitReaction(attacker, 100, 300);
        verify(timer).schedule(any(Runnable.class), eq(300L));
        verify(map, never()).broadcastMessage(any(Packet.class), any(Point.class));
        pending.removeFirst().run();
        movement();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 99})
    void missesAndDamageBelowPushedDoNotScheduleAHit(int damage) {
        mob.showHitReaction(attacker, damage, 300);
        verifyNoInteractions(timer, client);
        verify(map, never()).broadcastMessage(any(Packet.class), any(Point.class));
    }

    @Test
    void mobsWithoutHitFramesAndUnobservedMapsNeedNoReactionTask() throws ReflectiveOperationException {
        mob.getStats().setHitAnimationTimes(new int[0]);
        mob.showHitReaction(attacker, 100, 300);
        mob.getStats().setHitAnimationTimes(new int[]{400});
        useController(null);
        mob.showHitReaction(attacker, 100, 300);
        verifyNoInteractions(timer, client);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aDelayedHitCannotReviveARemovedMobOrFollowAWarp(int change) {
        mob.showHitReaction(attacker, 100, 300);
        switch (change) {
            case 0 -> mob.disposeMapObject();
            case 1 -> when(map.getMonsterByOid(123)).thenReturn(null);
            case 2 -> when(attacker.getMap()).thenReturn(mock(MapleMap.class));
        }
        pending.removeFirst().run();
        verifyNoInteractions(client);
        verify(map, never()).broadcastMessage(any(Packet.class), any(Point.class));
    }

    @Test
    void aControllerHandoffDuringTheDelayKeepsTheNewController() throws ReflectiveOperationException {
        mob.showHitReaction(attacker, 100, 300);
        Client replacementClient = mock(Client.class);
        Character replacement = controller(replacementClient);
        useController(replacement);
        pending.removeFirst().run();
        movement();
        verifyNoInteractions(client);
        verify(replacementClient).sendPacket(any(Packet.class));
        assertSame(replacement, mob.getController());
    }

    @Test
    void aPlayingHitIsNotRestartedBeforeItsWzDurationExpires() {
        mob.showHitReaction(attacker, 100, 0);
        now.set(10_399);
        mob.showHitReaction(attacker, 100, 0);
        now.set(10_400);
        mob.showHitReaction(attacker, 100, 0);
        verify(map, times(2)).broadcastMessage(any(Packet.class), eq(mob.getPosition()));
    }

    @Test
    void fixedFacingIsKeptAndTheBossFlagAloneDoesNotSuppressAValidHit() {
        mob.getStats().setFixedStance(4);
        mob.getStats().setBoss(true);
        mob.showHitReaction(attacker, 100, 0);
        assertEquals(12, movement().getBytes()[8] & 0xff);
    }

    private Character controller(Client socket) {
        Character player = mock(Character.class);
        when(player.getMap()).thenReturn(map);
        when(player.isLoggedInWorld()).thenReturn(true);
        when(player.isMapObjectVisible(any())).thenReturn(true);
        when(player.getClient()).thenReturn(socket);
        doAnswer(inv -> {
            socket.sendPacket(inv.getArgument(0));
            return null;
        }).when(player).sendPacket(any(Packet.class));
        return player;
    }

    private void useController(Character player) throws ReflectiveOperationException {
        var setter = Monster.class.getDeclaredMethod("setController", Character.class);
        setter.setAccessible(true);
        setter.invoke(mob, player);
    }

    private Packet movement() {
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(map).broadcastMessage(packets.capture(), eq(mob.getPosition()));
        return packets.getValue();
    }

    private static ByteBuffer packet(Packet p) {
        return ByteBuffer.wrap(p.getBytes()).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static boolean isStartControl(Packet p) {
        return (packet(p).getShort() & 0xffff) == SendOpcode.SPAWN_MONSTER_CONTROL.getValue()
                && p.getBytes()[2] != 0;
    }

    private static ByteBufInPacket walkingPacket() {
        ByteBuffer p = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        p.putInt(123).putShort((short) 7).put((byte) 0).put((byte) -1);
        p.putInt(0).putLong(0).put((byte) 0).putInt(0);
        p.putShort((short) 40).putShort((short) -18);
        p.put((byte) 1).put((byte) 0);
        p.putShort((short) 120).putShort((short) -18).putInt(0);
        p.putShort((short) 9).put((byte) 0).putShort((short) 100);
        return new ByteBufInPacket(Unpooled.wrappedBuffer(p.array()));
    }

    private static void assertPosition(ByteBuffer p) {
        assertEquals(40, p.getShort());
        assertEquals(-20, p.getShort());
    }
}
