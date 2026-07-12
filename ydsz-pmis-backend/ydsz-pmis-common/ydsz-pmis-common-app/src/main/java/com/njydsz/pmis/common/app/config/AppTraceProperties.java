package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 端请求追踪 / 日志配置属性
 *
 * <p>继承 {@link BaseTraceProperties}，复用通用追踪配置字段（RequestId、MDC、Header 等），
 * 通过独立前缀 {@code remi.app.trace} 与 Web 端隔离。
 *
 * <p><b>线程安全性：</b>由 Spring Boot 配置属性绑定机制管理，绑定完成后通常视为只读。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see BaseTraceProperties
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "remi.app.trace")
public class AppTraceProperties extends BaseTraceProperties {
}
