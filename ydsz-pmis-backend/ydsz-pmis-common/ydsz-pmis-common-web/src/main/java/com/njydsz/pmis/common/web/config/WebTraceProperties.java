package com.njydsz.pmis.common.web.config;

import com.njydsz.pmis.common.web.filter.TraceIdResponseFilter;
import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 *       trace-id-header-name: X-Trace-Id
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseTraceProperties
 * @see TraceIdResponseFilter
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "ydsz.web.trace")
public class WebTraceProperties extends BaseTraceProperties {

    /**
     * TraceId 响应头名称
     * <p>默认值：{@code X-Trace-Id}
     */
    private String traceIdHeaderName = "X-Trace-Id";
}
