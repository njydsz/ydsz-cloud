package com.njydsz.workflow.server.service.impl.definition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;

/**
 * 流程定义发布管理器
 *
 * <p>承担流程定义<b>发布/停用/切换/回滚</b>相关全部职责：发布（含兼容性校验）、停用、
 * 激活版本切换、启用/停用、一键回滚（含在途实例迁移）。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>发布管理</b>：发布流程定义（含版本兼容性校验，HIGH 风险可阻断）
 *   <li><b>停用管理</b>：停用（deprecate）流程定义，清理缓存
 *   <li><b>版本切换</b>：切换同 flowCode 的激活版本，原子性失效旧版本 + 激活新版本
 *   <li><b>启用/停用</b>：activityStatus 维度的启用/停用切换
 *   <li><b>一键回滚</b>：切换回上一版本并自动迁移在途实例
 * </ul>
 *
 * <p><b>缓存治理：</b>发布/停用/切换/回滚时通过 {@link CacheEvict} 双层失效本地与 Redis 集群缓存。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class FlowDefinitionPublishManager {

    /** 停用流程时发布的版本号（9 表示停用态） */
  private static final int DEPRECATE_PUBLISH_VERSION = 9;

  /** 流程定义仓储 */
  private final FlowDefinitionRepository definitionRepository;

  /** 流程节点仓储 */
  private final FlowNodeRepository nodeRepository;

  /** 流程定义元数据缓存 */
  private final FlowDefinitionCacheService flowDefinitionCacheService;

  /** 统一配置属性 */
  private final FlowProperties flowProperties;

  /** 变更影响分析管理器 */
  private final FlowDefinitionMigrationManager migrationManager;

  /** 流程实例迁移服务（一键回滚时迁移在途实例） */
  private final FlowInstanceMigrationService migrationService;

  public FlowDefinitionPublishManager(
      FlowDefinitionRepository definitionRepository,
      FlowNodeRepository nodeRepository,
      FlowDefinitionCacheService flowDefinitionCacheService,
      FlowProperties flowProperties,
      FlowDefinitionMigrationManager migrationManager,
      @Lazy FlowInstanceMigrationService migrationService) {
    this.definitionRepository = definitionRepository;
    this.nodeRepository = nodeRepository;
    this.flowDefinitionCacheService = flowDefinitionCacheService;
    this.flowProperties = flowProperties;
    this.migrationManager = migrationManager;
    this.migrationService = migrationService;
  }

  /**
   * 发布流程定义（不带强制标志）
   *
   * <p>默认调用 {@link #publish(String, boolean)} 并传 {@code force=false}，
   * 当变更影响分析为 HIGH 风险时会被 {@link #checkPublishCompatibility} 阻断。
   *
   * @param definitionId 流程定义 ID
   * @see #publish(String, boolean)
   */
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public void publish(String definitionId) {
    publish(definitionId, false);
  }

  /**
   * 发布流程定义（带强制标志）
   *
   * <p>将 {@code ydsz_flow_definition.isPublish=0 → 1}，并清理本地 + Redis 集群缓存。
   * 发布前通过 {@link #checkPublishCompatibility} 评估与同 {@code flowCode} 激活版本的差异。
   *
   * @param definitionId 流程定义 ID
   * @param force 是否强制发布（跳过 HIGH 风险阻断）
   * @throws SysException {@code NOT_FOUND} — 流程定义不存在；{@code BAD_REQUEST} — HIGH 风险未强制发布
   */
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public void publish(String definitionId, boolean force) {
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    checkPublishCompatibility(def, force);
    definitionRepository.publish(definitionId, 1);
    flowDefinitionCacheService.evict(definitionId);
    log.info(
        "[Flow] 发布流程: defId={} flowCode={} version={} force={}",
        definitionId,
        def.getFlowCode(),
        def.getFlowVersion(),
        force);
  }

  /**
   * 停用流程定义
   *
   * <p>将 {@code isPublish} 置为 {@code 9}（已废弃），并清理本地 + 集群缓存。
   * 停用后流程定义将无法被新实例引用，但已有实例不受影响。
   *
   * @param definitionId 流程定义 ID
   */
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public void deprecate(String definitionId) {
    definitionRepository.publish(definitionId, DEPRECATE_PUBLISH_VERSION);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 停用流程: defId={}", definitionId);
  }

  /**
   * 切换激活版本
   *
   * <p>将同 {@code flowCode} 的其他已发布版本置为 {@code isPublish=0}，目标版本置为 {@code isPublish=1}。
   *
   * @param flowCode 流程编码
   * @param definitionId 目标定义 ID
   * @param tenantId 租户 ID
   * @throws SysException {@code BAD_REQUEST} / {@code NOT_FOUND} — 参数缺失或定义不存在
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public void switchActiveVersion(String flowCode, String definitionId, String tenantId) {
    if (!StringUtils.hasText(flowCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("flowCode 不能为空")
          .build();
    }
    FlowDefinitionVO def = definitionRepository.findById(definitionId).orElse(null);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    if (!flowCode.equals(def.getFlowCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("flowCode 不匹配: 期望=" + flowCode + " 实际=" + def.getFlowCode())
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    definitionRepository.deactivateByFlowCode(flowCode, definitionId, tid);
    definitionRepository.publish(definitionId, 1);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 切换流程定义版本: flowCode={} → defId={} tenantId={}", flowCode, definitionId, tid);
  }

  /**
   * 启用流程定义
   *
   * <p>将 {@code activityStatus=0 → 1}，恢复流程定义在设计器与发起页可见。
   *
   * @param definitionId 流程定义 ID
   */
  public void enable(String definitionId) {
    definitionRepository.updateActivityStatus(definitionId, 1);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 启用流程定义: defId={}", definitionId);
  }

  /**
   * 停用流程定义（activityStatus 维度）
   *
   * <p>将 {@code activityStatus=1 → 0}，流程定义仍在数据库但设计器与发起页不可见。
   *
   * @param definitionId 流程定义 ID
   */
  public void disable(String definitionId) {
    definitionRepository.updateActivityStatus(definitionId, 0);
    flowDefinitionCacheService.evict(definitionId);
    log.info("[Flow] 停用流程定义: defId={}", definitionId);
  }

  /**
   * 流程定义一键回滚
   *
   * <p>将指定 flowCode 的激活版本切换回上一个已发布版本，并自动迁移在途实例。
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID
   * @return 回滚结果报告
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {CacheConstants.FLOW_DEF_PUBLISHED_CACHE, CacheConstants.FLOW_DEF_LATEST_CACHE},
      allEntries = true)
  public Map<String, Object> rollbackDefinition(String flowCode, String tenantId) {
    if (!StringUtils.hasText(flowCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("flowCode 不能为空")
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

    FlowDefinitionVO currentDef = definitionRepository.findPublished(flowCode, null, tid)
        .orElse(null);
    if (currentDef == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("未找到当前激活的流程定义: flowCode=" + flowCode)
          .build();
    }

    FlowDefinitionVO previousDef = definitionRepository.findPreviousPublishedVersion(flowCode, tid, currentDef.getId())
        .orElse(null);
    if (previousDef == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("无可回滚的历史版本: flowCode=" + flowCode)
          .build();
    }

    Map<String, Object> migrationImpact =
        migrationManager.analyzeMigrationImpact(currentDef.getId(), previousDef.getId());
    String riskLevel = (String) migrationImpact.get("riskLevel");

    if ("HIGH".equals(riskLevel)) {
      log.warn(
          "[Flow] 一键回滚中止（HIGH 风险）: flowCode={} current={} target={} risk={}",
          flowCode,
          currentDef.getId(),
          previousDef.getId(),
          riskLevel);
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("回滚风险等级为 HIGH，存在在途实例将卡死，请先处理在途实例后再回滚")
          .build();
    }

    switchActiveVersion(flowCode, previousDef.getId(), tid);

    InstanceMigrationResultDTO migrationResult = null;
    try {
      InstanceMigrationDTO migrateDto = new InstanceMigrationDTO();
      migrateDto.setSourceDefinitionId(currentDef.getId());
      migrateDto.setTargetDefinitionId(previousDef.getId());
      migrateDto.setTenantId(tid);
      Map<String, String> nodeMapping = new HashMap<>(16);
      List<FlowNodeVO> oldNodes = nodeRepository.findByDefinitionId(currentDef.getId());
      List<FlowNodeVO> newNodes = nodeRepository.findByDefinitionId(previousDef.getId());
      Set<String> newNodeCodes =
          newNodes.stream().map(FlowNodeVO::getNodeCode).collect(Collectors.toSet());
      for (FlowNodeVO oldNode : oldNodes) {
        if (newNodeCodes.contains(oldNode.getNodeCode())) {
          nodeMapping.put(oldNode.getNodeCode(), oldNode.getNodeCode());
        }
      }
      migrateDto.setNodeMapping(nodeMapping);
      migrationResult = migrationService.migrate(migrateDto);
      log.info(
          "[Flow] 一键回滚实例迁移完成: flowCode={} migrated={} skipped={}",
          flowCode,
          migrationResult != null ? migrationResult.getMigratedCount() : 0,
          migrationResult != null ? migrationResult.getSkippedCount() : 0);
    } catch (Exception e) {
      log.error("[Flow] 一键回滚实例迁移异常: flowCode={} err={}", flowCode, e.getMessage(), e);
    }

    Map<String, Object> fromInfo = new LinkedHashMap<>(16);
    fromInfo.put("id", currentDef.getId());
    fromInfo.put("flowVersion", currentDef.getFlowVersion());

    Map<String, Object> toInfo = new LinkedHashMap<>(16);
    toInfo.put("id", previousDef.getId());
    toInfo.put("flowVersion", previousDef.getFlowVersion());

    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("fromDefinition", fromInfo);
    result.put("toDefinition", toInfo);
    result.put("migrationImpact", migrationImpact);
    result.put("migrationResult", migrationResult);
    result.put("rollbackTime", LocalDateTime.now().toString());

    log.info(
        "[Flow] 一键回滚完成: flowCode={} from=v{} to=v{} risk={}",
        flowCode,
        currentDef.getFlowVersion(),
        previousDef.getFlowVersion(),
        riskLevel);
    return result;
  }

  /**
   * 发布前版本兼容性校验
   *
   * @param def 当前待发布的流程定义 VO
   * @param force 是否强制发布（跳过 HIGH 风险阻断）
   */
  private void checkPublishCompatibility(FlowDefinitionVO def, boolean force) {
    String flowCode = def.getFlowCode();
    String tenantId = def.getTenantId() != null ? def.getTenantId() : "1";

    FlowDefinitionVO activeDef = definitionRepository.findPublished(flowCode, null, tenantId)
        .orElse(null);
    if (activeDef == null || activeDef.getId().equals(def.getId())) {
      log.debug("[Flow][P1-4] 无前序激活版本，跳过兼容性校验: flowCode={} defId={}", flowCode, def.getId());
      return;
    }

    Map<String, Object> impact;
    try {
      impact = migrationManager.analyzeMigrationImpact(activeDef.getId(), def.getId());
    } catch (Exception e) {
      log.warn(
          "[Flow][P1-4] 变更影响分析失败，跳过兼容性校验: oldDef={} newDef={} err={}",
          activeDef.getId(),
          def.getId(),
          e.getMessage());
      return;
    }

    String riskLevel = (String) impact.get("riskLevel");
    long runningTotal = extractLong(impact, "runningInstances", "total");
    List<String> recommendations = extractStringList(impact, "recommendations");

    if ("HIGH".equals(riskLevel)) {
      if (force) {
        log.warn(
            "[Flow][P1-4] 强制发布 HIGH 风险流程: flowCode={} newDef={} oldDef={} "
                + "runningInstances={} recommendations={}",
            flowCode,
            def.getId(),
            activeDef.getId(),
            runningTotal,
            recommendations);
      } else if (flowProperties.isPublishBlockOnHighRisk()) {
        log.warn(
            "[Flow][P1-4] 阻断 HIGH 风险发布: flowCode={} newDef={} oldDef={} "
                + "runningInstances={} recommendations={}",
            flowCode,
            def.getId(),
            activeDef.getId(),
            runningTotal,
            recommendations);
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message(
                "发布阻断：存在 "
                    + runningTotal
                    + " 个在途实例将因节点删除而卡死。"
                    + "请先处理在途实例（强制流转/通知撤回/等待完成），"
                    + "或使用 force=true 参数强制发布（需管理员权限）。"
                    + "建议："
                    + String.join("；", recommendations))
            .build();
      } else {
        log.warn(
            "[Flow][P1-4] block-on-high-risk=false，放行 HIGH 风险发布: flowCode={} "
                + "newDef={} oldDef={} runningInstances={} recommendations={}",
            flowCode,
            def.getId(),
            activeDef.getId(),
            runningTotal,
            recommendations);
      }
    } else if ("MEDIUM".equals(riskLevel) || "LOW".equals(riskLevel)) {
      log.warn(
          "[Flow][P1-4] 发布 {} 风险流程: flowCode={} newDef={} oldDef={} "
              + "runningInstances={} recommendations={}",
          riskLevel,
          flowCode,
          def.getId(),
          activeDef.getId(),
          runningTotal,
          recommendations);
    } else {
      log.info(
          "[Flow][P1-4] 发布无风险: flowCode={} newDef={} oldDef={} runningInstances=0",
          flowCode,
          def.getId(),
          activeDef.getId());
    }
  }

  /**
   * 从影响分析结果中提取 long 值（兼容嵌套 Map 结构）
   *
   * @param root 影响分析结果 Map
   * @param keys 嵌套 key 路径（按层级深入取值）
   * @return 解析到的 long 值；路径中途断链或最终值非 Number 时返回 0L
   */
  private long extractLong(Map<String, Object> root, String... keys) {
    Object current = root;
    for (String key : keys) {
      if (current instanceof Map) {
        current = ((Map<?, ?>) current).get(key);
      } else {
        return 0L;
      }
    }
    if (current instanceof Number) {
      return ((Number) current).longValue();
    }
    return 0L;
  }

  /**
   * 从影响分析结果中提取 String 列表
   *
   * @param root 影响分析结果 Map
   * @param key 要读取的列表属性名
   * @return 字符串列表；value 非 List 或为空时返回空列表
   */
  private List<String> extractStringList(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (value instanceof List) {
      List<String> result = new ArrayList<>(16);
      for (Object item : (List<?>) value) {
        if (item != null) {
          result.add(String.valueOf(item));
        }
      }
      return result;
    }
    return Collections.emptyList();
  }
}
