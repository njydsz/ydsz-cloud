package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.entity.ConfigVersion;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.ConfigVersionVO;
import com.njydsz.system.infra.mapper.ConfigVersionMapper;
import com.njydsz.system.infra.repository.ConfigRepository;
import com.njydsz.system.server.service.ConfigVersionService;

/**
 * 配置版本 Service 实现
 *
 * <p>对 {@link ConfigVersionService} 接口的完整实现，是「配置中心」版本管理子系统的核心业务逻辑层。 维护配置项（{@code
 * configKey}）的变更历史快照，支持版本回滚、变更审计、配置复盘等场景。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本查询</b>：{@link #listByResourceKey} — 按 {@code configKey} 查询某配置项的所有历史版本
 *   <li><b>版本创建</b>：{@link #createVersion} — 由 {@link ConfigServiceImpl} 在写操作成功后调用
 *   <li><b>版本回滚</b>：{@link #rollbackTo} — 通过 {@code snapshotJson} 重建配置项
 * </ul>
 *
 * <p><b>与 DictVersionServiceImpl 的区别：</b>
 *
 * <ul>
 *   <li>DictVersion 按 typeCode 分组管理（一个版本覆盖整个字典类型，回滚需删除全部 + 重建）
 *   <li>ConfigVersion 按 configKey 单键粒度（每个配置键独立版本链，回滚仅更新单条配置）
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}； 读方法不开启事务，依赖 MyBatis
 * 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigVersionService 配置版本 Service 接口
 * @see ConfigServiceImpl 配置 Service（写操作触发版本快照）
 * @see com.njydsz.system.domain.entity.ConfigVersion 配置版本实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigVersionServiceImpl implements ConfigVersionService {

  /** 配置版本 Mapper（继承 {@code ydsz_config_version} 表 CRUD） */
  private final ConfigVersionMapper configVersionMapper;

  /** 配置仓储（用于回滚时更新配置项） */
  private final ConfigRepository configRepository;

  /** Spring Cache 管理器（用于失效本地缓存） */
  private final CacheManager cacheManager;

  /**
   * 按配置键查询所有历史版本（按生效时间倒序）
   *
   * <p>典型调用方：管理后台「配置历史版本」列表页。
   *
   * @param resourceKey 配置键（{@code ydsz_config.config_key}）
   * @return 历史版本列表（最新生效时间在前），无版本时返回<b>空列表</b>（不是 null）
   */
  @Override
  public List<ConfigVersionVO> listByResourceKey(String resourceKey) {
    return configVersionMapper.listByResourceKey(resourceKey).stream()
        .map(SystemConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * 创建配置版本快照
   *
   * <p>由 {@link ConfigServiceImpl} 在配置项变更（增 / 删 / 改）后调用， 记录变更前的<b>配置项 JSON 快照</b>，用于版本回滚和变更审计。
   *
   * <p><b>关键设计：</b>
   *
   * <ul>
   *   <li><b>事务一致性</b>：调用方需在<b>配置项变更事务</b>内调用本方法， 通过 Spring 事务传播保证原子性（{@code PROPAGATION_REQUIRED}）
   *   <li><b>快照时机</b>：必须在<b>变更前</b>查询并快照原配置项，而非变更后
   *   <li><b>版本号语义</b>：{@code version} 字段由调用方决定格式（典型：{@code "v" + 时间戳}）
   * </ul>
   *
   * @param resourceKey 配置键
   * @param configGroup 配置分组
   * @param version 版本号（由调用方决定格式）
   * @param changeLog 变更说明
   * @param snapshotJson 变更前的<b>配置项 JSON 快照</b>
   * @return 新创建的版本 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createVersion(
      String resourceKey,
      String configGroup,
      String version,
      String changeLog,
      String snapshotJson) {
    ConfigVersion entity = new ConfigVersion();
    entity.setResourceKey(resourceKey);
    entity.setConfigGroup(configGroup);
    entity.setVersion(version);
    entity.setChangeLog(changeLog);
    entity.setSnapshotJson(snapshotJson);
    entity.setEffectiveDate(LocalDateTime.now());
    configVersionMapper.insert(entity);
    return entity.getId();
  }

  /**
   * 回滚配置到指定版本
   *
   * <p>事务边界内执行「查询快照 → 更新配置项 → 创建新版本 → 失效缓存」全链路。 若中间步骤失败，整个事务回滚，配置数据保持原状。
   *
   * <p><b>审计设计：</b>回滚创建新版本（而非覆盖历史）， 新版本 changeLog = 「回滚自 {sourceVersion} by {operatorId}」，
   * 保持完整审计链（旧版本永不可变）。
   *
   * @param resourceKey 配置键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID
   * @return 新创建的回滚版本 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    // 1. 查询目标版本
    ConfigVersion targetVersionEntity =
        configVersionMapper.selectByKeyAndVersion(resourceKey, targetVersion);
    if (targetVersionEntity == null) {
      throw BusinessException.of(SystemExceptionCode.CONFIG_VERSION_NOT_FOUND)
          .data("resourceKey", resourceKey)
          .data("version", targetVersion);
    }

    // 2. 查询当前配置项作为回滚前快照（用于审计回溯）
    Config currentConfig =
        configRepository
            .getConfigMapper()
            .selectOne(new QueryWrapper<Config>().eq("config_key", resourceKey).eq("deleted", 0));
    String rollbackSnapshot = currentConfig != null ? YdszJson.toJson(currentConfig) : null;

    // 3. 反序列化目标快照并更新配置项
    String snapshotJson = targetVersionEntity.getSnapshotJson();
    if (StringUtils.isNotBlank(snapshotJson)) {
      try {
        ConfigDTO snapshotDTO = YdszJson.fromJson(snapshotJson, ConfigDTO.class);
        if (currentConfig != null) {
          // 更新现有配置项
          currentConfig.setConfigValue(snapshotDTO.getConfigValue());
          currentConfig.setValueType(snapshotDTO.getValueType());
          currentConfig.setDefaultValue(snapshotDTO.getDefaultValue());
          currentConfig.setDescription(snapshotDTO.getDescription());
          currentConfig.setIsPublic(snapshotDTO.getIsPublic());
          currentConfig.setSortOrder(snapshotDTO.getSortOrder());
          currentConfig.setStatus(snapshotDTO.getStatus());
          configRepository.getConfigMapper().updateById(currentConfig);
        } else {
          // 配置项已被删除，重新插入
          Config newConfig = new Config();
          newConfig.setConfigGroup(snapshotDTO.getConfigGroup());
          newConfig.setConfigKey(snapshotDTO.getConfigKey());
          newConfig.setConfigValue(snapshotDTO.getConfigValue());
          newConfig.setValueType(snapshotDTO.getValueType());
          newConfig.setDefaultValue(snapshotDTO.getDefaultValue());
          newConfig.setDescription(snapshotDTO.getDescription());
          newConfig.setIsPublic(snapshotDTO.getIsPublic());
          newConfig.setSortOrder(snapshotDTO.getSortOrder());
          newConfig.setStatus(snapshotDTO.getStatus());
          configRepository.getConfigMapper().insert(newConfig);
        }
      } catch (Exception e) {
        log.error(
            "[ConfigVersion] 快照解析失败: resourceKey={}, version={}, error={}",
            resourceKey,
            targetVersion,
            e.getMessage());
        throw BusinessException.of(SystemExceptionCode.SNAPSHOT_PARSE_ERROR)
            .data("reason", e.getMessage());
      }
    }

    // 4. 创建新版本（标记回滚来源）
    String newVersion = "v" + System.currentTimeMillis();
    String changeLog = String.format("回滚自 %s by %s", targetVersion, operatorId);
    ConfigVersion newVersionEntity = new ConfigVersion();
    newVersionEntity.setResourceKey(resourceKey);
    newVersionEntity.setConfigGroup(targetVersionEntity.getConfigGroup());
    newVersionEntity.setVersion(newVersion);
    newVersionEntity.setChangeLog(changeLog);
    newVersionEntity.setSnapshotJson(rollbackSnapshot);
    newVersionEntity.setEffectiveDate(LocalDateTime.now());
    configVersionMapper.insert(newVersionEntity);

    // 5. 失效缓存
    cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).clear();

    log.info(
        "[ConfigVersion] 回滚完成: resourceKey={}, targetVersion={}, newVersion={}",
        resourceKey,
        targetVersion,
        newVersion);
    return newVersionEntity.getId();
  }
}
