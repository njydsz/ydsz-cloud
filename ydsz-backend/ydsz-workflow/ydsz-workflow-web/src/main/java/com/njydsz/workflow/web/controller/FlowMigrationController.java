package com.njydsz.workflow.web.controller.instance;

import java.util.Map;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.InstanceMigrationResultDTOVO;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 流程实例迁移 Controller（GAP-V2-09 / P1-10）
 *
 * <p>提供<b>运行中流程实例</b>从源定义版本迁移到目标定义版本的能力。典型场景：
 * 流程定义迭代升级后，存量运行中实例需要平滑迁移到新版本，避免「实例挂在旧版定义上
 * 但维护侧已停止维护旧版」的悬空状态。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/engine/instance/migrate/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>执行迁移</b>：{@code POST /instance/migrate} — 真实迁移运行中实例（含事务、审计、通知）</li>
 *   <li><b>预览迁移</b>：{@code POST /instance/migrate/preview} — dry run 模式，仅生成报告，不落库</li>
 *   <li><b>节点自动映射</b>：{@code GET /instance/migrate/autoMap} — 对比源/目标定义节点，按编码自动匹配</li>
 * </ul>
 *
 * <p><b>迁移流程：</b>
 * <ol>
 *   <li>读取源/目标定义（{@code sourceDefinitionId} / {@code targetDefinitionId}）</li>
 *   <li>校验节点兼容性（同编码 / 重命名 / 删除 / 新增）</li>
 *   <li>按 {@code nodeMapping}（旧→新）映射当前任务节点到新定义节点</li>
 *   <li>事务内更新 {@code ydsz_flow_instance} 与 {@code ydsz_flow_run_task} 的 definitionId 与 nodeCode</li>
 *   <li>触发 {@code ydsz_flow_audit_log} 写入（标记为「迁移」）</li>
 *   <li>异步通知当前待办人「流程已升级」</li>
 * </ol>
 *
 * <p><b>约束：</b>
 * <ul>
 *   <li>源与目标定义的 {@code flowCode} 必须相同</li>
 *   <li>仅迁移 RUNNING / SUSPENDED 状态实例（COMPLETED / REJECTED / TERMINATED 不迁移）</li>
 *   <li>同一批实例迁移粒度：建议 ≤ 200 条/批，避免长事务</li>
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传与幂等保护；
 * 节点兼容性校验、事务边界控制、报告生成下沉到 {@link FlowInstanceMigrationService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowInstanceMigrationService 实例迁移服务
 * @see InstanceMigrationDTO 迁移参数 DTO
 * @see InstanceMigrationResultDTO 迁移结果 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-migration", description = "工作流实例迁移接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowMigrationController {

    /** GAP-V2-09: 流程实例迁移服务（新版本部署后迁移运行中实例） */
    private final FlowInstanceMigrationService instanceMigrationService;

    /**
     * 执行实例迁移 — 将源定义下运行中实例迁移到目标定义
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>请求体 {@link InstanceMigrationDTO} 字段说明：
     * <ul>
     *   <li>{@code sourceDefinitionId} / {@code targetDefinitionId}：源/目标定义 ID（必填）</li>
     *   <li>{@code tenantId}：租户 ID（可选，默认从上下文获取）</li>
     *   <li>{@code nodeMapping}：旧节点编码 → 新节点编码 映射（可选，未传则按编码自动匹配）</li>
     *   <li>{@code dryRun}：是否试运行（可选，true 时仅模拟不落库）</li>
     * </ul>
     *
     * @param dto 迁移参数
     * @return 统一响应结果，包含迁移结果报告（成功数 / 失败数 / 失败明细 / 节点映射生效情况）
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @RateLimit(resource = "workflow.flowmigration.migrateInstances", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowMigrationController:migrateInstances:lock", ttlSeconds = 5)
    @PostMapping("/instance/migrate")
    @Audit(module = "流程迁移", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'migrateInstances'")
    public BaseResponse<InstanceMigrationResultDTOVO> migrateInstances(@RequestBody InstanceMigrationDTO dto) {
        return BaseResponse.success(WorkflowConverter.INSTANT.entityToVO(instanceMigrationService.migrate(dto)));
    }

    /**
     * 预览实例迁移（试运行 / dry run）
     *
     * <p>幂等保护 5 秒；限流 50 QPS。
     * <p>不实际更新数据库，仅返回迁移报告（哪些实例会迁移 / 哪些会失败 / 节点兼容性结论）。
     * <p>典型用途：上线前预演 / 灰度前评估影响面。
     *
     * @param dto 迁移参数（{@code dryRun} 字段将被忽略，强制为试运行）
     * @return 统一响应结果，包含迁移结果报告
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @RateLimit(resource = "workflow.flowmigration.previewMigration", threshold = 50)
    @Idempotent(key = "ydsz:workflow:FlowMigrationController:previewMigration:lock", ttlSeconds = 5)
    @PostMapping("/instance/migrate/preview")
    @Audit(module = "流程迁移", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'previewMigration'")
    public BaseResponse<InstanceMigrationResultDTOVO> previewMigration(@RequestBody InstanceMigrationDTO dto) {
        return BaseResponse.success(WorkflowConverter.INSTANT.entityToVO(instanceMigrationService.previewMigration(dto)));
    }

    /**
     * 自动映射节点编码 — 对比源/目标定义节点，按编码自动匹配
     *
     * <p>不写库，仅生成「旧节点编码 → 新节点编码」的映射建议。
     * <p>返回的映射可作为 {@link InstanceMigrationDTO#setNodeMapping(Map)} 的预填值；
     * 编码不同 / 节点被删除 / 新增节点的场景需人工补充映射。
     *
     * @param sourceDefinitionId 源定义 ID
     * @param targetDefinitionId 目标定义 ID
     * @return 统一响应结果，包含「旧节点编码 → 新节点编码」的映射
     */
    @GetMapping("/instance/migrate/autoMap")
    public BaseResponse<Map<String, String>> autoMapNodes(
            @RequestParam Long sourceDefinitionId,
            @RequestParam Long targetDefinitionId) {
        return BaseResponse.success(instanceMigrationService.autoMapNodes(sourceDefinitionId, targetDefinitionId));
    }
}
