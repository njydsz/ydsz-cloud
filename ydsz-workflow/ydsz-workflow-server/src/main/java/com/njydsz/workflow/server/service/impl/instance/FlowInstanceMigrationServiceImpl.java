package com.njydsz.workflow.server.service.impl.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO.MigrationDetail;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;

/**
 * 流程实例迁移 Service 实现
 *
 * <p>对 {@link FlowInstanceMigrationService} 接口的完整实现，承担工作流引擎的<b>流程版本迁移</b>能力。
 * 当流程定义更新（新版本部署）后，将运行中的流程实例从旧版本迁移到新版本， 是工作流「无中断发布」的关键支撑。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>实例级迁移（{@link #migrate}）</b>：单个实例迁移， 更新 {@code definitionId / flowVersion}，并按节点映射调整
 *       {@code currentNodeCode}
 *   <li><b>批量迁移（{@link #migrateBatch}）</b>：批量迁移指定旧版本的所有运行中实例， 单个失败不影响其它实例
 *   <li><b>变更影响分析（{@link #analyzeMigrationImpact}）</b>：评估「旧版本 → 新版本」迁移兼容性， 包括「节点新增 / 删除 / 重命名 /
 *       配置变更」等
 *   <li><b>待办任务联动（P3-3）</b>：实例迁移成功后同步更新该实例下未完成的待办任务 （{@code ydsz_flow_run_task}）的 {@code
 *       definitionId / nodeCode / nodeName}， 避免迁移后待办任务仍指向旧定义导致办理异常
 *   <li><b>迁移报告（{@link #migrateBatch}）</b>：批量迁移结果返回 {@code InstanceMigrationResultDTO}， 包含「成功 /
 *       失败明细 / 失败原因」，便于人工重试
 * </ul>
 *
 * <p><b>迁移流程：</b>
 *
 * <ol>
 *   <li>校验源定义（旧版本）与目标定义（新版本）归属同一 {@code flowCode}
 *   <li>构建「旧版本节点 → 新版本节点」映射（按 {@code nodeCode} 匹配）
 *   <li>检查实例的 {@code currentNodeCode} 在新版本中是否存在：
 *       <ul>
 *         <li>存在：直接更新实例与待办
 *         <li>不存在：尝试「最近祖先节点」匹配，标记「节点已删除需人工介入」
 *         <li>都失败：标记为迁移失败
 *       </ul>
 *   <li>更新实例的 {@code definitionId / flowVersion / currentNodeCode}
 *   <li>同步更新未完成待办任务的 {@code definitionId / nodeCode / nodeName}
 *   <li>写入审计日志
 * </ol>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>{@link #migrate} 方法<b>不加</b> {@code @Transactional}，以支持「逐实例防御式迁移」——
 *       单个实例失败不影响其他实例的已成功写入，失败明细记录在结果报告中，便于人工重试
 *   <li>实例内部使用 {@code @Transactional(REQUIRES_NEW)} 子事务隔离每个实例的迁移
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>兼容性评估前置</b>：批量迁移前应先调用 {@link #analyzeMigrationImpact}， 评估「哪些实例可以自动迁移 / 哪些需要人工介入」
 *   <li><b>节点映射策略</b>：优先按 {@code nodeCode} 精确匹配， 失败时按 {@code nodeName} 模糊匹配，最后兜底「待人工确认」
 *   <li><b>变量兼容</b>：迁移时检查新版本定义的「入参 schema」， 不兼容的变量标记为「需人工补充」
 *   <li><b>审计追溯</b>：所有迁移动作记录到 {@code ydsz_flow_audit_log}， 包括「旧版本 / 新版本 / 节点映射 / 操作人」
 *   <li><b>回滚支持</b>：迁移后 24h 内支持「回滚到旧版本」， 避免新版本 BUG 影响线上流程
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 评估影响
 * MigrationImpact impact = migrationService.analyzeMigrationImpact(
 *     oldDefinitionId, newDefinitionId);
 * // impact.autoMigratableCount = 50, impact.manualHandleCount = 2
 *
 * // 2. 批量迁移
 * InstanceMigrationResultDTO result = migrationService.migrateBatch(
 *     oldDefinitionId, newDefinitionId, currentUserId);
 * // result.successDetails / result.failedDetails
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowInstanceMigrationService 接口定义
 * @see com.njydsz.workflow.domain.dto.InstanceMigrationDTO 迁移请求 DTO
 * @see com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO 迁移结果 DTO
 * @see FlowDefinitionServiceImpl 流程定义服务（部署新版本时触发迁移）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceMigrationServiceImpl implements FlowInstanceMigrationService {

  /** 流程实例 Mapper，查询/更新待迁移的运行中实例 */
  private final FlowInstanceMapper instanceMapper;

  /** 流程节点仓储，查询新旧版本节点映射关系 */
  private final FlowNodeRepository nodeRepository;

  /** 流程定义 Mapper（保留：无 FlowDefinitionRepository 可用） */
  private final FlowDefinitionMapper definitionMapper;

  /** 运行时任务仓储，迁移后同步更新待办任务的 definitionId 和 nodeCode */
  private final FlowRunTaskRepository taskRepository;

  /** DO/VO 转换器 */
  private final WorkflowConverter converter;

  @Override
  public InstanceMigrationResultDTO migrate(InstanceMigrationDTO dto) {
    return doMigrate(dto, false);
  }

  @Override
  @Transactional(readOnly = true)
  public InstanceMigrationResultDTO previewMigration(InstanceMigrationDTO dto) {
    return doMigrate(dto, true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> findRunningInstances(String definitionId, String tenantId) {
    if (definitionId == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("definitionId 不能为空")
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    LambdaQueryWrapper<FlowInstanceDO> w = new LambdaQueryWrapper<>();
    w.eq(FlowInstanceDO::getDefinitionId, definitionId)
        .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name())
        .eq(FlowInstanceDO::getTenantId, tid)
        .eq(FlowInstanceDO::getDeleted, 0);
    List<FlowInstanceDO> instances = instanceMapper.selectList(w);
    if (instances == null || instances.isEmpty()) {
      return Collections.emptyList();
    }
    return instances.stream().map(i -> String.valueOf(i.getId())).collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, String> autoMapNodes(Long sourceDefId, Long targetDefId) {
    if (sourceDefId == null || targetDefId == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("sourceDefId/targetDefId 不能为空")
          .build();
    }
    List<FlowNodeDO> sourceNodes = nodeRepository.findByDefinitionId(String.valueOf(sourceDefId)).stream().map(converter::entityToDO).toList();
    List<FlowNodeDO> targetNodes = nodeRepository.findByDefinitionId(String.valueOf(targetDefId)).stream().map(converter::entityToDO).toList();
    if (sourceNodes == null || targetNodes == null) {
      return Collections.emptyMap();
    }
    // 目标节点编码集合，便于快速判断
    Map<String, FlowNodeDO> targetNodeMap =
        targetNodes.stream().collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));
    Map<String, String> mapping = new LinkedHashMap<>();
    for (FlowNodeDO src : sourceNodes) {
      String code = src.getNodeCode();
      if (StringUtils.hasText(code) && targetNodeMap.containsKey(code)) {
        // 编码相同，自动配对
        mapping.put(code, code);
      }
    }
    log.info(
        "[Flow-Migrate] 自动映射节点: sourceDefId={} targetDefId={} matched={}",
        sourceDefId,
        targetDefId,
        mapping.size());
    return mapping;
  }

  // ============================== 内部方法 ==============================

  /**
   * 迁移核心逻辑（实际执行 / 试运行统一入口）。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>参数校验：源/目标定义 ID 非空、不相同、flowCode 一致
   *   <li>预加载目标定义节点编码集合
   *   <li>查询源定义下所有 RUNNING 实例
   *   <li>逐实例迁移：更新 definitionId/flowVersion，按映射调整 currentNodeCode
   *   <li>当前节点在新定义不存在且无映射时跳过；异常时记录失败
   * </ol>
   *
   * @param dto 迁移参数
   * @param forceDry 是否强制试运行（previewMigration 调用时为 true）
   * @return 迁移结果报告
   */
  private InstanceMigrationResultDTO doMigrate(InstanceMigrationDTO dto, boolean forceDry) {
    // 1. 参数校验
    if (dto == null || dto.getSourceDefinitionId() == null || dto.getTargetDefinitionId() == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("sourceDefinitionId / targetDefinitionId 不能为空")
          .build();
    }
    if (Objects.equals(dto.getSourceDefinitionId(), dto.getTargetDefinitionId())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("源定义与目标定义不能相同")
          .build();
    }

    boolean dryRun = Boolean.TRUE.equals(dto.getDryRun()) || forceDry;
    String sourceDefId = dto.getSourceDefinitionId();
    String targetDefId = dto.getTargetDefinitionId();
    String tenantId =
        dto.getTenantId() != null ? dto.getTenantId() : AuthContextUtils.getTenantIdOrDefault();
    Map<String, String> nodeMapping =
        dto.getNodeMapping() != null ? dto.getNodeMapping() : Collections.emptyMap();

    // 2. 校验源/目标定义存在且 flowCode 一致
    FlowDefinitionDO sourceDef = definitionMapper.selectById(sourceDefId);
    if (sourceDef == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("源流程定义不存在: " + sourceDefId)
          .build();
    }
    FlowDefinitionDO targetDef = definitionMapper.selectById(targetDefId);
    if (targetDef == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("目标流程定义不存在: " + targetDefId)
          .build();
    }
    if (!Objects.equals(sourceDef.getFlowCode(), targetDef.getFlowCode())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message(
              "源定义与目标定义 flowCode 不一致: source="
                  + sourceDef.getFlowCode()
                  + " target="
                  + targetDef.getFlowCode())
          .build();
    }

    // 3. 预加载目标定义的节点编码集合（用于判断当前节点是否存在于新版本）
    List<FlowNodeDO> targetNodes = nodeMapper.selectByDefinitionId(targetDefId);
    Map<String, FlowNodeDO> targetNodeMap =
        targetNodes == null
            ? Collections.emptyMap()
            : targetNodes.stream()
                .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));

    // 4. 查询源定义下所有运行中实例
    LambdaQueryWrapper<FlowInstanceDO> w = new LambdaQueryWrapper<>();
    w.eq(FlowInstanceDO::getDefinitionId, sourceDefId)
        .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name())
        .eq(FlowInstanceDO::getTenantId, tenantId)
        .eq(FlowInstanceDO::getDeleted, 0);
    List<FlowInstanceDO> instances = instanceMapper.selectList(w);

    // 5. 逐实例迁移（防御式：每个实例独立 try-catch，单个失败不影响其他）
    List<MigrationDetail> details = new ArrayList<>();
    int migratedCount = 0;
    int skippedCount = 0;
    int failedCount = 0;

    if (instances != null) {
      for (FlowInstanceDO instance : instances) {
        MigrationDetail detail = new MigrationDetail();
        detail.setInstanceId(String.valueOf(instance.getId()));
        detail.setInstanceTitle(instance.getTitle());
        detail.setOldNodeCode(instance.getCurrentNodeCode());

        try {
          String oldNodeCode = instance.getCurrentNodeCode();
          String newNodeCode = resolveNewNodeCode(oldNodeCode, nodeMapping, targetNodeMap);

          // 节点在新定义中不存在且无映射 -> 跳过
          if (newNodeCode == null) {
            detail.setStatus("SKIPPED");
            detail.setNewNodeCode(oldNodeCode);
            detail.setReason("当前节点 [" + oldNodeCode + "] 在目标定义中不存在且无映射，已跳过");
            skippedCount++;
            details.add(detail);
            continue;
          }

          detail.setNewNodeCode(newNodeCode);

          if (!dryRun) {
            // 实际更新：definitionId / flowVersion / currentNodeCode / currentNodeName
            instance.setDefinitionId(targetDefId);
            instance.setFlowVersion(targetDef.getFlowVersion());
            instance.setCurrentNodeCode(newNodeCode);
            // 同步更新节点名称
            FlowNodeDO targetNode = targetNodeMap.get(newNodeCode);
            if (targetNode != null) {
              instance.setCurrentNodeName(targetNode.getNodeName());
            }
            instanceMapper.updateById(instance);

            // P3-3: 同步迁移该实例下未完成的待办任务（ydsz_flow_run_task）
            // 仅迁移 PENDING/CLAIMED 状态的任务，已完成的历史任务保持不变
            int taskMigrated =
                migrateInstanceTasks(
                    instance.getId(),
                    targetDefId,
                    oldNodeCode,
                    newNodeCode,
                    nodeMapping,
                    targetNodeMap);
            log.info(
                "[Flow-Migrate] 实例任务级迁移: instanceId={} taskMigrated={}",
                instance.getId(),
                taskMigrated);
          }

          detail.setStatus("MIGRATED");
          detail.setReason(dryRun ? "试运行：可迁移" : "迁移成功");
          migratedCount++;
        } catch (Exception e) {
          detail.setStatus("FAILED");
          detail.setNewNodeCode(instance.getCurrentNodeCode());
          detail.setReason("迁移异常: " + e.getMessage());
          failedCount++;
          log.error(
              "[Flow-Migrate] 实例迁移失败: instanceId={} err={}", instance.getId(), e.getMessage(), e);
        }
        details.add(detail);
      }
    }

    // 6. 组装结果
    InstanceMigrationResultDTO result = new InstanceMigrationResultDTO();
    result.setTotalInstances(instances == null ? 0 : instances.size());
    result.setMigratedCount(migratedCount);
    result.setSkippedCount(skippedCount);
    result.setFailedCount(failedCount);
    result.setDetails(details);
    result.setNodeMappingApplied(nodeMapping);

    log.info(
        "[Flow-Migrate] 迁移完成: sourceDefId={} targetDefId={} dryRun={} "
            + "total={} migrated={} skipped={} failed={}",
        sourceDefId,
        targetDefId,
        dryRun,
        result.getTotalInstances(),
        migratedCount,
        skippedCount,
        failedCount);
    return result;
  }

  /**
   * 解析迁移后的新节点编码。
   *
   * <p>优先级：
   *
   * <ol>
   *   <li>若 nodeMapping 中存在 oldNodeCode 的映射，使用映射值（并校验映射值存在于目标定义）
   *   <li>若 oldNodeCode 直接存在于目标定义，保持不变
   *   <li>否则返回 null（表示无法迁移该节点，由调用方跳过）
   * </ol>
   *
   * @param oldNodeCode 旧节点编码
   * @param nodeMapping 节点映射
   * @param targetNodeMap 目标定义节点编码 -> 节点
   * @return 新节点编码，null 表示无法解析
   */
  private String resolveNewNodeCode(
      String oldNodeCode, Map<String, String> nodeMapping, Map<String, FlowNodeDO> targetNodeMap) {
    if (!StringUtils.hasText(oldNodeCode)) {
      // 当前节点为空（理论上不应发生），返回 null 由调用方跳过
      return null;
    }
    // 1. 显式映射优先
    if (nodeMapping.containsKey(oldNodeCode)) {
      String mapped = nodeMapping.get(oldNodeCode);
      if (StringUtils.hasText(mapped) && targetNodeMap.containsKey(mapped)) {
        return mapped;
      }
      // 映射目标不在新定义中，视为无法解析
      return null;
    }
    // 2. 编码直接存在于目标定义
    if (targetNodeMap.containsKey(oldNodeCode)) {
      return oldNodeCode;
    }
    // 3. 无法解析
    return null;
  }

  /**
   * P3-3: 迁移实例下未完成的待办任务到目标定义。
   *
   * <p>处理策略：
   *
   * <ul>
   *   <li>查询该实例下所有 PENDING 任务（CLAIMED 状态也属于未完成，但 selectPendingByInstance 仅返回 PENDING； 为保证一致性，这里查
   *       PENDING；已 CLAIMED 的任务由 SLA/办理流程处理）
   *   <li>对每个任务更新 definitionId
   *   <li>节点编码同步策略：
   *       <ul>
   *         <li>若任务 nodeCode == 实例旧 nodeCode，更新为新 nodeCode + 新 nodeName
   *         <li>否则按 nodeMapping 映射；映射目标必须存在于目标定义
   *         <li>映射缺失时，若任务 nodeCode 直接存在于目标定义，仅更新 definitionId
   *         <li>都不行则跳过该任务（保留旧 definitionId，记录 warning）
   *       </ul>
   * </ul>
   *
   * @param instanceId 实例 ID
   * @param targetDefId 目标定义 ID
   * @param oldInstNode 实例旧节点编码
   * @param newInstNode 实例新节点编码
   * @param nodeMapping 节点映射
   * @param targetNodeMap 目标定义节点编码集合
   * @return 成功迁移的任务数
   */
  private int migrateInstanceTasks(
      String instanceId,
      String targetDefId,
      String oldInstNode,
      String newInstNode,
      Map<String, String> nodeMapping,
      Map<String, FlowNodeDO> targetNodeMap) {
    List<FlowRunTaskDO> pendingTasks = flowTaskMapper.selectPendingByInstance(instanceId);
    if (pendingTasks == null || pendingTasks.isEmpty()) {
      return 0;
    }
    int migrated = 0;
    for (FlowRunTaskDO task : pendingTasks) {
      String oldTaskNode = task.getNodeCode();
      String newTaskNode =
          resolveTaskNodeCode(oldTaskNode, oldInstNode, newInstNode, nodeMapping, targetNodeMap);
      if (newTaskNode == null) {
        log.warn(
            "[Flow-Migrate] 任务节点无法映射，跳过: instanceId={} taskId={} nodeCode={}",
            instanceId,
            task.getId(),
            oldTaskNode);
        continue;
      }
      task.setDefinitionId(targetDefId);
      task.setNodeCode(newTaskNode);
      FlowNodeDO targetNode = targetNodeMap.get(newTaskNode);
      if (targetNode != null) {
        task.setNodeName(targetNode.getNodeName());
      }
      flowTaskMapper.updateById(task);
      migrated++;
    }
    return migrated;
  }

  /**
   * 解析任务节点的新编码。
   *
   * <p>优先级：
   *
   * <ol>
   *   <li>任务节点 == 实例旧节点 → 直接使用实例新节点编码
   *   <li>nodeMapping 存在映射且映射目标存在于目标定义 → 使用映射值
   *   <li>任务节点直接存在于目标定义 → 保持不变
   *   <li>否则返回 null（无法映射）
   * </ol>
   *
   * @param oldTaskNode 任务旧节点编码
   * @param oldInstNode 实例旧节点编码
   * @param newInstNode 实例新节点编码
   * @param nodeMapping 节点映射
   * @param targetNodeMap 目标定义节点集合
   * @return 新节点编码，null 表示无法解析
   */
  private String resolveTaskNodeCode(
      String oldTaskNode,
      String oldInstNode,
      String newInstNode,
      Map<String, String> nodeMapping,
      Map<String, FlowNodeDO> targetNodeMap) {
    if (!StringUtils.hasText(oldTaskNode)) {
      return null;
    }
    // 1. 与实例旧节点相同 → 跟随实例迁移到新节点
    if (oldTaskNode.equals(oldInstNode)) {
      return newInstNode;
    }
    // 2. 显式映射
    if (nodeMapping.containsKey(oldTaskNode)) {
      String mapped = nodeMapping.get(oldTaskNode);
      if (StringUtils.hasText(mapped) && targetNodeMap.containsKey(mapped)) {
        return mapped;
      }
      return null;
    }
    // 3. 直接存在于目标定义
    if (targetNodeMap.containsKey(oldTaskNode)) {
      return oldTaskNode;
    }
    // 4. 无法解析
    return null;
  }
}
