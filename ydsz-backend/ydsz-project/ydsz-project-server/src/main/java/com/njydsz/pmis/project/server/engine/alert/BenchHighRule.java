package com.njydsz.project.server.engine.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.njydsz.common.util.id.SnowflakeUtils;
import com.njydsz.project.domain.dto.AlertEventDTO;
import com.njydsz.project.domain.enums.AlertSeverity;

/**
 * Bench 闲置成本过高规则
 *
 * <p>当累计 Bench 闲置成本超过阈值时触发。缺省阈值 50 万元。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BenchHighRule implements AlertRule {

    /** 缺省红色阈值 = 1,000,000 元 */
    public static final BigDecimal DEFAULT_RED = new BigDecimal("1000000");
    /** 缺省黄色阈值 = 500,000 元 */
    public static final BigDecimal DEFAULT_YELLOW = new BigDecimal("500000");

    /** 黄色阈值 */
    private final BigDecimal yellowThreshold;
    /** 红色阈值 */
    private final BigDecimal redThreshold;

    /** 默认构造（使用缺省阈值） */
    public BenchHighRule() {
        this(DEFAULT_YELLOW, DEFAULT_RED);
    }

    /**
     * 自定义阈值构造
     *
     * @param yellowThreshold 黄色阈值
     * @param redThreshold    红色阈值
     */
    public BenchHighRule(BigDecimal yellowThreshold, BigDecimal redThreshold) {
        this.yellowThreshold = yellowThreshold;
        this.redThreshold = redThreshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    public String getCode() {
        return "BENCH_IDLE_COST_HIGH";
    }

    /**
     * @return 规则中文名
     */
    @Override
    public String getName() {
        return "Bench 闲置成本过高";
    }

    /**
     * @return 规则类别
     */
    @Override
    public String getCategory() {
        return "BENCH";
    }

    /**
     * 评估 Bench 闲置成本是否超过阈值
     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
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
                .eventId(SnowflakeUtils.nextIdStr())
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
