package com.njydsz.pmis.execution.engine.alert;

import com.njydsz.pmis.execution.dto.AlertEventDTO;
import com.njydsz.pmis.execution.enums.AlertSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预警规则引擎单元测试
 *
 * <p>覆盖：
 *  - 4 条规则（EVM/Margin/Bench/Utilization）独立触发
 *  - 规则优先级（严重度排序）
 *  - 规则异常隔离（一条抛错不影响其他）
 *  - 空快照与 null 防御
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class AlertRuleEngineTest {

    private AlertRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AlertRuleEngine()
                .register(new EvmRedRule(3))
                .register(new MarginLowRule(new BigDecimal("0.10"), new BigDecimal("0.05")))
                .register(new BenchHighRule(new BigDecimal("500000"), new BigDecimal("1000000")))
                .register(new UtilizationLowRule(new BigDecimal("0.70"), new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("健康快照下不应触发任何规则")
    void healthySnapshotNoTrigger() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("evmYellowCount", 1);
        snap.put("evmGreenCount", 5);
        snap.put("grossMargin", new BigDecimal("0.25"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("EVM 红色项目 5 个 > 阈值 3 触发 RED")
    void evmRedTriggers() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 5);
        snap.put("grossMargin", new BigDecimal("0.25"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRuleCode()).isEqualTo("EVM_RED_EXCESS");
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.RED);
        assertThat(events.get(0).getCurrentValue()).isEqualTo("5");
    }

    @Test
    @DisplayName("毛利率 0.03 < 0.05 触发 RED")
    void marginRedTriggers() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("grossMargin", new BigDecimal("0.03"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRuleCode()).isEqualTo("MARGIN_LOW");
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.RED);
    }

    @Test
    @DisplayName("毛利率 0.08 ∈ [0.05, 0.10) 触发 YELLOW")
    void marginYellowTriggers() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("grossMargin", new BigDecimal("0.08"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRuleCode()).isEqualTo("MARGIN_LOW");
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.YELLOW);
    }

    @Test
    @DisplayName("Bench 闲置 80 万触发 YELLOW，120 万触发 RED")
    void benchTriggers() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("grossMargin", new BigDecimal("0.25"));
        snap.put("benchIdleCost", new BigDecimal("800000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRuleCode()).isEqualTo("BENCH_IDLE_COST_HIGH");
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.YELLOW);

        snap.put("benchIdleCost", new BigDecimal("1200000"));
        events = engine.evaluate(snap);
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.RED);
    }

    @Test
    @DisplayName("利用率 0.40 < 0.50 触发 RED，0.60 ∈ [0.50, 0.70) 触发 YELLOW")
    void utilizationTriggers() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("grossMargin", new BigDecimal("0.25"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.40"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRuleCode()).isEqualTo("UTILIZATION_LOW");
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.RED);

        snap.put("avgBillableUtilization", new BigDecimal("0.60"));
        events = engine.evaluate(snap);
        assertThat(events.get(0).getSeverity()).isEqualTo(AlertSeverity.YELLOW);
    }

    @Test
    @DisplayName("多条规则同时触发：按严重度倒序（RED 在前）")
    void multipleTriggersSortedBySeverity() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 5);              // RED
        snap.put("grossMargin", new BigDecimal("0.08")); // YELLOW
        snap.put("benchIdleCost", new BigDecimal("1200000")); // RED
        snap.put("avgBillableUtilization", new BigDecimal("0.60")); // YELLOW
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).hasSize(4);
        // 两条 RED 应排前
        for (int i = 0; i < 2; i++) {
            assertThat(events.get(i).getSeverity()).isEqualTo(AlertSeverity.RED);
        }
        for (int i = 2; i < 4; i++) {
            assertThat(events.get(i).getSeverity()).isEqualTo(AlertSeverity.YELLOW);
        }
    }

    @Test
    @DisplayName("单条规则异常不影响其他规则")
    void ruleExceptionIsolated() {
        AlertRule broken = new AlertRule() {
            @Override public String getCode() { return "BROKEN_RULE"; }
            @Override public String getName() { return "Broken"; }
            @Override public String getCategory() { return "TEST"; }
            @Override public AlertEventDTO evaluate(Map<String, Object> snapshot) {
                throw new RuntimeException("boom");
            }
        };
        engine.register(broken);

        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 0);
        snap.put("grossMargin", new BigDecimal("0.25"));
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        // 不应抛错，应返回空
        List<AlertEventDTO> events = engine.evaluate(snap);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("null / 空快照 防御")
    void nullSnapshotSafe() {
        assertThat(engine.evaluate(null)).isEmpty();
        assertThat(engine.evaluate(new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("topAlert 返回最高严重度事件（多条触发时取首条）")
    void topAlertReturnsHighestSeverity() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("evmRedCount", 5);                       // RED
        snap.put("grossMargin", new BigDecimal("0.08"));  // YELLOW
        snap.put("benchIdleCost", new BigDecimal("100000"));
        snap.put("avgBillableUtilization", new BigDecimal("0.85"));
        snap.put("activeProjects", 5);
        snap.put("confirmedRevenue", new BigDecimal("1000"));

        AlertEventDTO top = engine.topAlert(snap);
        assertThat(top).isNotNull();
        assertThat(top.getSeverity()).isEqualTo(AlertSeverity.RED);
    }

    @Test
    @DisplayName("topAlert 在无任何触发时返回 null")
    void topAlertNullWhenNoTrigger() {
        AlertEventDTO top = engine.topAlert(new HashMap<>());
        assertThat(top).isNull();
    }

    @Test
    @DisplayName("severityWeight 数值化正确")
    void severityWeightMapping() {
        assertThat(AlertRuleEngine.severityWeightByCode("RED")).isEqualTo(3);
        assertThat(AlertRuleEngine.severityWeightByCode("YELLOW")).isEqualTo(2);
        assertThat(AlertRuleEngine.severityWeightByCode("INFO")).isEqualTo(1);
        assertThat(AlertRuleEngine.severityWeightByCode(null)).isEqualTo(0);
        assertThat(AlertRuleEngine.severityWeightByCode("UNKNOWN")).isEqualTo(0);
    }

    @Test
    @DisplayName("getRules 返回只读列表")
    void getRulesReadOnly() {
        List<AlertRule> rules = engine.getRules();
        assertThat(rules).hasSize(4);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> rules.add(new EvmRedRule()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("register null 不抛错，registerAll null 不抛错")
    void registerNullSafe() {
        AlertRuleEngine e = new AlertRuleEngine();
        e.register(null);
        e.registerAll(null);
        assertThat(e.getRules()).isEmpty();
    }
}
