package com.njydsz.common.safe.ratelimit.properties;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流模块配置属性
 *
 * <p>前缀：{@code ydsz.safe.ratelimit}
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     ratelimit:
 *       enabled: true
 *       default-mode: LOCAL
 *       fallback-on-error: PASS
 *       metrics-enabled: true
 *       rules:
 *         - resource: user.login
 *           threshold: 5
 *           window-millis: 1000
 *           dimension: USER
 *         - resource: order.create
 *           threshold: 100
 *           algorithm: TOKEN_BUCKET
 *           mode: CLUSTER
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.ratelimit")
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
  private String clusterKeyPrefix = "ydsz:ratelimit:";

  /** 规则列表（静态配置） */
  private List<RateLimitRule> rules = new ArrayList<>(4);
}