package com.njydsz.pmis.project.engine.alert;

import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.project.dto.AlertEventDTO;
import com.njydsz.pmis.project.enums.AlertSeverity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * EVM 红色告警规则
 *
 * <p>当 EVM 红色项目数超过阈值时触发。缺省阈值 = 3。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class EvmRedRule implements AlertRule {

    /** 缺省红色项目数阈值 */
    public static final int DEFAULT_THRESHOLD = 3;

    /** 红色项目数阈值 */
    private final int threshold;

    /** 默认构造（使用缺省阈值） */
    public EvmRedRule() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * 自定义阈值构造
     *
     * @param threshold 红色项目数阈值
     */
    public EvmRedRule(int threshold) {
        this.threshold = threshold;
    }

    /**
     * @return 规则编码
     */
    @Override
    public String getCode() {
        return "EVM_RED_EXCESS";
    }

    /**
     * @return 规则中文名
     */
    @Override
    public String getName() {
        return "EVM 红色告警项目过多";
    }

    /**
     * @return 规则类别
     */
    @Override
    public String getCategory() {
        return "EVM";
    }

    /**
     * 评估 EVM 红色告警项目数是否超过阈值
     *
     * @param snapshot KPI 快照
     * @return 预警事件；未触发返回 null
     */
    @Override
    public AlertEventDTO evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object raw = snapshot.get("evmRedCount");
        int red = toInt(raw);
        if (red < threshold) return null;
        return AlertEventDTO.builder()
                .eventId(SnowflakeIdGenerator.nextIdStr())
                .ruleCode(getCode())
                .ruleName(getName())
                .category(getCategory())
                .severity(AlertSeverity.RED)
                .title("EVM 红色告警项目 " + red + " 个")
                .description("当前周期红色告警项目数已达到 " + red + " 个，超过阈值 " + threshold + "。请关注挣值偏差并复盘。")
                .currentValue(String.valueOf(red))
                .threshold(String.valueOf(threshold))
                .scope("ALL")
                .triggeredAt(LocalDateTime.now())
                .drilldownAvailable(true)
                .build();
    }

    /**
     * 将对象转换为 int
     *
     * @param o 原始对象
     * @return 转换后的 int；无法转换返回 0
     */
    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
