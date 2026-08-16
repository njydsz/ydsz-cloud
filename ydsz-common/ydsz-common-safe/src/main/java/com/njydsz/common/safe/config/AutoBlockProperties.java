package com.njydsz.common.safe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全事件自动响应配置属性
 *
 * <p>配置前缀 {@code ydsz.safe.auto-block}，用于控制安全事件自动封禁行为。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     auto-block:
 *       enabled: true
 *       threshold: 10
 *       window-seconds: 60
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.auto-block")
public class AutoBlockProperties {

    /**
     * 是否启用自动封禁
     */
    private boolean enabled = true;

    /**
     * 触发自动封禁的事件数量阈值（同一 IP 在窗口内触发此数量次安全事件则自动封禁）
     */
    private int threshold = 10;

    /**
     * 滑动窗口大小（秒）
     */
    private long windowSeconds = 60;
}
