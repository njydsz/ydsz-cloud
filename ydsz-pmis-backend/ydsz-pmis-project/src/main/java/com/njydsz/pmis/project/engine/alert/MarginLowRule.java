package com.njydsz.pmis.project.engine.alert;

import com.njydsz.pmis.project.dto.AlertEventDTO;
import com.njydsz.pmis.project.enums.AlertSeverity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 毛利率过低规则
 *
 * <p>当平均毛利率低于黄色阈值（缺省 0.10 = 10%）触发黄色预警，低于红色阈值（缺省 0.05 = 5%）触发红色预警。
 * 毛利率为负时直接红色。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class MarginLowRule implements AlertRule {

    /** 黄色阈值（0.10 = 10%） */
    public static final BigDecimal DEFAULT_YELLOW = new BigDecimal("0.10");
    /** 红色阈值（0.05 = 5%） */
    public static final BigDecimal DEFAULT_RED = new BigDecimal("0.05");

    /** 黄色阈值 */
    private final BigDecimal yellowThreshold;
    /** 红色阈值 */
    private final BigDecimal redThreshold;

    /** 默认构造（使用缺省阈值） */
    public MarginLowRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    /**
     * 自定义阈值构造
     *
     * @param yellowThreshold 黄色阈值
     * @param redThreshold    红色阈值
     */
    public MarginLowRule(BigDecimal yellowThreshold, BigDecimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    public String getCode() {
        return "MARGIN_LOW";
    }

    /**
     * @return 规则中文名
     */
    @Override
    public String getName() {
        return "毛利率过低";
    }

    /**
     * @return 规则类别
     */
    @Override
    public String getCategory() {
        return "COST";
    }

    /**
     * 评估毛利率是否低于阈值
     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
    @Override
    public AlertEventDTO evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object raw = snapshot.get("grossMargin");
        BigDecimal margin = toDecimal(raw);
        // 无收入/无项目时不评估（视为"无数据"状态，不应误触发）
        Object revRaw = snapshot.get("confirmedRevenue");
        BigDecimal revenue = toDecimal(revRaw);
        if (revenue.signum() <= 0) return null;
        AlertSeverity severity = null;
        if (margin.compareTo(redThreshold) < 0) {
            severity = AlertSeverity.RED;
        } else if (margin.compareTo(yellowThreshold) < 0) {
            severity = AlertSeverity.YELLOW;
        }
        if (severity == null) return null;
        return AlertEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .ruleCode(getCode())
                .ruleName(getName())
                .category(getCategory())
                .severity(severity)
                .title("毛利率仅 " + margin.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%")
                .description("当前累计毛利率为 " + margin + "，低于阈值。需关注毛利结构与项目组合。")
                .currentValue(margin.toPlainString())
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
