package com.njydsz.common.web.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import com.njydsz.common.base.config.BaseTraceProperties;
import com.njydsz.common.web.filter.TraceIdResponseFilter;

/**
 * Web 端请求追踪/日志配置属性
 *
 * <p>继承 {@link BaseTraceProperties}，配置前缀：{@code ydsz.web.trace}
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>{@code
 * ydsz:
 *   web:
 *     trace:
 *       enabled: true
 *       request-log-enabled: true
 *       log-level: INFO
 *       sampling-rate: 1.0
 * }</pre>
 *
 * @author ydsz-team
 * @see BaseTraceProperties
 * @see TraceIdResponseFilter
 * @since 1.0.0
 */
@Data
@Validated
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "ydsz.web.trace")
public class WebTraceProperties extends BaseTraceProperties {
}
