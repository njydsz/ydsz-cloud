package com.njydsz.system.server.service.impl;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.EntityVersionDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.EntityVersionPageQuery;
import com.njydsz.system.domain.repository.EntityVersionRepository;
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
}
