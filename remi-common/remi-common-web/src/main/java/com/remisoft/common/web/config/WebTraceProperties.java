package com.remisoft.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.remisoft.common.base.config.BaseTraceProperties;
import com.remisoft.common.web.filter.TraceIdResponseFilter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Web 端请求追踪/日志配置属性
 *
 * <p>继承 {@link BaseTraceProperties}，配置前缀：{@code remi.web.trace}
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>{@code
 * remi:
 *   web:
 *     trace:
 *       enabled: true
 *       request-log-enabled: true
 *       log-level: INFO
 *       sampling-rate: 1.0
 * }</pre>
 *
 * @author remi-team
 * @see BaseTraceProperties
 * @see TraceIdResponseFilter
 * @since 1.0.0
 */
@Data
@Validated
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "remi.web.trace")
public class WebTraceProperties extends BaseTraceProperties {
}
