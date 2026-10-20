/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

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
package org.gms.net.server.coordinator.world;

import org.gms.client.Character;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Ronan
 */
public class InviteCoordinator {

    public enum InviteResultType {
        ACCEPTED,
        DENIED,
        NOT_FOUND
    }

    public enum InviteType {
        //BUDDY, (not needed)
        FAMILY,
        FAMILY_SUMMON,
        MESSENGER,
        TRADE,
        PARTY,
        GUILD,
        ALLIANCE;

        private final ConcurrentHashMap<Integer, Invitation> invites = new ConcurrentHashMap<>();

        private void removeRequest(int target) {
            invites.remove(target);
        }

        private boolean addRequest(Character from, Object referenceFrom, int targetCid, Object[] params) {
            return invites.putIfAbsent(targetCid, new Invitation(from, referenceFrom, params)) == null;
        }

        private boolean hasRequest(int targetCid) {
            return invites.containsKey(targetCid);
        }
    }

    /** Publish and consume the complete invitation together; identity distinguishes replacements. */
    private static final class Invitation {
        final Character from;
        final Object reference;
        final Object[] params;
        int timeoutSweeps; // accessed only by ConcurrentHashMap.computeIfPresent

        Invitation(Character from, Object reference, Object[] params) {
            this.from = from;
            this.reference = reference;
            this.params = params;
        }
    }

    // note: referenceFrom is a specific value that represents the "common association" created between the sender/recver parties
    public static boolean createInvite(InviteType type, Character from, Object referenceFrom, int targetCid, Object... params) {
        return type.addRequest(from, referenceFrom, targetCid, params);
    }

    public static boolean hasInvite(InviteType type, int targetCid) {
        return type.hasRequest(targetCid);
    }

    public static InviteResult answerInvite(InviteType type, int targetCid, Object referenceFrom, boolean answer) {
        Invitation invitation = type.invites.get(targetCid);
        if (invitation == null || !referenceFrom.equals(invitation.reference)
                || !type.invites.remove(targetCid, invitation)) {
            return new InviteResult(InviteResultType.NOT_FOUND, null, new Object[0]);
        }
        Character from = invitation.from;
        if (from != null && !from.isLoggedInWorld()) {
            from = null;
        }
        return new InviteResult(answer ? InviteResultType.ACCEPTED : InviteResultType.DENIED,
                from, invitation.params);
    }

    public static void removeInvite(InviteType type, int targetCid) {
        type.removeRequest(targetCid);
    }

    public static void removePlayerIncomingInvites(int cid) {
        for (InviteType it : InviteType.values()) {
            it.removeRequest(cid);
        }
    }

    public static void runTimeoutSchedule() {
        for (InviteType it : InviteType.values()) {
            it.invites.forEach((target, observed) -> it.invites.computeIfPresent(target, (id, current) -> {
                if (current != observed) return current; // never age a replacement from an old scan
                return current.timeoutSweeps++ > 5 ? null : current;
            }));
        }
    }

    public static class InviteResult {

        public final InviteResultType result;
        public final Character from;
        public final Object[] params;

        private InviteResult(InviteResultType result, Character from, Object[] params) {
            this.result = result;
            this.from = from;
            this.params = params;
        }
    }
}
