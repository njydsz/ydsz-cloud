package com.njydsz.pmis.common.sentry.domain;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 告警事件
 *
 * <p>统一的告警事件模型，支持告警收敛、去重和静默。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@Builder
public class AlertEvent {

    /** 告警名称 */
    private String name;

    /** 告警级别 */
    private AlertSeverity severity;

    /** 告警摘要 */
    private String summary;

    /** 告警详情 */
    private String description;

    /** 告警分类（business/availability/resource/database/cache/mq） */
    private String category;

    /** 告警标签 */
    private Map<String, String> labels;

    /** 告警注解 */
    private Map<String, String> annotations;

    /** 触发时间 */
    @Builder.Default
    private Instant firedAt = Instant.now();

    /** 触发值 */
    private double value;

    /** Runbook URL */
    private String runbookUrl;

    /**
     * 生成去重 Key（用于告警收敛）
     */
    public String dedupKey() {
        return name + "|" + (severity != null ? severity.name() : "")
                + "|" + (labels != null ? labels.getOrDefault("job", "") : "");
    }
}
