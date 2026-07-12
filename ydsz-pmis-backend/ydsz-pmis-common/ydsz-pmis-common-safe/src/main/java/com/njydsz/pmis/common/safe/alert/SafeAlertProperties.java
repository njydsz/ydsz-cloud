package com.njydsz.pmis.common.safe.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全告警配置属性
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   safe:
 *     alert:
 *       enabled: true
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "remi.safe.alert")
public class SafeAlertProperties {

    /**
     * 是否启用安全事件告警
     */
    private boolean enabled = true;
}
