package com.njydsz.system.server.config.hotreload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 系统模块配置变更监听器（P0-1：接入统一 ConfigChangeBridge）
 *
 * <p>监听系统模块相关的配置中心变更（{@code ydsz.system.*}），将 Spring Cloud 配置变更事件桥接到本地缓存清理和运行时状态更新。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整系统模块行为参数（如缓存 TTL、BCrypt 强度、跨实例缓存失效开关等），无需重启服务即可生效。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如缓存清理、资源重建等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.system.config.cache-ttl-minutes}：配置缓存 TTL 变更 → 清理 {@link
 *       CacheConstants#SYSTEM_CONFIG_CACHE}
 *   <li>{@code ydsz.system.dict.cache-ttl-minutes}：字典缓存 TTL 变更 → 清理 {@link
 *       CacheConstants#SYSTEM_DICT_ITEM_CACHE}
 *   <li>{@code ydsz.system.variable.cache-ttl-minutes}：变量缓存 TTL 变更 → 清理 {@link
 *       CacheConstants#SYSTEM_VARIABLE_CACHE}
 *   <li>{@code ydsz.system.cache.cross-instance-enabled}：跨实例缓存失效开关变更 → 日志提示
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigChangeListener implements ConfigChangeListener {

  /** 系统模块配置属性前缀 */
  private static final String SYSTEM_CONFIG_PREFIX = "ydsz.system.";

  /** 缓存配置后缀（用于判断是否为缓存 TTL 类配置） */
  private static final String CACHE_TTL_SUFFIX = "cache-ttl-minutes";

  /** 跨实例缓存失效开关配置键 */
  private static final String CROSS_INSTANCE_CONFIG_KEY = "ydsz.system.cache.cross-instance-enabled";

  private final CacheManager cacheManager;

  /**
   * 接收配置变更回调
   *
   * <p>仅处理 {@code ydsz.system.} 前缀的配置项，其他配置变更忽略。对于缓存 TTL 类配置，变更后主动清理本地缓存使其按新 TTL 重建。
   *
   * @param key 变更的配置键（如 ydsz.system.dict.cache-ttl-minutes）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(SYSTEM_CONFIG_PREFIX)) {
      return;
    }

    log.info("[System] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 缓存 TTL 变更 → 清理对应本地缓存
    if (key.endsWith(CACHE_TTL_SUFFIX)) {
      handleCacheTtlChange(key);
      return;
    }

    // 跨实例缓存失效开关变更 → 日志提示
    if (CROSS_INSTANCE_CONFIG_KEY.equals(key)) {
      log.info("[System] 跨实例缓存失效开关变更: {} -> {}，请确认 Redis Pub/Sub 频道已正确配置", oldValue, newValue);
    }
  }

  /**
   * 处理缓存 TTL 配置变更
   *
   * <p>根据配置键中的缓存区域标识，清理对应 Spring Cache 使其下次读取时按新 TTL 重建。
   *
   * @param key 配置键
   */
  private void handleCacheTtlChange(String key) {
    if (key.contains("config.")) {
      evictCacheByName(CacheConstants.SYSTEM_CONFIG_CACHE, "配置");
    } else if (key.contains("dict.")) {
      evictCacheByName(CacheConstants.SYSTEM_DICT_ITEM_CACHE, "字典");
    } else if (key.contains("variable.")) {
      evictCacheByName(CacheConstants.SYSTEM_VARIABLE_CACHE, "变量");
    }
  }

  /**
   * 根据缓存名称清理缓存
   *
   * @param cacheName Spring Cache 名称
   * @param cacheLabel 缓存中文标签（用于日志）
   */
  private void evictCacheByName(String cacheName, String cacheLabel) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.clear();
      log.info("[System] {} 缓存 TTL 变更，已清理 {} 缓存", cacheLabel, cacheName);
    } else {
      log.warn("[System] {} 缓存 {} 不存在，跳过清理", cacheLabel, cacheName);
    }
  }
}
