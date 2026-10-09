package com.njydsz.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 网关模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge，100% 业务模块覆盖率收官）。
 *
 * <p>监听网关模块相关的配置中心变更（{@code ydsz.gateway.ratelimit.*} / {@code ydsz.gateway.rate-limiter.*} /
 * {@code ydsz.gateway.cache.*} / {@code ydsz.gateway.cors.*} / {@code ydsz.gateway.ip-access.*}），
 * 将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整网关限流阈值、缓存 TTL、CORS 策略、IP 访问控制规则等，
 * 无需重启网关即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。网关限流组件（{@code RateLimitFilter} / {@code GatewayRateLimiterConfig}）
 * 基于 Resilience4j 或自实现令牌桶，核心运行时参数在初始化后不可动态替换，配置变更后需重启生效。
 * 本监听器主要提供变更审计和运维感知能力。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.gateway.ratelimit.*}：限流阈值配置变更 → 日志感知（QPS / 突发容量 / 白名单）
 *   <li>{@code ydsz.gateway.rate-limiter.*}：Resilience4j 限流兜底配置变更 → 日志感知
 *   <li>{@code ydsz.gateway.cache.*}：缓存 TTL / 最大容量变更 → 日志感知（{@code GatewayCacheConfig} 字段已重绑）
 *   <li>{@code ydsz.gateway.cors.*}：CORS 策略变更 → 日志感知
 *   <li>{@code ydsz.gateway.ip-access.*}：IP 黑白名单配置变更 → 日志感知
 *   <li>{@code ydsz.gateway.api-key.*}：API Key 校验配置变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>网关作为系统入口，限流阈值变更直接影响流量管控效果，建议通过灰度发布方式逐步调整。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class GatewayConfigChangeListener implements ConfigChangeListener {

  /** 网关限流配置属性前缀 */
  private static final String GATEWAY_PREFIX = "ydsz.gateway.";

  /** 网关限流属性前缀 */
  private static final String RATELIMIT_PREFIX = "ydsz.gateway.ratelimit.";

  /** 网关缓存属性前缀 */
  private static final String CACHE_PREFIX = "ydsz.gateway.cache.";

  /** 网关 CORS 属性前缀 */
  private static final String CORS_PREFIX = "ydsz.gateway.cors.";

  /** 网关 IP 访问控制属性前缀 */
  private static final String IP_ACCESS_PREFIX = "ydsz.gateway.ip-access.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code ydsz.gateway.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.gateway.ratelimit.per-user.default-qps）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(GATEWAY_PREFIX)) {
      return;
    }

    // 根据配置类别使用不同的日志级别
    if (key.startsWith(RATELIMIT_PREFIX)) {
      log.warn("[Gateway] 限流配置变更: {} = {} → {}（需重启生效）", key, oldValue, newValue);
    } else if (key.startsWith(CACHE_PREFIX)) {
      log.info("[Gateway] 缓存配置变更: {} = {} → {}", key, oldValue, newValue);
    } else if (key.startsWith(CORS_PREFIX) || key.startsWith(IP_ACCESS_PREFIX)) {
      log.warn("[Gateway] 安全配置变更: {} = {} → {}", key, oldValue, newValue);
    } else {
      log.info("[Gateway] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>网关配置监听器优先级为 10（较高优先级，网关作为入口组件，配置变更需优先感知）。
   *
   * @return 10
   */
  @Override
  public int getOrder() {
    return 10;
  }
}
