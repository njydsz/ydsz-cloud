package com.njydsz.pmis.project.engine.alert;

import com.njydsz.pmis.project.dto.AlertEventDTO;
import com.njydsz.pmis.project.enums.AlertSeverity;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 黄色阈值 */
    private final BigDecimal yellowThreshold;
    /** 红色阈值 */
    private final BigDecimal redThreshold;

    /** 默认构造（使用缺省阈值） */
    public UtilizationLowRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    /**
     * 自定义阈值构造
     *
     * @param yellowThreshold 黄色阈值
     * @param redThreshold    红色阈值
     */
    public UtilizationLowRule(BigDecimal yellowThreshold, BigDecimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    public String getCode() {
        return "UTILIZATION_LOW";
    }

    /**
     * @return 规则中文名
     */
    @Override
    public String getName() {
        return "可计费利用率偏低";
    }

    /**
     * @return 规则类别
     */
    @Override
    public String getCategory() {
        return "UTILIZATION";
    }

    /**
     * 评估可计费利用率是否低于阈值
     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
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
        BigDecimal pct = util.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
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

    /**
     * 将对象转换为 BigDecimal
     *
     * @param o 原始对象
     * @return 转换后的 BigDecimal；无法转换返回 ZERO
     */
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
