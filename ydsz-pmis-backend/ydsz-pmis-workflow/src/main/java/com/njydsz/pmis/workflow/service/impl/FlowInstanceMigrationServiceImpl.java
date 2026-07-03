package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.dto.InstanceMigrationResultDTO;
import com.njydsz.pmis.workflow.dto.InstanceMigrationResultDTO.MigrationDetail;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.service.FlowInstanceMigrationService;
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

    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowDefinitionMapper definitionMapper;

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
    public List<String> findRunningInstances(Long definitionId, Long tenantId) {
        if (definitionId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "definitionId 不能为空");
        }
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "sourceDefId/targetDefId 不能为空");
        }
        List<FlowNodeDO> sourceNodes = nodeMapper.selectByDefinitionId(sourceDefId);
        List<FlowNodeDO> targetNodes = nodeMapper.selectByDefinitionId(targetDefId);
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
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "sourceDefinitionId / targetDefinitionId 不能为空");
        }
        if (Objects.equals(dto.getSourceDefinitionId(), dto.getTargetDefinitionId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "源定义与目标定义不能相同");
        }

        boolean dryRun = Boolean.TRUE.equals(dto.getDryRun()) || forceDry;
        Long sourceDefId = dto.getSourceDefinitionId();
        Long targetDefId = dto.getTargetDefinitionId();
        Long tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : SecurityContext.getTenantIdOrDefault(1L);
        Map<String, String> nodeMapping = dto.getNodeMapping() != null
                ? dto.getNodeMapping()
                : Collections.emptyMap();

        // 2. 校验源/目标定义存在且 flowCode 一致
        FlowDefinitionDO sourceDef = definitionMapper.selectById(sourceDefId);
        if (sourceDef == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "源流程定义不存在: " + sourceDefId);
        }
        FlowDefinitionDO targetDef = definitionMapper.selectById(targetDefId);
        if (targetDef == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "目标流程定义不存在: " + targetDefId);
        }
        if (!Objects.equals(sourceDef.getFlowCode(), targetDef.getFlowCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
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
                        instance.setFlowVersion(targetDef.getVersion());
                        instance.setCurrentNodeCode(newNodeCode);
                        // 同步更新节点名称
                        FlowNodeDO targetNode = targetNodeMap.get(newNodeCode);
                        if (targetNode != null) {
                            instance.setCurrentNodeName(targetNode.getNodeName());
                        }
                        instanceMapper.updateById(instance);
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
        result.setTotalInstances(instances == null ? 0 : instances.size());
        result.setMigratedCount(migratedCount);
        result.setSkippedCount(skippedCount);
        result.setFailedCount(failedCount);
        result.setDetails(details);
        result.setNodeMappingApplied(nodeMapping);

        log.info("[Flow-Migrate] 迁移完成: sourceDefId={} targetDefId={} dryRun={} "
                        + "total={} migrated={} skipped={} failed={}",
                sourceDefId, targetDefId, dryRun,
                result.getTotalInstances(), migratedCount, skippedCount, failedCount);
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
}
