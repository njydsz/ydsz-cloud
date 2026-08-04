package com.remisoft.common.safe.ratelimit.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.remisoft.common.safe.ratelimit.model.RateLimitRule;

import lombok.Data;

/**
 * 限流模块配置属性
 *
 * <p>前缀：{@code remi.ratelimit}
 *
 * <p>配置示例：
 * <pre>{@code
 * remi:
 *   ratelimit:
 *     enabled: true
 *     default-mode: LOCAL
 *     fallback-on-error: PASS
 *     metrics-enabled: true
 *     rules:
 *       - resource: user.login
 *         threshold: 5
 *         window-millis: 1000
 *         dimension: USER
 *       - resource: order.create
 *         threshold: 100
 *         algorithm: SLIDING_WINDOW
 *         mode: CLUSTER
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.ratelimit")
public class RateLimitProperties {

    /** 是否启用限流模块 */
    private boolean enabled = true;

    /** 默认限流模式 */
    private String defaultMode = "LOCAL";

    /** 限流决策异常时降级策略：PASS（放行）/ BLOCK（拒绝） */
    private String fallbackOnError = "PASS";

    /** 是否启用 Micrometer 指标 */
    private boolean metricsEnabled = true;

    /** 集群限流 Redis Key 前缀 */
    private String clusterKeyPrefix = "remi:ratelimit:";

    /** 规则列表（静态配置） */
    private List<RateLimitRule> rules = new ArrayList<>();

    /** 热点参数特殊配置（key 索引 → 阈值） */
    private List<HotParamRule> hotParams = new ArrayList<>();
}
