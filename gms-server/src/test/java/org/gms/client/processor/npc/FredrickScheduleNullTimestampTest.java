package org.gms.client.processor.npc;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fredrick 定时任务对 NULL 时间戳的容错。
 *
 * <p>线上崩溃过一次：{@code DueyFredrickTask} 抛出
 * {@code NullPointerException: Cannot invoke "java.sql.Timestamp.getTime()"}。
 * 原因是 {@code fredstorage} 里的行与 {@code characters} LEFT JOIN 后，
 * 角色的 {@code lastLogoutTime} 取到的是 NULL（角色已删除的孤儿行，
 * 或从未正常下线过的历史数据），直接喂给了 {@code timestampElapsedDays}。
 */
class FredrickScheduleNullTimestampTest {

    private static final long NOW = 1_700_000_000_000L;

    @Test
    void nullTimestampIsTreatedAsNoElapsedTime() {
        // 曾经在这里抛 NPE。返回 0 = "当成刚刚发生/刚刚活跃"：
        // 不会误判为超过 7 天不活跃而静默掉，也不会误判为超过 100 天而清物品。
        assertEquals(0, FredrickProcessor.timestampElapsedDays(null, NOW));
    }

    @Test
    void elapsedDaysAreWholeDays() {
        Timestamp then = new Timestamp(NOW - DAYS.toMillis(3) - DAYS.toMillis(1) / 2);
        assertEquals(3, FredrickProcessor.timestampElapsedDays(then, NOW));
    }

    @Test
    void nullLogoutTimeDoesNotLookLikeAnInactivePlayer() {
        // 判断"长期不活跃则免打扰"的分支要求 inactivityDays >= 7。
        // NULL 必须落在该阈值之下，否则老角色会收不到取回提醒。
        int inactivityDays = FredrickProcessor.timestampElapsedDays(null, NOW);
        assertEquals(0, inactivityDays, "NULL 下线时间不应被当成长期不活跃");
        org.junit.jupiter.api.Assertions.assertTrue(inactivityDays < 7);
    }
}
