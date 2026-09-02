package com.njydsz.system.server.service.rollback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.cache.CacheKeyBuilder;



/**
 * 配置回滚策略 — 从快照 JSON 反序列化并重建配置资源。
 *
 * <p>负责：
 *
 * <ol>
 *   <li>反序列化快照 JSON 为 ConfigVO
 *   <li>更新或创建配置记录
 *   <li>精准失效相关缓存
 *   <li>发布配置变更事件
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigRollbackStrategy implements RollbackStrategy {

  private final ConfigRepository configRepository;
  private final CacheManager cacheManager;
  private final CacheKeyBuilder cacheKeyBuilder;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  @Override
  public void rebuild(String snapshotJson) {
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return;
    }

    String snapshotGroup = null;
    String snapshotKey;
    try {
      ConfigVO snapshotVO = YdszJson.fromJson(snapshotJson, ConfigVO.class);
      snapshotGroup = snapshotVO.getConfigGroup();
      snapshotKey = snapshotVO.getConfigKey();

      ConfigVO currentConfig = configRepository.findByKeyIgnoreStatus(snapshotKey).orElse(null);
      if (currentConfig != null) {
        // 更新现有配置
        currentConfig.setConfigValue(snapshotVO.getConfigValue());
        currentConfig.setValueType(snapshotVO.getValueType());
        currentConfig.setDefaultValue(snapshotVO.getDefaultValue());
        currentConfig.setDescription(snapshotVO.getDescription());
        currentConfig.setIsPublic(snapshotVO.getIsPublic());
        currentConfig.setSortOrder(snapshotVO.getSortOrder());
        currentConfig.setStatus(snapshotVO.getStatus());
        configRepository.updateById(toDto(currentConfig));
      } else {
        // 原配置已被删除，重新创建
        ConfigDTO newConfig = new ConfigDTO();
        newConfig.setConfigGroup(snapshotVO.getConfigGroup());
        newConfig.setConfigKey(snapshotVO.getConfigKey());
        newConfig.setConfigValue(snapshotVO.getConfigValue());
        newConfig.setValueType(snapshotVO.getValueType());
        newConfig.setDefaultValue(snapshotVO.getDefaultValue());
        newConfig.setDescription(snapshotVO.getDescription());
        newConfig.setIsPublic(snapshotVO.getIsPublic());
        newConfig.setSortOrder(snapshotVO.getSortOrder());
        newConfig.setStatus(snapshotVO.getStatus());
        configRepository.insert(newConfig);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
          .data("reason", e.getMessage());
    }

    // 精准失效缓存
    evictConfigCaches(snapshotKey, snapshotGroup);

    // 发布变更事件
    publishConfigChangedEvent(snapshotKey, snapshotGroup);
  }

  /**
   * 精准失效配置缓存（单 key + 分组 + 公开配置）。
   *
   * @param configKey 配置键
   * @param configGroup 配置分组
   */
  private void evictConfigCaches(String configKey, String configGroup) {
    Cache cache = cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE);
    if (cache == null) {
      return;
    }
    if (configKey != null) {
      cache.evict(cacheKeyBuilder.configValue(configKey));
    }
    if (configGroup != null) {
      cache.evict(cacheKeyBuilder.configGroup(configGroup));
    }
    cache.evict(cacheKeyBuilder.configPublic());
  }

  /**
   * 发布配置变更事件。
   *
   * @param configKey 配置键
   * @param configGroup 配置分组
   */
  private void publishConfigChangedEvent(String configKey, String configGroup) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      return;
    }
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("Config")
            .aggregateId(configKey)
            .eventType(DomainEventTypes.CONFIG_CHANGED)
            .metadata("configKey", configKey)
            .metadata("configGroup", configGroup)
            .build());
  }

  /**
   * 将 ConfigVO 转换为 ConfigDTO（避免 server 层依赖 infra 的 SystemConverter）。
   *
   * @param vo 配置 VO
   * @return 配置 DTO
   */
  private ConfigDTO toDto(ConfigVO vo) {
    ConfigDTO dto = new ConfigDTO();
    dto.setId(vo.getId());
    dto.setConfigGroup(vo.getConfigGroup());
    dto.setConfigKey(vo.getConfigKey());
    dto.setConfigValue(vo.getConfigValue());
    dto.setValueType(vo.getValueType());
    dto.setDefaultValue(vo.getDefaultValue());
    dto.setDescription(vo.getDescription());
    dto.setIsPublic(vo.getIsPublic());
    dto.setSortOrder(vo.getSortOrder());
    dto.setStatus(vo.getStatus());
    return dto;
  }
}
