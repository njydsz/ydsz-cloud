package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 端请求追踪/日志配置属性
 *
 * <p>继承 {@link BaseTraceProperties}，复用通用追踪配置字段，
 * 通过独立前缀 {@code pmis.app.trace} 与 Web 端隔离。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ConfigurationProperties(prefix = "pmis.app.trace")
public class AppTraceProperties extends BaseTraceProperties {
}
