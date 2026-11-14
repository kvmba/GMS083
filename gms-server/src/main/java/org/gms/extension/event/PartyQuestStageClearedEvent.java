package org.gms.extension.event;

import org.gms.extension.api.HostEvent;
import org.gms.scripting.event.EventInstanceManager;
import org.gms.server.maps.MapleMap;

/** A stage's clear effect was shown; listeners run before the clearing script continues. */
public record PartyQuestStageClearedEvent(EventInstanceManager instance, MapleMap map) implements HostEvent {
}
