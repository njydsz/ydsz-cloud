package com.njydsz.pmis.literule.cep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CEP 引擎单元测试（P2-13）
 */
class CEPEngineTest {

    private CEPEngine engine;
    private CopyOnWriteArrayList<CEPHit> hits;

    @BeforeEach
    void setUp() {
        engine = new CEPEngine();
        hits = new CopyOnWriteArrayList<>();
        engine.addListener(hits::add);
    }

    @Test
    void testTimeWindowTrigger() {
        CEPPattern p = CEPPatternFactory.timeWindow("LOGIN_FAIL",
                "rule_login_fail", "LOGIN_FAILED", Duration.ofMinutes(3), 3);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        // 投放 3 次失败登录
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(30)).build());
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(60)).build());

        assertEquals(1, hits.size());
        assertEquals("rule_login_fail", hits.get(0).getRuleCode());
        assertEquals(3, hits.get(0).getMatchedEvents().size());
    }

    @Test
    void testTimeWindowNoTriggerWhenBelowThreshold() {
        CEPPattern p = CEPPatternFactory.timeWindow("LOGIN_FAIL",
                "rule_login_fail", "LOGIN_FAILED", Duration.ofMinutes(3), 3);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        // 仅 2 次，未到阈值
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(30)).build());
        assertEquals(0, hits.size());
    }

    @Test
    void testTimeWindowSlidesOutOldEvents() {
        CEPPattern p = CEPPatternFactory.timeWindow("LOGIN_FAIL",
                "rule_login_fail", "LOGIN_FAILED", Duration.ofMinutes(1), 2);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        // 投放 2 个时间间隔很近的事件
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(30)).build());
        assertEquals(1, hits.size());

        // 再投放 1 个，但时间已超过窗口（base + 2 分钟），前面的会被裁剪
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(120)).build());
        // 此时窗口内仅 1 个事件，不应再触发
        assertEquals(1, hits.size());
    }

    @Test
    void testSequencePattern() {
        CEPPattern p = CEPPatternFactory.sequence("ORDER_FLOW",
                "rule_order", Duration.ofMinutes(5), List.of(
                        CEPPattern.SequenceStep.builder().order(1).eventType("ORDER_CREATED").build(),
                        CEPPattern.SequenceStep.builder().order(2).eventType("PAYMENT_RECEIVED").build(),
                        CEPPattern.SequenceStep.builder().order(3).eventType("ORDER_SHIPPED").build()));
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        engine.feed(CEPEvent.builder().type("ORDER_CREATED").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("PAYMENT_RECEIVED").timestamp(base.plusSeconds(10)).build());
        engine.feed(CEPEvent.builder().type("ORDER_SHIPPED").timestamp(base.plusSeconds(20)).build());

        assertEquals(1, hits.size());
        assertEquals(3, hits.get(0).getMatchedEvents().size());
    }

    @Test
    void testSequenceResetsOnWrongOrder() {
        CEPPattern p = CEPPatternFactory.sequence("ORDER_FLOW",
                "rule_order", Duration.ofMinutes(5), List.of(
                        CEPPattern.SequenceStep.builder().order(1).eventType("ORDER_CREATED").build(),
                        CEPPattern.SequenceStep.builder().order(2).eventType("PAYMENT_RECEIVED").build()));
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        engine.feed(CEPEvent.builder().type("ORDER_CREATED").timestamp(base).build());
        // 错的顺序：直接来 ORDER_SHIPPED
        engine.feed(CEPEvent.builder().type("ORDER_SHIPPED").timestamp(base.plusSeconds(10)).build());
        assertEquals(0, hits.size());
    }

    @Test
    void testAggregateCount() {
        CEPPattern p = CEPPatternFactory.aggregate("TASK_COUNT",
                "rule_task", "TASK_DONE", null, CEPPattern.AggregateFunction.COUNT,
                Duration.ofMinutes(10), 3);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        engine.feed(CEPEvent.builder().type("TASK_DONE").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("TASK_DONE").timestamp(base.plusSeconds(60)).build());
        engine.feed(CEPEvent.builder().type("TASK_DONE").timestamp(base.plusSeconds(120)).build());
        assertEquals(1, hits.size());
        assertEquals(3.0, hits.get(0).getMetric());
    }

    @Test
    void testAggregateSum() {
        CEPPattern p = CEPPatternFactory.aggregate("AMOUNT_SUM",
                "rule_amount", "PAYMENT", "amount",
                CEPPattern.AggregateFunction.SUM, Duration.ofMinutes(5), 1000.0);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        java.util.Map<String, Object> attrs1 = new java.util.HashMap<>();
        attrs1.put("amount", 500);
        engine.feed(CEPEvent.builder().type("PAYMENT").timestamp(base).attributes(attrs1).build());
        java.util.Map<String, Object> attrs2 = new java.util.HashMap<>();
        attrs2.put("amount", 700);
        engine.feed(CEPEvent.builder().type("PAYMENT").timestamp(base.plusSeconds(30)).attributes(attrs2).build());
        // 总和 1200 > 1000
        assertEquals(1, hits.size());
        assertEquals(1200.0, hits.get(0).getMetric());
    }

    @Test
    void testAbsencePattern() {
        CEPPattern p = CEPPatternFactory.absence("HEARTBEAT_MISSING",
                "rule_heartbeat", "HEARTBEAT", Duration.ofMinutes(2), 3);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        // 投放非 HEARTBEAT 事件
        engine.feed(CEPEvent.builder().type("OTHER").timestamp(base).build());
        engine.feed(CEPEvent.builder().type("OTHER").timestamp(base.plusSeconds(30)).build());
        engine.feed(CEPEvent.builder().type("OTHER").timestamp(base.plusSeconds(60)).build());
        // 窗口内无 HEARTBEAT
        assertEquals(1, hits.size());
    }

    @Test
    void testPartitionIsolation() {
        CEPPattern p = CEPPatternFactory.timeWindow("LOGIN_FAIL",
                "rule_login_fail", "LOGIN_FAILED", Duration.ofMinutes(3), 2);
        engine.registerPattern(p);

        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        // 用户A 1 次
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base)
                .partitionKey("userA").build());
        // 用户B 2 次
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base)
                .partitionKey("userB").build());
        engine.feed(CEPEvent.builder().type("LOGIN_FAILED").timestamp(base.plusSeconds(10))
                .partitionKey("userB").build());

        // 仅用户B 触发
        assertEquals(1, hits.size());
        assertEquals("userB", hits.get(0).getContext().get("partitionKey"));
    }

    @Test
    void testUnregisterPattern() {
        CEPPattern p = CEPPatternFactory.timeWindow("X", "rule_x", "X", Duration.ofMinutes(1), 1);
        engine.registerPattern(p);
        assertEquals(1, engine.patternCount());
        engine.unregisterPattern("X");
        assertEquals(0, engine.patternCount());
    }

    @Test
    void testClearPartition() {
        CEPPattern p = CEPPatternFactory.timeWindow("X", "rule_x", "X", Duration.ofMinutes(1), 1);
        engine.registerPattern(p);
        Instant base = Instant.parse("2024-01-01T10:00:00Z");
        engine.feed(CEPEvent.builder().type("X").timestamp(base).partitionKey("k1").build());
        assertEquals(1, hits.size());
        hits.clear();

        engine.clearPartition("X", "k1");
        engine.feed(CEPEvent.builder().type("X").timestamp(base).partitionKey("k1").build());
        // 清空后又触发
        assertEquals(1, hits.size());
    }
}
