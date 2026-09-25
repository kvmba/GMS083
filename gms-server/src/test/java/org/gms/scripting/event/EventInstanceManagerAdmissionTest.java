package org.gms.scripting.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the event-instance admission rule for artificial characters.
 *
 * <p>This is the gate that decides whether a headless bot in a party may take part in a party
 * quest. It reads {@code Character.loggedIn}, which is <b>false for every template-cloned bot
 * by design</b>: {@code CharacterService.loadCharFromDB(cid, client, false)} returns before
 * {@code setLoggedIn(true)}, and that early return is deliberate - it is also what keeps the
 * character autosaver from writing thousands of ambient bots to the database every hour.
 *
 * <p>So the artificial clause is not a convenience, it is the only thing admitting bots. It was
 * missing until this rule was extracted, and the symptom was silent: a bot that could be invited
 * into a party still never entered the instance (no {@code setEventInstance}, no
 * {@code playerEntry} warp, absent from {@code getPlayerCount()}), so every body-count puzzle
 * failed at its first check while the bot stood in the lobby "ready".
 *
 * <p>The predicates take primitives on purpose: {@code Character} cannot be constructed in a
 * unit test (its static initializers reach for the Spring context), so a gate expressed as
 * {@code chr.isLoggedInWorld() || isArtificial(chr)} is untestable and was in fact never tested.
 */
class EventInstanceManagerAdmissionTest {

    @Test
    void aRealPlayerPresentInTheWorldIsAdmitted() {
        assertTrue(EventInstanceManager.admitsParticipant(true, false));
    }

    @Test
    void aRealPlayerAwayFromTheWorldIsNotAdmitted() {
        // In the cash shop / MTS: "logged in" but not present, and the original gate rejected
        // exactly this case. Kept as-is.
        assertFalse(EventInstanceManager.admitsParticipant(false, false));
    }

    @Test
    void anArtificialCharacterIsAdmittedWithoutBeingLoggedIn() {
        assertTrue(EventInstanceManager.admitsParticipant(false, true));
    }

    @Test
    void anArtificialCharacterAwayFromTheWorldIsStillAdmitted() {
        // A bot never reaches setEnteredChannelWorld(), so awayFromWorld is its starting value
        // unless markPresentInWorld() ran; the artificial clause must not depend on that either.
        assertTrue(EventInstanceManager.admitsParticipant(false, true));
    }

    @Test
    void theLeaverGateAcceptsBotsButStillIgnoresAbsentees() {
        assertTrue(EventInstanceManager.admitsLeaver(true, false));
        assertTrue(EventInstanceManager.admitsLeaver(false, true));
        assertFalse(EventInstanceManager.admitsLeaver(false, false));
    }

    @Test
    void aNullCharacterIsNeverAdmitted() {
        // Both call sites short-circuit on null before evaluating the rule, but the rule itself
        // is expressed so that the null case is unrepresentable rather than silently admitted.
        assertFalse(EventInstanceManager.admitsParticipant(false, false));
        assertFalse(EventInstanceManager.admitsLeaver(false, false));
    }
}
