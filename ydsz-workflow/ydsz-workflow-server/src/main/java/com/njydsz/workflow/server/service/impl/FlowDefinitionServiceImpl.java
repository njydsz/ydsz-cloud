package com.njydsz.workflow.server.service.impl;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.vo.FlowBatchDeployResultVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDetailVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionDiffVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVersionVO;
import com.njydsz.workflow.domain.vo.FlowMigrationImpactVO;
import com.njydsz.workflow.domain.vo.FlowRollbackResultVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.impl.definition.FlowDefinitionDeployManager;
import com.njydsz.workflow.server.service.impl.definition.FlowDefinitionDesignManager;
import com.njydsz.workflow.server.service.impl.definition.FlowDefinitionMigrationManager;
import com.njydsz.workflow.server.service.impl.definition.FlowDefinitionPublishManager;
import com.njydsz.workflow.server.service.impl.definition.FlowDefinitionQueryService;

/**
 * 流程定义 Service 实现（门面模式）
 *
 * <p>对 {@link FlowDefinitionService} 接口的完整实现，作为<b>门面（Facade）</b>委托给 5 个管理器完成实际工作：
 *
 * <ul>
 *   <li>{@link FlowDefinitionDeployManager} — 双模式部署（BPMN XML / JSON）+ 拓扑校验 + 三方写入 + zip 批量部署
 *   <li>{@link FlowDefinitionPublishManager} — 发布 / 停用 / 版本切换 / 启用停用 / 一键回滚
 *   <li>{@link FlowDefinitionQueryService} — 查询能力（已发布 / 最新版本 / 分页 / 详情 / 版本历史）
 *   <li>{@link FlowDefinitionDesignManager} — 设计器集成（坐标同步 / 协同编辑锁 / 表单字段权限 / SLA 配置）
 *   <li>{@link FlowDefinitionMigrationManager} — 变更影响分析 / 版本对比 / 导入导出
 * </ul>
 *
 * <p><b>设计意图：</b>原实现类约 2300+ 行混合多种职责，现拆分为职责清晰的多个管理器。
 * 本类保持接口签名不变，仅做委派调用，方便上层 Controller 无感知切换。
 *
 * <p><b>事务与缓存：</b>{@code @Transactional} / {@code @CacheEvict} / {@code @Cacheable}
 * 标注在各管理器的具体执行方法上，本类不重复声明。
 *
 * <p><b>多租户：</b>所有查询与写入均按 {@code tenantId} 隔离，管理器内部处理租户上下文回退逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDefinitionService 接口定义
 * @see FlowDefinitionDeployManager 部署管理器
 * @see FlowDefinitionPublishManager 发布管理器
 * @see FlowDefinitionQueryService 查询服务
 * @see FlowDefinitionDesignManager 设计器管理器
 * @see FlowDefinitionMigrationManager 迁移管理器
 */
@Slf4j
@Service
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

  /** 部署管理器：双模式部署 + 拓扑校验 + 三方写入 + zip 批量部署 */
  private final FlowDefinitionDeployManager deployManager;

  /** 发布管理器：发布 / 停用 / 版本切换 / 启用停用 / 一键回滚 */
  private final FlowDefinitionPublishManager publishManager;

  /** 查询服务：已发布 / 最新版本 / 分页 / 详情 / 版本历史 */
  private final FlowDefinitionQueryService queryService;

  /** 设计器管理器：坐标同步 / 协同编辑锁 / 表单字段权限 / SLA 配置 */
  private final FlowDefinitionDesignManager designManager;

  /** 迁移管理器：变更影响分析 / 版本对比 / 导入导出 */
  private final FlowDefinitionMigrationManager migrationManager;

  public FlowDefinitionServiceImpl(
      FlowDefinitionDeployManager deployManager,
      FlowDefinitionPublishManager publishManager,
      FlowDefinitionQueryService queryService,
      FlowDefinitionDesignManager designManager,
      FlowDefinitionMigrationManager migrationManager) {
    this.deployManager = deployManager;
    this.publishManager = publishManager;
    this.queryService = queryService;
    this.designManager = designManager;
    this.migrationManager = migrationManager;
  }

  @Override
  public String deploy(FlowDeployProcessDTO dto) {
    return deployManager.deploy(dto);
  }

  @Override
  public void publish(String definitionId) {
    publishManager.publish(definitionId);
  }

  @Override
  public void publish(String definitionId, boolean force) {
    publishManager.publish(definitionId, force);
  }

  @Override
  public void deprecate(String definitionId) {
    publishManager.deprecate(definitionId);
  }

  @Override
  public FlowDefinitionVO getPublished(String flowCode, String version, String tenantId) {
    return queryService.getPublished(flowCode, version, tenantId);
  }

  @Override
  public FlowDefinitionVO getLatestByCode(String flowCode, String tenantId) {
    return queryService.getLatestByCode(flowCode, tenantId);
  }

  @Override
  public List<FlowDefinitionVO> page(int pageNo, int pageSize, String category, String flowCode) {
    return queryService.page(pageNo, pageSize, category, flowCode);
  }

  @Override
  public FlowDefinitionDetailVO getDetail(String definitionId) {
    Map<String, Object> map = queryService.getDetail(definitionId);
    if (map == null) { return null; }
    return YdszJson.convertValue(map, FlowDefinitionDetailVO.class);
  }

  @Override
  public void switchActiveVersion(String flowCode, String definitionId, String tenantId) {
    publishManager.switchActiveVersion(flowCode, definitionId, tenantId);
  }

  @Override
  public void enable(String definitionId) {
    publishManager.enable(definitionId);
  }

  @Override
  public void disable(String definitionId) {
    publishManager.disable(definitionId);
  }

  @Override
  public void updateNodeCoordinate(String definitionId, String nodeCode, String coordinate) {
    designManager.updateNodeCoordinate(definitionId, nodeCode, coordinate);
  }

  @Override
  public void updateDefinition(String definitionId, FlowDeployProcessDTO dto) {
    designManager.updateDefinition(definitionId, dto);
  }

  @Override
  public String exportDefinition(String definitionId) {
    return migrationManager.exportDefinition(definitionId);
  }

  @Override
  public String importDefinition(String json, String tenantId) {
    return migrationManager.importDefinition(json, tenantId);
  }

  @Override
  public Map<String, Object> getDesignerData(String definitionId) {
    return designManager.getDesignerData(definitionId);
  }

  @Override
  public void saveDesignerData(String definitionId, Map<String, Object> designerData) {
    designManager.saveDesignerData(definitionId, designerData);
  }

  @Override
  public String getFormConfig(String definitionId, String nodeCode) {
    return designManager.getFormConfig(definitionId, nodeCode);
  }

  @Override
  public void saveFormConfig(String definitionId, String nodeCode, String formFieldsConfig) {
    designManager.saveFormConfig(definitionId, nodeCode, formFieldsConfig);
  }

  @Override
  public String getSlaConfig(String definitionId, String nodeCode) {
    return designManager.getSlaConfig(definitionId, nodeCode);
  }

  @Override
  public void saveSlaConfig(String definitionId, String nodeCode, String slaConfig) {
    designManager.saveSlaConfig(definitionId, nodeCode, slaConfig);
  }

  @Override
  public List<FlowDefinitionVersionVO> listVersions(String definitionId) {
    List<Map<String, Object>> list = queryService.listVersions(definitionId);
    if (list == null) { return null; }
    return YdszJson.convertValue(list,
        new com.njydsz.common.json.type.JsonType<List<FlowDefinitionVersionVO>>() {});
  }

  @Override
  public FlowDefinitionDiffVO diffVersions(String definitionId, Integer version1, Integer version2) {
    Map<String, Object> map = migrationManager.diffVersions(definitionId, version1, version2);
    if (map == null) { return null; }
    return YdszJson.convertValue(map, FlowDefinitionDiffVO.class);
  }

  @Override
  public FlowBatchDeployResultVO batchDeployFromZip(byte[] zipBytes, String tenantId) {
    Map<String, Object> map = deployManager.batchDeployFromZip(zipBytes, tenantId);
    if (map == null) { return null; }
    return YdszJson.convertValue(map, FlowBatchDeployResultVO.class);
  }

  @Override
  public boolean lockDefinition(String definitionId, String userId) {
    return designManager.lockDefinition(definitionId, userId);
  }

  @Override
  public boolean unlockDefinition(String definitionId, String userId) {
    return designManager.unlockDefinition(definitionId, userId);
  }

  @Override
  public Map<String, Object> getLockStatus(String definitionId) {
    return designManager.getLockStatus(definitionId);
  }

  @Override
  public FlowMigrationImpactVO analyzeMigrationImpact(String oldDefinitionId, String newDefinitionId) {
    Map<String, Object> map = migrationManager.analyzeMigrationImpact(oldDefinitionId, newDefinitionId);
    if (map == null) { return null; }
    return YdszJson.convertValue(map, FlowMigrationImpactVO.class);
  }

  @Override
  public FlowRollbackResultVO rollbackDefinition(String flowCode, String tenantId) {
    Map<String, Object> map = publishManager.rollbackDefinition(flowCode, tenantId);
    if (map == null) { return null; }
    return YdszJson.convertValue(map, FlowRollbackResultVO.class);
  }
}
