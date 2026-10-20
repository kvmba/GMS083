package org.gms.net.server.coordinator.world;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.gms.net.server.coordinator.world.InviteCoordinator.InviteResultType.*;
import static org.gms.net.server.coordinator.world.InviteCoordinator.InviteType.PARTY;
import static org.junit.jupiter.api.Assertions.*;

class InviteCoordinatorConcurrencyTest {
    private static final int TARGET = 78_100;

    @AfterEach
    void clearInvite() {
        InviteCoordinator.removeInvite(PARTY, TARGET);
    }

    @Test
    void metadataIsConsumedWithTheMatchedInviteAndOnlyOnce() {
        Object[] params = {"payload", 17};
        assertTrue(InviteCoordinator.createInvite(PARTY, null, 100, TARGET, params));
        assertFalse(InviteCoordinator.createInvite(PARTY, null, 200, TARGET));
        assertEquals(NOT_FOUND, InviteCoordinator.answerInvite(PARTY, TARGET, 200, true).result);
        var answer = InviteCoordinator.answerInvite(PARTY, TARGET, 100, true);
        assertEquals(ACCEPTED, answer.result);
        assertArrayEquals(params, answer.params);
        assertEquals(NOT_FOUND, InviteCoordinator.answerInvite(PARTY, TARGET, 100, true).result);
    }

    @Test
    void oldAnswerCannotConsumeAReplacementAfterTimeout() throws Exception {
        assertTrue(InviteCoordinator.createInvite(PARTY, null, 100, TARGET));
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch replaced = new CountDownLatch(1);
        AtomicReference<InviteCoordinator.InviteResult> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Object expected = new Object() {
            @Override
            public boolean equals(Object reference) {
                read.countDown();
                await(replaced);
                return Integer.valueOf(100).equals(reference);
            }

            @Override
            public int hashCode() {
                return 100;
            }
        };
        Thread answering = Thread.ofPlatform().start(() -> {
            try {
                result.set(InviteCoordinator.answerInvite(PARTY, TARGET, expected, true));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        try {
            await(read);
            for (int sweep = 0; sweep < 7; sweep++) InviteCoordinator.runTimeoutSchedule();
            assertFalse(InviteCoordinator.hasInvite(PARTY, TARGET));
            assertTrue(InviteCoordinator.createInvite(PARTY, null, 200, TARGET, "new payload"));
        } finally {
            replaced.countDown();
            answering.join(5_000);
        }
        assertFalse(answering.isAlive());
        assertNull(failure.get());
        assertEquals(NOT_FOUND, result.get().result);
        assertTrue(InviteCoordinator.hasInvite(PARTY, TARGET));
        var replacement = InviteCoordinator.answerInvite(PARTY, TARGET, 200, false);
        assertEquals(DENIED, replacement.result);
        assertArrayEquals(new Object[]{"new payload"}, replacement.params);
    }

    @Test
    void timeoutRetainsTheExistingSweepCadenceAndDoesNotResurrectEntries() {
        InviteCoordinator.createInvite(PARTY, null, 100, TARGET);
        for (int sweep = 0; sweep < 6; sweep++) {
            InviteCoordinator.runTimeoutSchedule();
            assertTrue(InviteCoordinator.hasInvite(PARTY, TARGET));
        }
        InviteCoordinator.runTimeoutSchedule();
        assertFalse(InviteCoordinator.hasInvite(PARTY, TARGET));
        InviteCoordinator.runTimeoutSchedule();
        assertFalse(InviteCoordinator.hasInvite(PARTY, TARGET));
        assertTrue(InviteCoordinator.createInvite(PARTY, null, 200, TARGET));
        InviteCoordinator.runTimeoutSchedule();
        assertEquals(ACCEPTED, InviteCoordinator.answerInvite(PARTY, TARGET, 200, true).result);
    }

    @Test
    void twoConcurrentAnswersCannotBothConsumeTheSameInvite() throws Exception {
        InviteCoordinator.createInvite(PARTY, null, 100, TARGET, "payload");
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch answerNow = new CountDownLatch(1);
        Object expected = new Object() {
            @Override
            public boolean equals(Object reference) {
                bothRead.countDown();
                await(answerNow);
                return Integer.valueOf(100).equals(reference);
            }

            @Override
            public int hashCode() {
                return 100;
            }
        };
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> InviteCoordinator.answerInvite(PARTY, TARGET, expected, true));
            var second = executor.submit(() -> InviteCoordinator.answerInvite(PARTY, TARGET, expected, true));
            try {
                await(bothRead);
            } finally {
                answerNow.countDown();
            }
            var answers = java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, answers.stream().filter(answer -> answer.result == ACCEPTED).count());
            assertEquals(1, answers.stream().filter(answer -> answer.result == NOT_FOUND).count());
            assertFalse(InviteCoordinator.hasInvite(PARTY, TARGET));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "interleaving timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
