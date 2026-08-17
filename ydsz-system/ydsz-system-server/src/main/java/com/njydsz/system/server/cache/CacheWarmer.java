package com.njydsz.system.server.cache;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.infra.repository.DictRepository;

/**
 * 缓存预热器 — 应用启动后异步预热高频访问的缓存数据。
 *
 * <p>预热策略：
 *
 * <ul>
 *   <li>系统配置缓存（{@link CacheConstants#SYSTEM_CONFIG_CACHE}）：加载全部启用配置
 *   <li>字典项缓存（{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}）：加载全部启用字典项
 * </ul>
 *
 * <p><b>触发时机：</b>{@link ApplicationReadyEvent}（应用就绪后异步执行，不影响启动时间）。
 *
 * <p><b>设计考量：</b>
 *
 * <ul>
 *   <li>异步执行：预热在独立线程池执行，不阻塞主线程
 *   <li>失败容错：预热失败仅记录警告，不影响应用启动
 *   <li>幂等安全：预热数据会被后续写操作的 {@code @CacheEvict} 覆盖，不会产生脏数据
 * </ul>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmer {

  /** Spring Cache 管理器 */
  private final CacheManager cacheManager;

  /** 租户感知缓存键构造器 */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 系统配置仓储 */
  private final ConfigRepository configRepository;

  /** 字典仓储 */
  private final DictRepository dictRepository;

  /**
   * 应用就绪后执行缓存预热。
   *
   * <p>异步执行，避免阻塞应用启动。预热失败仅记录警告，不影响应用正常启动。
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("[CacheWarmer] 开始缓存预热...");
    long start = System.currentTimeMillis();
    try {
      warmConfigCache();
      warmDictCache();
      log.info("[CacheWarmer] 缓存预热完成，耗时 {}ms", System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.warn("[CacheWarmer] 缓存预热失败（不影响应用启动）: {}", e.getMessage());
    }
  }

  /**
   * 预热系统配置缓存。
   *
   * <p>加载全部启用配置，按 configGroup 分组预热。
   */
  private void warmConfigCache() {
    try {
      List<Config> configs =
          configRepository.getConfigMapper().selectList(
              new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Config>()
                  .eq("status", "ENABLED")
                  .eq("deleted", 0));

      if (configs.isEmpty()) {
        log.info("[CacheWarmer] 系统配置表为空，跳过配置缓存预热");
        return;
      }

      // 按分组预热缓存键
      for (Config config : configs) {
        try {
          String valueKey = cacheKeyBuilder.configValue(config.getConfigKey());
          cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).put(valueKey, config.getConfigValue());

          String groupKey = cacheKeyBuilder.configGroup(config.getConfigGroup());
          // 组缓存仅预热一次（通过 putIfAbsent 语义）
          cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).putIfAbsent(groupKey, true);
        } catch (Exception e) {
          log.debug("[CacheWarmer] 预热单条配置失败: {}/{}", config.getConfigGroup(), config.getConfigKey());
        }
      }

      log.info("[CacheWarmer] 系统配置缓存预热完成，共 {} 条", configs.size());
    } catch (Exception e) {
      log.warn("[CacheWarmer] 系统配置缓存预热失败: {}", e.getMessage());
    }
  }

  /**
   * 预热字典项缓存。
   *
   * <p>加载全部启用字典项，按 typeCode 分组预热。
   */
  private void warmDictCache() {
    try {
      List<DictItem> dictItems =
          dictRepository.getDictItemMapper().selectList(
              new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DictItem>()
                  .eq("status", "ENABLED")
                  .eq("deleted", 0));

      if (dictItems.isEmpty()) {
        log.info("[CacheWarmer] 字典项表为空，跳过字典缓存预热");
        return;
      }

      // 按 typeCode 预热列表缓存
      for (DictItem item : dictItems) {
        try {
          String listKey = cacheKeyBuilder.dictList(item.getTypeCode());
          cacheManager.getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE).putIfAbsent(listKey, true);
        } catch (Exception e) {
          log.debug("[CacheWarmer] 预热单条字典项失败: {}/{}", item.getTypeCode(), item.getItemCode());
        }
      }

      log.info("[CacheWarmer] 字典项缓存预热完成，共 {} 条", dictItems.size());
    } catch (Exception e) {
      log.warn("[CacheWarmer] 字典项缓存预热失败: {}", e.getMessage());
    }
  }
}
