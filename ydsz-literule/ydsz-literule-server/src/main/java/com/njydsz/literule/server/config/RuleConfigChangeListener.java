package com.njydsz.literule.server.config;

import com.njydsz.common.config.hotreload.ConfigChangeListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LiteRule 配置变更监听器（P1-1：接入统一 ConfigChangeBridge）
 *
 * <p>监听规则引擎相关的配置中心变更（{@code ydsz.literule.*}），将 Spring Cloud 配置变更事件桥接到 {@link RuleHotReloader}
 * 执行规则热刷新。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整规则引擎行为参数 （如热加载开关、dry-run 模式、缓存 TTL 等），无需重启服务即可生效。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口， 由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件， 替代原有的自建配置变更监听机制（保留 {@link RuleHotReloader} 中的业务逻辑）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleConfigChangeListener implements ConfigChangeListener {

  /** LiteRule 配置属性前缀 */
  private static final String LITERULE_CONFIG_PREFIX = "ydsz.literule.";

  private final RuleHotReloader ruleHotReloader;

  /**
   * 接收配置变更回调
   *
   * <p>仅处理 {@code ydsz.literule.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.literule.hot-reload-enabled）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(LITERULE_CONFIG_PREFIX)) {
      return;
    }

    log.info("[LiteRule] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 核心配置变更时触发全量刷新
    if (key.contains("hot-reload") || key.contains("cache") || key.contains("dry-run")) {
      log.info("[LiteRule] 核心配置 {} 已变更，触发规则全量刷新", key);
      ruleHotReloader.fullReload("CONFIG_CHANGE");
    }
  }
}
