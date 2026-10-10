package com.njydsz.message.server.config.hotreload;


import com.njydsz.common.locales.util.I18n;import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 消息模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge）。
 *
 * <p>监听 message 模块相关的配置中心变更（{@code ydsz.message.*}），将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整 message 模块行为参数（如限流阈值、熔断参数、
 * 重试策略等），无需重启服务即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如日志记录、运行时感知等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.message.rateLimit.*}：限流配置变更 → 日志感知（限流令牌桶由 @ConfigurationProperties 自动重绑）
 *   <li>{@code ydsz.message.circuitBreaker.*}：熔断器配置变更 → 日志感知
 *   <li>{@code ydsz.message.dedup.*}：去重配置变更 → 日志感知
 *   <li>{@code ydsz.message.smartTiming.*}：智能定时配置变更 → 日志感知
 *   <li>{@code ydsz.message.channelEnabled.*}：通道开关变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>大部分运行时组件（限流器、熔断器）基于 @ConfigurationProperties 自动重绑，Java 字段值会自动更新。
 * 但某些组件初始化后读取的配置（如 Resilience4j 熔断器实例）可能需要重建才能应用新值。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class MessageConfigChangeListener implements ConfigChangeListener {

  /** message 模块配置属性前缀 */
  private static final String MESSAGE_CONFIG_PREFIX = "ydsz.message.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code ydsz.message.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.message.rateLimit.receiverPermits）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(MESSAGE_CONFIG_PREFIX)) {
      return;
    }

    log.info(I18n.message("message.log.other.Message_key_{}_{}_{}.cb99ff"), key, oldValue, newValue);

    // 通道开关变更 → 重要性级别较高，使用 WARN
    if (key.startsWith("ydsz.message.channelEnabled")) {
      log.warn(I18n.message("message.log.other.Message_{}_{}.a22f31"), key, newValue);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>message 配置监听器优先级为 20（普通优先级）。
   *
   * @return 20
   */
  @Override
  public int getOrder() {
    return 20;
  }
}
