package com.njydsz.pmis.execution.engine.alert;

import com.njydsz.pmis.execution.dto.AlertEventDTO;
import com.njydsz.pmis.execution.enums.AlertSeverity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

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

    private final int threshold;

    public EvmRedRule() {
        this(DEFAULT_THRESHOLD);
    }

    public EvmRedRule(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public String getCode() {
        return "EVM_RED_EXCESS";
    }

    @Override
    public String getName() {
        return "EVM 红色告警项目过多";
    }

    @Override
    public String getCategory() {
        return "EVM";
    }

    @Override
    public AlertEventDTO evaluate(Map<String, Object> snapshot) {
        if (snapshot == null) return null;
        Object raw = snapshot.get("evmRedCount");
        int red = toInt(raw);
        if (red < threshold) return null;
        return AlertEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
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
