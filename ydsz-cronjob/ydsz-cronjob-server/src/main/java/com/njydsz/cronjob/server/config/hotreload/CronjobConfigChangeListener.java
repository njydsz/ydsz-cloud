package com.njydsz.cronjob.server.config.hotreload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 定时任务模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge）。
 *
 * <p>监听 cronjob 模块相关的配置中心变更（{@code ydsz.cronjob.*}），将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整 cronjob 模块行为参数（如调度器线程池大小、Leader 选举间隔、
 * HTTP 任务超时等），无需重启服务即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如日志记录、告警通知等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.cronjob.scheduler-pool-size}：调度器线程池大小变更 → 日志提示需重启生效
 *   <li>{@code ydsz.cronjob.leader.*}：Leader 选举配置变更 → 日志感知
 *   <li>{@code ydsz.cronjob.executor.*}：执行器配置变更 → 日志感知
 *   <li>{@code ydsz.cronjob.http.*}：HTTP 任务配置变更 → 日志感知
 *   <li>{@code ydsz.cronjob.alert.*}：告警通道配置变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>Cronjob 核心运行时资源（线程池、调度器）在启动时初始化后不支持热替换，配置变更后需重启生效。
 * 本监听器主要提供变更审计和运维感知能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class CronjobConfigChangeListener implements ConfigChangeListener {

  /** cronjob 模块配置属性前缀 */
  private static final String CRONJOB_CONFIG_PREFIX = "ydsz.cronjob.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code ydsz.cronjob.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.cronjob.scheduler-pool-size）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(CRONJOB_CONFIG_PREFIX)) {
      return;
    }

    log.info("[Cronjob] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 线程池大小变更 → 提示需重启
    if ("ydsz.cronjob.scheduler-pool-size".equals(key)) {
      log.warn("[Cronjob] 调度器线程池大小变更: {} -> {}，需重启生效", oldValue, newValue);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>cronjob 配置监听器优先级为 20（普通优先级）。
   *
   * @return 20
   */
  @Override
  public int getOrder() {
    return 20;
  }
}
