package com.njydsz.pmis.execution.engine.alert;

import com.njydsz.pmis.execution.dto.AlertEventDTO;
import com.njydsz.pmis.execution.enums.AlertSeverity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Bench 闲置成本过高规则
 *
 * <p>当累计 Bench 闲置成本超过阈值时触发。缺省阈值 50 万元。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class BenchHighRule implements AlertRule {

    /** 缺省红色阈值 = 1,000,000 元 */
    public static final BigDecimal DEFAULT_RED = new BigDecimal("1000000");
    /** 缺省黄色阈值 = 500,000 元 */
    public static final BigDecimal DEFAULT_YELLOW = new BigDecimal("500000");

    private final BigDecimal yellowThreshold;
    private final BigDecimal redThreshold;

    public BenchHighRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    public BenchHighRule(BigDecimal yellowThreshold, BigDecimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    @Override
    public String getCode() {
        return "BENCH_IDLE_COST_HIGH";
    }

    @Override
    public String getName() {
        return "Bench 闲置成本过高";
    }

    @Override
    public String getCategory() {
        return "BENCH";
    }

    @Override
    public AlertEventDTO evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object raw = snapshot.get("benchIdleCost");
        BigDecimal cost = toDecimal(raw);
        AlertSeverity severity = null;
        if (cost.compareTo(redThreshold) >= 0) {
            severity = AlertSeverity.RED;
        } else if (cost.compareTo(yellowThreshold) >= 0) {
            severity = AlertSeverity.YELLOW;
        }
        if (severity == null) return null;
        return AlertEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .ruleCode(getCode())
                .ruleName(getName())
                .category(getCategory())
                .severity(severity)
                .title("Bench 闲置成本 " + cost + " 元")
                .description("累计 Bench 闲置成本已达到 " + cost + " 元，资源池利用率不足。建议加速调度。")
                .currentValue(cost.toPlainString())
                .threshold("YELLOW>=" + yellowThreshold + ", RED>=" + redThreshold)
                .scope("RESOURCE_POOL")
                .triggeredAt(LocalDateTime.now())
                .drilldownAvailable(true)
                .build();
    }

    private BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return new BigDecimal(o.toString());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
