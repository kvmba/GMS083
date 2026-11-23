package org.gms.extension.event;

import org.gms.extension.api.HostEvent;
import org.gms.scripting.event.EventInstanceManager;
import org.gms.server.maps.MapleMap;

/** A script rejected an attempt in this event room. Emitted on the script thread. */
public record PartyQuestStageWrongEvent(EventInstanceManager instance, MapleMap map) implements HostEvent {
}
