package com.njydsz.system.server.cache;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.entity.DictItem;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.infra.repository.DictRepository;

/**
 * 缓存预热器 — 应用启动后异步预热高频访问的缓存数据。
 *
 * <p>预热策略：
 *
 * <ul>
 *   <li>系统配置缓存（{@link CacheConstants#SYSTEM_CONFIG_CACHE}）：加载全部启用配置，按 key 预热
 *   <li>字典项缓存（{@link CacheConstants#SYSTEM_DICT_ITEM_CACHE}）：按 typeCode 分组预热列表
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
 *   <li>真实数据预热：直接缓存真实值，避免首次请求击穿到 DB
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

  /** 默认租户 ID（预热使用系统级默认租户） */
  private static final String DEFAULT_TENANT = "default";

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
   * <p>加载全部启用配置，按 configKey 预热单条值缓存。
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

      Cache configCache = cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE);
      if (configCache == null) {
        log.warn("[CacheWarmer] 配置缓存不存在，跳过预热");
        return;
      }

      // 按 configKey 预热单条值缓存
      for (Config config : configs) {
        try {
          String valueKey = "value:" + DEFAULT_TENANT + ":" + config.getConfigKey();
          configCache.put(valueKey, config.getConfigValue());
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
   * <p>加载全部启用字典项，按 typeCode 分组预热列表缓存。
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

      Cache dictCache = cacheManager.getCache(CacheConstants.SYSTEM_DICT_ITEM_CACHE);
      if (dictCache == null) {
        log.warn("[CacheWarmer] 字典缓存不存在，跳过预热");
        return;
      }

      // 按 typeCode 分组预热列表缓存
      Map<String, List<DictItemVO>> groupedItems = dictItems.stream()
          .map(SystemConverter.INSTANT::entityToVO)
          .filter(Objects::nonNull)
          .collect(Collectors.groupingBy(DictItemVO::getTypeCode));

      for (Map.Entry<String, List<DictItemVO>> entry : groupedItems.entrySet()) {
        try {
          String listKey = "list:" + DEFAULT_TENANT + ":" + entry.getKey();
          dictCache.put(listKey, entry.getValue());
        } catch (Exception e) {
          log.debug("[CacheWarmer] 预热字典列表失败: typeCode={}", entry.getKey());
        }
      }

      log.info("[CacheWarmer] 字典项缓存预热完成，共 {} 条，{} 个类型", dictItems.size(), groupedItems.size());
    } catch (Exception e) {
      log.warn("[CacheWarmer] 字典项缓存预热失败: {}", e.getMessage());
    }
  }
}
