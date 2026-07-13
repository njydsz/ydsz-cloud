package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationResultDTO.MigrationDetail;
import com.njydsz.pmis.workflow.domain.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.instance.FlowInstanceStatus;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.service.FlowInstanceMigrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 流程实例迁移 Service 实现
 *
 * <p>当流程定义更新（新版本部署）后，将运行中实例迁移到新版本：
 * 更新 definitionId / flowVersion，并按节点映射调整 currentNodeCode。
 *
 * <p>P3-3 增强：实例迁移成功后同步更新该实例下未完成的待办任务（pmis_flow_run_task）的
 * definitionId / nodeCode / nodeName，避免迁移后待办任务仍指向旧定义导致办理异常。
 *
 * <p>注意：{@link #migrate(InstanceMigrationDTO)} 不加 {@code @Transactional}，
 * 以支持"逐实例防御式迁移"——单个实例失败不影响其他实例的已成功写入，
 * 失败明细记录在结果报告中，便于人工重试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceMigrationServiceImpl implements FlowInstanceMigrationService {

    /** 流程实例 Mapper，查询/更新待迁移的运行中实例 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程节点 Mapper，查询新旧版本节点映射关系 */
    private final FlowNodeMapper nodeMapper;
    /** 流程定义 Mapper，查询新版本定义信息 */
    private final FlowDefinitionMapper definitionMapper;
    /** 运行时任务 Mapper，迁移后同步更新待办任务的 definitionId 和 nodeCode */
    private final FlowRunTaskMapper flowTaskMapper;

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
            throw new SysException(StandardResultCode.BAD_REQUEST, "definitionId 不能为空");
        }
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        LambdaQueryWrapper<FlowInstanceDO> w = new LambdaQueryWrapper<>();
        w.eq(FlowInstanceDO::getDefinitionId, definitionId)
                .eq(FlowInstanceDO::getFlowStatus, FlowInstanceStatus.RUNNING.name())
                .eq(FlowInstanceDO::getTenantId, tid)
                .eq(FlowInstanceDO::getDeleted, 0);
        List<FlowInstanceDO> instances = instanceMapper.selectList(w);
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }
        return instances.stream()
                .map(i -> String.valueOf(i.getId()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> autoMapNodes(Long sourceDefId, Long targetDefId) {
        if (sourceDefId == null || targetDefId == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "sourceDefId/targetDefId 不能为空");
        }
        List<FlowNodeDO> sourceNodes = nodeMapper.selectByDefinitionId(String.valueOf(sourceDefId));
        List<FlowNodeDO> targetNodes = nodeMapper.selectByDefinitionId(String.valueOf(targetDefId));
        if (sourceNodes == null || targetNodes == null) {
            return Collections.emptyMap();
        }
        // 目标节点编码集合，便于快速判断
        Map<String, FlowNodeDO> targetNodeMap = targetNodes.stream()
                .collect(Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));
        Map<String, String> mapping = new LinkedHashMap<>();
        for (FlowNodeDO src : sourceNodes) {
            String code = src.getNodeCode();
            if (StringUtils.hasText(code) && targetNodeMap.containsKey(code)) {
                // 编码相同，自动配对
                mapping.put(code, code);
            }
        }
        log.info("[Flow-Migrate] 自动映射节点: sourceDefId={} targetDefId={} matched={}",
                sourceDefId, targetDefId, mapping.size());
        return mapping;
    }

    // ============================== 内部方法 ==============================

    /**
     * 迁移核心逻辑（实际执行 / 试运行统一入口）。
     *
     * <p>步骤：
     * <ol>
     *   <li>参数校验：源/目标定义 ID 非空、不相同、flowCode 一致</li>
     *   <li>预加载目标定义节点编码集合</li>
     *   <li>查询源定义下所有 RUNNING 实例</li>
     *   <li>逐实例迁移：更新 definitionId/flowVersion，按映射调整 currentNodeCode</li>
     *   <li>当前节点在新定义不存在且无映射时跳过；异常时记录失败</li>
     * </ol>
     *
     * @param dto      迁移参数
     * @param forceDry 是否强制试运行（previewMigration 调用时为 true）
     * @return 迁移结果报告
     */
    private InstanceMigrationResultDTO doMigrate(InstanceMigrationDTO dto, boolean forceDry) {
        // 1. 参数校验
        if (dto == null || dto.getSourceDefinitionId() == null
                || dto.getTargetDefinitionId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "sourceDefinitionId / targetDefinitionId 不能为空");
        }
        if (Objects.equals(dto.getSourceDefinitionId(), dto.getTargetDefinitionId())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "源定义与目标定义不能相同");
        }

        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun()) || forceDry;
        String sourceDefId = dto.getSourceDefinitionId();
        String targetDefId = dto.getTargetDefinitionId();
        String tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : AuthContext.getTenantIdOrDefault("1");
        Map<String, String> nodeMapping = dto.getNodeMapping() != null
                ? dto.getNodeMapping()
                : Collections.emptyMap();

        // 2. 校验源/目标定义存在且 flowCode 一致
        FlowDefinitionDO sourceDef = definitionMapper.selectById(sourceDefId);
        if (sourceDef == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "源流程定义不存在: " + sourceDefId);
        }
        FlowDefinitionDO targetDef = definitionMapper.selectById(targetDefId);
        if (targetDef == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "目标流程定义不存在: " + targetDefId);
        }
        if (!Objects.equals(sourceDef.getFlowCode(), targetDef.getFlowCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "源定义与目标定义 flowCode 不一致: source="
                            + sourceDef.getFlowCode() + " target=" + targetDef.getFlowCode());
        }

        // 3. 预加载目标定义的节点编码集合（用于判断当前节点是否存在于新版本）
        List<FlowNodeDO> targetNodes = nodeMapper.selectByDefinitionId(targetDefId);
        Map<String, FlowNodeDO> targetNodeMap = targetNodes == null
                ? Collections.emptyMap()
                : targetNodes.stream().collect(
                        Collectors.toMap(FlowNodeDO::getNodeCode, n -> n, (a, b) -> a));

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
                        detail.setReason("当前节点 [" + oldNodeCode
                                + "] 在目标定义中不存在且无映射，已跳过");
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

                        // P3-3: 同步迁移该实例下未完成的待办任务（pmis_flow_run_task）
                        // 仅迁移 PENDING/CLAIMED 状态的任务，已完成的历史任务保持不变
                        int taskMigrated = migrateInstanceTasks(
                                instance.getId(), targetDefId, oldNodeCode, newNodeCode,
                                nodeMapping, targetNodeMap);
                        log.info("[Flow-Migrate] 实例任务级迁移: instanceId={} taskMigrated={}",
                                instance.getId(), taskMigrated);
                    }

                    detail.setStatus("MIGRATED");
                    detail.setReason(dryRun ? "试运行：可迁移" : "迁移成功");
                    migratedCount++;
                } catch (Exception e) {
                    detail.setStatus("FAILED");
                    detail.setNewNodeCode(instance.getCurrentNodeCode());
                    detail.setReason("迁移异常: " + e.getMessage());
                    failedCount++;
                    log.error("[Flow-Migrate] 实例迁移失败: instanceId={} err={}",
                            instance.getId(), e.getMessage(), e);
                }
                details.add(detail);
            }
        }

        // 6. 组装结果
        InstanceMigrationResultDTO result = new InstanceMigrationResultDTO();
        BaseResponse.setTotalInstances(instances == null ? 0 : instances.size());
        BaseResponse.setMigratedCount(migratedCount);
        BaseResponse.setSkippedCount(skippedCount);
        BaseResponse.setFailedCount(failedCount);
        BaseResponse.setDetails(details);
        BaseResponse.setNodeMappingApplied(nodeMapping);

        log.info("[Flow-Migrate] 迁移完成: sourceDefId={} targetDefId={} dryRun={} "
                        + "total={} migrated={} skipped={} failed={}",
                sourceDefId, targetDefId, dryRun,
                BaseResponse.getTotalInstances(), migratedCount, skippedCount, failedCount);
        return result;
    }

    /**
     * 解析迁移后的新节点编码。
     *
     * <p>优先级：
     * <ol>
     *   <li>若 nodeMapping 中存在 oldNodeCode 的映射，使用映射值（并校验映射值存在于目标定义）</li>
     *   <li>若 oldNodeCode 直接存在于目标定义，保持不变</li>
     *   <li>否则返回 null（表示无法迁移该节点，由调用方跳过）</li>
     * </ol>
     *
     * @param oldNodeCode   旧节点编码
     * @param nodeMapping   节点映射
     * @param targetNodeMap 目标定义节点编码 -> 节点
     * @return 新节点编码，null 表示无法解析
     */
    private String resolveNewNodeCode(String oldNodeCode,
                                      Map<String, String> nodeMapping,
                                      Map<String, FlowNodeDO> targetNodeMap) {
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
     * <ul>
     *   <li>查询该实例下所有 PENDING 任务（CLAIMED 状态也属于未完成，但 selectPendingByInstance 仅返回 PENDING；
     *       为保证一致性，这里查 PENDING；已 CLAIMED 的任务由 SLA/办理流程处理）</li>
     *   <li>对每个任务更新 definitionId</li>
     *   <li>节点编码同步策略：
     *     <ul>
     *       <li>若任务 nodeCode == 实例旧 nodeCode，更新为新 nodeCode + 新 nodeName</li>
     *       <li>否则按 nodeMapping 映射；映射目标必须存在于目标定义</li>
     *       <li>映射缺失时，若任务 nodeCode 直接存在于目标定义，仅更新 definitionId</li>
     *       <li>都不行则跳过该任务（保留旧 definitionId，记录 warning）</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param instanceId    实例 ID
     * @param targetDefId   目标定义 ID
     * @param oldInstNode   实例旧节点编码
     * @param newInstNode   实例新节点编码
     * @param nodeMapping   节点映射
     * @param targetNodeMap 目标定义节点编码集合
     * @return 成功迁移的任务数
     */
    private int migrateInstanceTasks(String instanceId, String targetDefId,
                                     String oldInstNode, String newInstNode,
                                     Map<String, String> nodeMapping,
                                     Map<String, FlowNodeDO> targetNodeMap) {
        List<FlowRunTaskDO> pendingTasks = flowTaskMapper.selectPendingByInstance(instanceId);
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            return 0;
        }
        int migrated = 0;
        for (FlowRunTaskDO task : pendingTasks) {
            String oldTaskNode = task.getNodeCode();
            String newTaskNode = resolveTaskNodeCode(oldTaskNode, oldInstNode, newInstNode,
                    nodeMapping, targetNodeMap);
            if (newTaskNode == null) {
                log.warn("[Flow-Migrate] 任务节点无法映射，跳过: instanceId={} taskId={} nodeCode={}",
                        instanceId, task.getId(), oldTaskNode);
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
     * <ol>
     *   <li>任务节点 == 实例旧节点 → 直接使用实例新节点编码</li>
     *   <li>nodeMapping 存在映射且映射目标存在于目标定义 → 使用映射值</li>
     *   <li>任务节点直接存在于目标定义 → 保持不变</li>
     *   <li>否则返回 null（无法映射）</li>
     * </ol>
     *
     * @param oldTaskNode   任务旧节点编码
     * @param oldInstNode   实例旧节点编码
     * @param newInstNode   实例新节点编码
     * @param nodeMapping   节点映射
     * @param targetNodeMap 目标定义节点集合
     * @return 新节点编码，null 表示无法解析
     */
    private String resolveTaskNodeCode(String oldTaskNode, String oldInstNode, String newInstNode,
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
