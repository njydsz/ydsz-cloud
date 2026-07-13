package com.njydsz.pmis.common.sentry.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 指标定义
 *
 * <p>描述一个指标的元信息，用于自动注册到 Micrometer 和生成 Prometheus 告警规则。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
@Builder
public class MetricDefinition {

    /** 指标名称（如 ydsz.search.requests） */
    private String name;

    /** 指标类型 */
    private MetricType type;

    /** 指标描述 */
    private String description;

    /** 指标单位（如 ms / bytes / count） */
    private String unit;

    /** 标签 */
    @Builder.Default
    private Map<String, String> tags = new LinkedHashMap<>();

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 获取不可变标签
     */
    public Map<String, String> getTags() {
        return tags != null ? Collections.unmodifiableMap(tags) : Collections.emptyMap();
    }
}
