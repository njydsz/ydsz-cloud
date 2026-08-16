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
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.domain.entity.VariableVersion;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.VariableVersionVO;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.infra.mapper.VariableVersionMapper;
import com.njydsz.system.server.service.VariableVersionService;

/**
 * 变量版本 Service 实现
 *
 * <p>对 {@link VariableVersionService} 接口的完整实现，是「变量中心」版本管理子系统的核心业务逻辑层。 维护变量（{@code
 * variableKey}）的变更历史快照，支持版本回滚、变更审计、变量复盘等场景。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本查询</b>：{@link #listByResourceKey} — 按 {@code variableKey} 查询某变量的所有历史版本
 *   <li><b>版本创建</b>：{@link #createVersion} — 由 {@link VariableServiceImpl} 在写操作成功后调用
 *   <li><b>版本回滚</b>：{@link #rollbackTo} — 通过 {@code snapshotJson} 重建变量
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}； 读方法不开启事务，依赖 MyBatis
 * 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see VariableVersionService 变量版本 Service 接口
 * @see VariableServiceImpl 变量 Service（写操作触发版本快照）
 * @see com.njydsz.system.domain.entity.VariableVersion 变量版本实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableVersionServiceImpl implements VariableVersionService {

  /** 变量版本 Mapper（继承 {@code ydsz_variable_version} 表 CRUD） */
  private final VariableVersionMapper variableVersionMapper;

  /** 变量 Mapper（用于回滚时更新变量） */
  private final VariableMapper variableMapper;

  /** Spring Cache 管理器（用于失效本地缓存） */
  private final CacheManager cacheManager;

  /**
   * 按变量键查询所有历史版本（按生效时间倒序）
   *
   * <p>典型调用方：管理后台「变量历史版本」列表页。
   *
   * @param resourceKey 变量键（{@code ydsz_variable.variable_key}）
   * @return 历史版本列表（最新生效时间在前），无版本时返回<b>空列表</b>（不是 null）
   */
  @Override
  public List<VariableVersionVO> listByResourceKey(String resourceKey) {
    return variableVersionMapper.listByResourceKey(resourceKey).stream()
        .map(SystemConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * 创建变量版本快照
   *
   * <p>由 {@link VariableServiceImpl} 在变量变更（增 / 删 / 改）后调用， 记录变更前的<b>变量 JSON 快照</b>，用于版本回滚和变更审计。
   *
   * <p><b>关键设计：</b>
   *
   * <ul>
   *   <li><b>事务一致性</b>：调用方需在<b>变量变更事务</b>内调用本方法， 通过 Spring 事务传播保证原子性（{@code PROPAGATION_REQUIRED}）
   *   <li><b>快照时机</b>：必须在<b>变更前</b>查询并快照原变量，而非变更后
   * </ul>
   *
   * @param resourceKey 变量键
   * @param version 版本号（由调用方决定格式）
   * @param changeLog 变更说明
   * @param snapshotJson 变更前的<b>变量 JSON 快照</b>
   * @return 新创建的版本 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createVersion(
      String resourceKey, String version, String changeLog, String snapshotJson) {
    VariableVersion entity = new VariableVersion();
    entity.setResourceKey(resourceKey);
    entity.setVersion(version);
    entity.setChangeLog(changeLog);
    entity.setSnapshotJson(snapshotJson);
    entity.setEffectiveDate(LocalDateTime.now());
    variableVersionMapper.insert(entity);
    return entity.getId();
  }

  /**
   * 回滚变量到指定版本
   *
   * <p>事务边界内执行「查询快照 → 更新变量 → 创建新版本 → 失效缓存」全链路。 若中间步骤失败，整个事务回滚，变量数据保持原状。
   *
   * <p><b>审计设计：</b>回滚创建新版本（而非覆盖历史）， 新版本 changeLog = 「回滚自 {sourceVersion} by {operatorId}」，
   * 保持完整审计链（旧版本永不可变）。
   *
   * @param resourceKey 变量键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID
   * @return 新创建的回滚版本 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(String resourceKey, String targetVersion, String operatorId) {
    // 1. 查询目标版本
    VariableVersion targetVersionEntity =
        variableVersionMapper.selectByKeyAndVersion(resourceKey, targetVersion);
    if (targetVersionEntity == null) {
      throw BusinessException.of(SystemExceptionCode.VARIABLE_VERSION_NOT_FOUND)
          .data("resourceKey", resourceKey)
          .data("version", targetVersion);
    }

    // 2. 查询当前变量作为回滚前快照（用于审计回溯）
    Variable currentVariable =
        variableMapper.selectOne(
            new QueryWrapper<Variable>().eq("variable_key", resourceKey).eq("deleted", 0));
    String rollbackSnapshot = currentVariable != null ? YdszJson.toJson(currentVariable) : null;

    // 3. 反序列化目标快照并更新变量
    String snapshotJson = targetVersionEntity.getSnapshotJson();
    if (StringUtils.isNotBlank(snapshotJson)) {
      try {
        VariableDTO snapshotDTO = YdszJson.fromJson(snapshotJson, VariableDTO.class);
        if (currentVariable != null) {
          // 更新现有变量
          currentVariable.setVariableValue(snapshotDTO.getVariableValue());
          currentVariable.setValueType(snapshotDTO.getValueType());
          currentVariable.setDescription(snapshotDTO.getDescription());
          currentVariable.setStatus(snapshotDTO.getStatus());
          variableMapper.updateById(currentVariable);
        } else {
          // 变量已被删除，重新插入
          Variable newVariable = new Variable();
          newVariable.setVariableKey(snapshotDTO.getVariableKey());
          newVariable.setVariableValue(snapshotDTO.getVariableValue());
          newVariable.setValueType(snapshotDTO.getValueType());
          newVariable.setDescription(snapshotDTO.getDescription());
          newVariable.setStatus(snapshotDTO.getStatus());
          variableMapper.insert(newVariable);
        }
      } catch (Exception e) {
        log.error(
            "[VariableVersion] 快照解析失败: resourceKey={}, version={}, error={}",
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
    VariableVersion newVersionEntity = new VariableVersion();
    newVersionEntity.setResourceKey(resourceKey);
    newVersionEntity.setVersion(newVersion);
    newVersionEntity.setChangeLog(changeLog);
    newVersionEntity.setSnapshotJson(rollbackSnapshot);
    newVersionEntity.setEffectiveDate(LocalDateTime.now());
    variableVersionMapper.insert(newVersionEntity);

    // 5. 失效缓存
    cacheManager.getCache(CacheConstants.SYSTEM_VARIABLE_CACHE).clear();

    log.info(
        "[VariableVersion] 回滚完成: resourceKey={}, targetVersion={}, newVersion={}",
        resourceKey,
        targetVersion,
        newVersion);
    return newVersionEntity.getId();
  }
}
