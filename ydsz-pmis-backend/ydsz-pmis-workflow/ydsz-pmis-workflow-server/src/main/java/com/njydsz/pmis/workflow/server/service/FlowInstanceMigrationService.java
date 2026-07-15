package com.njydsz.pmis.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationResultDTO;

/**
 * 流程实例迁移 Service
 *
 * <p>当流程定义更新（新版本部署）后，运行中的实例可能需要迁移到新版本。
 * 本服务负责实例的迁移、预览（试运行）、查询及节点自动映射。
 *
 * @since 1.0.0
 */
public interface FlowInstanceMigrationService {

    /**
     * 执行实例迁移。
     *
     * <p>将源定义下所有运行中实例迁移到目标定义：
     * <ol>
     *   <li>更新实例的 definitionId 与 flowVersion</li>
     *   <li>按 nodeMapping 映射当前节点编码</li>
     *   <li>当前节点在新定义中不存在且无映射时跳过该实例</li>
     * </ol>
     *
     * @param dto 迁移参数
     * @return 迁移结果报告
     */
    InstanceMigrationResultDTO migrate(InstanceMigrationDTO dto);

    /**
     * 预览迁移（试运行 / dry run）。
     *
     * <p>不实际更新数据库，仅模拟迁移并返回报告，便于评估迁移影响。
     *
     * @param dto 迁移参数
     * @return 迁移结果报告
     */
    InstanceMigrationResultDTO previewMigration(InstanceMigrationDTO dto);

    /**
     * 查询运行在指定旧定义上的实例 ID 列表。
     *
     * @param definitionId 流程定义 ID
     * @param tenantId     租户 ID（可选，默认从上下文获取）
     * @return 实例 ID 字符串列表
     */
    List<String> findRunningInstances(String definitionId, String tenantId);

    /**
     * 自动映射节点编码：对比源定义与目标定义的节点，按编码自动匹配。
     *
     * <p>编码相同的节点自动配对（旧编码 -> 新编码）。
     * 仅存在于源定义的节点不会出现在结果中，需人工指定映射。
     *
     * @param sourceDefId 源定义 ID
     * @param targetDefId 目标定义 ID
     * @return 旧节点编码 -> 新节点编码 的映射
     */
    Map<String, String> autoMapNodes(Long sourceDefId, Long targetDefId);
}
