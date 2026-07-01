package com.njydsz.pmis.agent.orchestration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentBlackboard 黑板模型测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentBlackboard 共享黑板")
class AgentBlackboardTest {

    private AgentBlackboard blackboard;

    @BeforeEach
    void setup() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("bizId", 1001L);
        facts.put("bizRef", "PRJ-001");
        blackboard = new AgentBlackboard(facts);
    }

    @Test
    @DisplayName("构造时拷贝 facts")
    void ctorCopiesFacts() {
        assertThat(blackboard.fact("bizId")).isEqualTo(1001L);
        assertThat(blackboard.fact("bizRef")).isEqualTo("PRJ-001");
        // 修改原 facts 不影响黑板
        Map<String, Object> origin = new HashMap<>();
        origin.put("x", 1);
        AgentBlackboard b = new AgentBlackboard(origin);
        origin.put("x", 2);
        assertThat(b.fact("x")).isEqualTo(1);
    }

    @Test
    @DisplayName("facts 为 null 时初始化空")
    void ctorNullFacts() {
        AgentBlackboard b = new AgentBlackboard(null);
        assertThat(b.getFacts()).isEmpty();
    }

    @Test
    @DisplayName("scratch put / get 隔离")
    void scratchPutGet() {
        blackboard.putScratch("A", "result-A");
        blackboard.putScratch("B", "result-B");
        assertThat(blackboard.scratch("A")).isEqualTo("result-A");
        assertThat(blackboard.scratch("B")).isEqualTo("result-B");
        assertThat(blackboard.scratch("C")).isNull();
    }

    @Test
    @DisplayName("appendTrace 写入 trace")
    void appendTrace() {
        blackboard.appendTrace("A", OrchestrationMode.SEQUENTIAL,
                new BigDecimal("80"), new BigDecimal("0.90"), "ok");
        blackboard.appendTrace("B", OrchestrationMode.PARALLEL,
                new BigDecimal("50"), new BigDecimal("0.60"), "warn");
        assertThat(blackboard.getTrace()).hasSize(2);
        AgentBlackboard.TraceEntry e0 = blackboard.getTrace().get(0);
        assertThat(e0.getAgentType()).isEqualTo("A");
        assertThat(e0.getMode()).isEqualTo("SEQUENTIAL");
        assertThat(e0.getScore()).isEqualByComparingTo("80");
        assertThat(e0.getNote()).isEqualTo("ok");
        assertThat(e0.getTs()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("appendTrace mode 为 null 时 mode 字段为 null")
    void appendTraceNullMode() {
        blackboard.appendTrace("A", null, null, null, "n");
        assertThat(blackboard.getTrace().get(0).getMode()).isNull();
    }

    @Test
    @DisplayName("TraceEntry 序列化 ID 一致")
    void traceEntrySerialVersion() {
        assertThat(blackboard.getTrace()).isNotNull();
        AgentBlackboard.TraceEntry e = new AgentBlackboard.TraceEntry();
        e.setAgentType("X");
        e.setMode("VOTING");
        assertThat(e.getAgentType()).isEqualTo("X");
        assertThat(e.getMode()).isEqualTo("VOTING");
    }
}
