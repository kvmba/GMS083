package org.gms.scripting.event;

import org.gms.client.Character;
import org.gms.extension.event.PartyQuestStageClearedEvent;
import org.gms.extension.event.PartyQuestStageWrongEvent;
import org.gms.extension.runtime.HostHooks;
import org.gms.manager.ServerManager;
import org.gms.property.ServiceProperty;
import org.gms.server.maps.MapleMap;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartyQuestStageClearedEventTest {

    @Test
    void publishesTheExactInstanceAndRoomSynchronouslyAfterTheClearEffect() {
        EventInstanceManager instance = mock(EventInstanceManager.class, CALLS_REAL_METHODS);
        MapleMap room = mock(MapleMap.class);
        doReturn(room).when(instance).getMapInstance(922010100);
        Thread clearingThread = Thread.currentThread();
        PartyQuestStageClearedEvent expected = new PartyQuestStageClearedEvent(instance, room);
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty properties = mock(ServiceProperty.class);
        when(properties.getLanguage()).thenReturn("zh-CN");
        when(context.getBean(ServiceProperty.class)).thenReturn(properties);

        try (var server = mockStatic(ServerManager.class);
             var hooks = mockStatic(HostHooks.class)) {
            server.when(ServerManager::getApplicationContext).thenReturn(context);
            hooks.when(() -> HostHooks.publish(any(PartyQuestStageClearedEvent.class)))
                    .thenAnswer(invocation -> {
                        PartyQuestStageClearedEvent event = invocation.getArgument(0);
                        assertSame(instance, event.instance());
                        assertSame(room, event.map());
                        assertSame(clearingThread, Thread.currentThread());
                        verify(room, times(2)).broadcastMessage(any());
                        return null;
                    });

            instance.showClearEffect(922010100);

            hooks.verify(() -> HostHooks.publish(expected));
        }
    }

    @Test
    void publishesTheExactRoomSynchronouslyAfterTheWrongEffect() {
        EventInstanceManager instance = mock(EventInstanceManager.class, CALLS_REAL_METHODS);
        MapleMap room = mock(MapleMap.class);
        doReturn(room).when(instance).getMapInstance(922010800);
        Thread scriptThread = Thread.currentThread();
        PartyQuestStageWrongEvent expected = new PartyQuestStageWrongEvent(instance, room);
        ApplicationContext context = mock(ApplicationContext.class);
        ServiceProperty properties = mock(ServiceProperty.class);
        when(properties.getLanguage()).thenReturn("zh-CN");
        when(context.getBean(ServiceProperty.class)).thenReturn(properties);

        try (var server = mockStatic(ServerManager.class);
             var hooks = mockStatic(HostHooks.class)) {
            server.when(ServerManager::getApplicationContext).thenReturn(context);
            Character leader = mock(Character.class);
            doReturn(leader).when(instance).getLeader();
            when(leader.getMapId()).thenReturn(922010800);
            hooks.when(() -> HostHooks.publish(expected)).thenAnswer(invocation -> {
                assertSame(scriptThread, Thread.currentThread());
                verify(room, times(2)).broadcastMessage(any());
                return null;
            });
            instance.showWrongEffect();
            hooks.verify(() -> HostHooks.publish(expected));
        }
    }
}
