package com.njydsz.common.safe.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 安全告警配置属性
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     alert:
 *       enabled: true
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.alert")
public class SafeAlertProperties {

    /**
     * 是否启用安全事件告警
     */
    private boolean enabled = true;
}
