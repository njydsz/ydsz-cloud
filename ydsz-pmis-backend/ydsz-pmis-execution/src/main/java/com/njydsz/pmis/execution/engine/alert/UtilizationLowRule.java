package com.njydsz.pmis.execution.engine.alert;

import com.njydsz.pmis.execution.dto.AlertEventDTO;
import com.njydsz.pmis.execution.enums.AlertSeverity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 可计费利用率过低规则
 *
 * <p>当平均可计费利用率低于 0.50 触发红色，低于 0.70 触发黄色。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class UtilizationLowRule implements AlertRule {

    /** 黄色阈值 = 0.70 */
    public static final BigDecimal DEFAULT_YELLOW = new BigDecimal("0.70");
    /** 红色阈值 = 0.50 */
    public static final BigDecimal DEFAULT_RED = new BigDecimal("0.50");

    private final BigDecimal yellowThreshold;
    private final BigDecimal redThreshold;

    public UtilizationLowRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    public UtilizationLowRule(BigDecimal yellowThreshold, BigDecimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    @Override
    public String getCode() {
        return "UTILIZATION_LOW";
    }

    @Override
    public String getName() {
        return "可计费利用率偏低";
    }

    @Override
    public String getCategory() {
        return "UTILIZATION";
    }

    @Override
    public AlertEventDTO evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object raw = snapshot.get("avgBillableUtilization");
        BigDecimal util = toDecimal(raw);
        // 无项目时不评估（"无数据"状态，不应误触发）
        Object apRaw = snapshot.get("activeProjects");
        Integer activeProjects = null;
        if (apRaw instanceof Number) activeProjects = ((Number) apRaw).intValue();
        else if (apRaw != null) {
            try { activeProjects = Integer.parseInt(String.valueOf(apRaw)); } catch (Exception ignore) {}
        }
        if (activeProjects == null || activeProjects <= 0) return null;
        AlertSeverity severity = null;
        if (util.compareTo(redThreshold) < 0) {
            severity = AlertSeverity.RED;
        } else if (util.compareTo(yellowThreshold) < 0) {
            severity = AlertSeverity.YELLOW;
        }
        if (severity == null) return null;
        BigDecimal pct = util.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP);
        return AlertEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .ruleCode(getCode())
                .ruleName(getName())
                .category(getCategory())
                .severity(severity)
                .title("可计费利用率仅 " + pct + "%")
                .description("团队平均可计费利用率为 " + pct + "%，低于阈值。请关注资源调度。")
                .currentValue(util.toPlainString())
                .threshold("YELLOW<" + yellowThreshold + ", RED<" + redThreshold)
                .scope("ALL")
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
