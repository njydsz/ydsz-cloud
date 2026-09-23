package com.njydsz.system.server.service.impl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.repository.EntityVersionRepository;
import com.njydsz.system.domain.vo.ConfigDiffVO;
import com.njydsz.system.domain.vo.ConfigDiffVO.ChangeType;
import com.njydsz.system.domain.vo.ConfigDiffVO.FieldDiff;
import com.njydsz.system.domain.vo.ConfigDiffVO.VersionBrief;
import com.njydsz.system.domain.vo.EntityVersionVO;
import com.njydsz.system.server.service.EntityVersionService;
import com.njydsz.system.server.service.rollback.RollbackStrategy;
import com.njydsz.system.server.util.SystemVersionUtils;




/**
 * 统一实体版本 Service 实现
 *
 * <p>对 {@link EntityVersionService} 接口的完整实现，为 Config/Dict/Variable 提供统一的版本管理能力。
 * 替代原有的三套独立版本服务实现（ConfigVersionServiceImpl/DictVersionServiceImpl/
 * VariableVersionServiceImpl）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>版本查询</b>：按资源类型 + 资源键查询历史版本
 *   <li><b>版本创建</b>：业务 Service 写操作成功后调用
 *   <li><b>版本回滚</b>：通过策略接口实现资源重建
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}；
 * 读方法不开启事务，依赖 MyBatis 自动提交。
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityVersionServiceImpl implements EntityVersionService {

  private final EntityVersionRepository entityVersionRepository;

  @Override
  public List<EntityVersionVO> listByResourceTypeAndKey(String resourceType, String resourceKey) {
    return entityVersionRepository.findByTypeAndKey(resourceType, resourceKey);
  }

  @Override
  public PageResponse<List<EntityVersionVO>> pageByResourceTypeAndKey(EntityVersionPageQuery query) {
    return entityVersionRepository.findPageByTypeAndKey(query);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createVersion(EntityVersionDTO dto) {
    EntityVersionVO saved = entityVersionRepository.save(dto);
    return saved.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String rollbackTo(
      String resourceType,
      String resourceKey,
      String targetVersion,
      String operatorId,
      RollbackStrategy rollbackStrategy) {
    // 1. 查询目标版本
    EntityVersionVO targetVersionVO =
        entityVersionRepository
            .findByTypeAndKeyAndVersion(resourceType, resourceKey, targetVersion)
            .orElseThrow(
                () ->
                    BusinessException.of(SystemExceptionCode.ENTITY_VERSION_NOT_FOUND)
                        .data("resourceType", resourceType)
                        .data("resourceKey", resourceKey)
                        .data("version", targetVersion));

    // 2. 执行回滚策略（由业务方实现资源重建逻辑）
    String snapshotJson = targetVersionVO.getSnapshotJson();
    if (snapshotJson != null && !snapshotJson.isBlank()) {
      rollbackStrategy.rebuild(snapshotJson);
    }

    // 3. 创建新版本（标记回滚来源）
    String newVersion = SystemVersionUtils.nextVersion();
    String changeLog = String.format("回滚自 %s by %s", targetVersion, operatorId);
    EntityVersionDTO newVersionDto = EntityVersionDTO.builder()
        .resourceType(resourceType)
        .resourceKey(resourceKey)
        .resourceGroup(targetVersionVO.getResourceGroup())
        .version(newVersion)
        .changeLog(changeLog)
        .snapshotJson(snapshotJson)
        .build();
    EntityVersionVO newVersionVO = entityVersionRepository.save(newVersionDto);

    log.info(
        "[EntityVersion] 回滚完成: resourceType={}, resourceKey={}, targetVersion={}, newVersion={}",
        resourceType,
        resourceKey,
        targetVersion,
        newVersion);
    return newVersionVO.getId();
  }

  /**
   * 对比两个版本的快照 JSON，生成字段级差异。
   *
   * <p>对两个版本的快照 JSON 做字段级 diff，返回变更类型（MODIFIED / ADDED / REMOVED）和对应旧值/新值。
   *
   * @param resourceType 资源类型（CONFIG/DICT/VARIABLE）
   * @param resourceKey 资源唯一标识
   * @param fromVersion 源版本号（基准）
   * @param toVersion 目标版本号（对比对象）
   * @return 版本对比结果（含字段级 diff 列表）
   * @throws BusinessException 任一版本不存在时抛出
   */
  @Override
  public ConfigDiffVO diffConfigVersions(
      String resourceType, String resourceKey, String fromVersion, String toVersion) {
    // 1. 查询两个版本
    EntityVersionVO fromVersionVO =
        entityVersionRepository
            .findByTypeAndKeyAndVersion(resourceType, resourceKey, fromVersion)
            .orElseThrow(
                () ->
                    BusinessException.of(SystemExceptionCode.ENTITY_VERSION_NOT_FOUND)
                        .data("resourceType", resourceType)
                        .data("resourceKey", resourceKey)
                        .data("version", fromVersion));
    EntityVersionVO toVersionVO =
        entityVersionRepository
            .findByTypeAndKeyAndVersion(resourceType, resourceKey, toVersion)
            .orElseThrow(
                () ->
                    BusinessException.of(SystemExceptionCode.ENTITY_VERSION_NOT_FOUND)
                        .data("resourceType", resourceType)
                        .data("resourceKey", resourceKey)
                        .data("version", toVersion));

    // 2. 组装版本摘要
    ConfigDiffVO result = new ConfigDiffVO();
    VersionBrief fromBrief = new VersionBrief();
    fromBrief.setVersion(fromVersionVO.getVersion());
    fromBrief.setSnapshotJson(fromVersionVO.getSnapshotJson());
    fromBrief.setEffectiveDate(fromVersionVO.getEffectiveDate() != null
        ? fromVersionVO.getEffectiveDate().toString() : null);
    result.setFromVersion(fromBrief);
    VersionBrief toBrief = new VersionBrief();
    toBrief.setVersion(toVersionVO.getVersion());
    toBrief.setSnapshotJson(toVersionVO.getSnapshotJson());
    toBrief.setEffectiveDate(toVersionVO.getEffectiveDate() != null
        ? toVersionVO.getEffectiveDate().toString() : null);
    result.setToVersion(toBrief);

    // 3. 执行 JSON diff
    List<FieldDiff> diffs = computeJsonDiff(
        resolveSnapshot(fromVersionVO.getSnapshotJson()),
        resolveSnapshot(toVersionVO.getSnapshotJson()));
    result.setDiffs(diffs);
    return result;
  }

  /**
   * 将快照 JSON 反序列化为扁平 Map；解析失败时返回空 Map（避免影响 diff 主流程）。
   *
   * @param snapshotJson 快照 JSON 字符串
   * @return 字段名 → 字段值字符串 的 Map；解析失败时返回空 Map
   */
  private Map<String, String> resolveSnapshot(String snapshotJson) {
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, Object> rawMap = YdszJson.parseMap(snapshotJson);
      Map<String, String> resultMap = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
        resultMap.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
      }
      return resultMap;
    } catch (Exception e) {
      log.warn("解析快照 JSON 进行 diff 失败: {}", e.getMessage());
      return new LinkedHashMap<>();
    }
  }

  /**
   * 计算两个快照 Map 的字段级 diff。
   *
   * @param fromMap 源版本字段值
   * @param toMap 目标版本字段值
   * @return 字段差异列表（有序）
   */
  private List<FieldDiff> computeJsonDiff(Map<String, String> fromMap, Map<String, String> toMap) {
    List<FieldDiff> diffs = new ArrayList<>();
    // 合并所有 key（保持插入顺序）
    List<String> allKeys = new ArrayList<>(fromMap.keySet());
    for (String key : toMap.keySet()) {
      if (!allKeys.contains(key)) {
        allKeys.add(key);
      }
    }
    for (String key : allKeys) {
      String fromVal = fromMap.get(key);
      String toVal = toMap.get(key);
      if (Objects.equals(fromVal, toVal)) {
        continue;
      }
      FieldDiff diff = new FieldDiff();
      diff.setField(key);
      diff.setOldValue(fromVal);
      diff.setNewValue(toVal);
      if (fromVal == null) {
        diff.setChangeType(ChangeType.ADDED);
      } else if (toVal == null) {
        diff.setChangeType(ChangeType.REMOVED);
      } else {
        diff.setChangeType(ChangeType.MODIFIED);
      }
      diffs.add(diff);
    }
    return diffs;
  }
}
