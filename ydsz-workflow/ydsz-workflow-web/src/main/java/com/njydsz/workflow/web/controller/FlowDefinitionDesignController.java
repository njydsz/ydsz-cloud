package com.njydsz.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.dto.FlowDefinitionSimulateDTO;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowInstanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
/**
 * 流程定义设计与模拟 Controller
 *
 * <p>提供流程定义的设计器交互 / 草稿编辑 / 导入导出 / 模拟运行 / 变更影响分析等 REST 接口，
 * 是前端 bpmn-js 设计器与运维变更评估的核心入口。
 *
 * <p><b>业务背景：</b>对标 Activiti / Flowable 的流程设计器与模拟运行能力。
 * 设计器通过节点坐标保存实现布局持久化；导入导出支持 JSON 格式的定义全量迁移；
 * 模拟运行使用虚拟变量驱动引擎走一遍流程，不创建实际实例，用于发布前验证流程正确性。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>设计器</b>：{@code POST /definition/{definitionId}/node/{nodeCode}/coordinate}（更新节点坐标） /
 *       {@code PUT /definition/{id}}（编辑未发布草稿）</li>
 *   <li><b>导入导出</b>：{@code GET /definition/{id}/export}（JSON 导出） /
 *       {@code POST /definition/import}（JSON 导入，创建为草稿）</li>
 *   <li><b>模拟</b>：{@code POST /definition/simulate}（节点级模拟执行，不创建实例）</li>
 *   <li><b>变更影响分析</b>：{@code GET /definition/migrationImpact}（评估版本升级对在途实例的影响）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>设计类接口通过 {@link AuthApiPermission} 校验
 * {@link PermissionCodes#WORKFLOW_DEFINITION_DESIGN} 权限码；
 * 导入接口通过 {@link PermissionCodes#WORKFLOW_DEFINITION_IMPORT} 权限码控制；
 * 模拟接口通过 {@link PermissionCodes#WORKFLOW_DEFINITION_DEPLOY} 权限码控制。
 *
 * <p><b>限流与幂等：</b>设计类写接口通过 {@link RateLimit} 限流（50 QPS），
 * 节点坐标更新 / 草稿编辑 / 导入通过 {@link Idempotent} 保证「同一请求 5s 内只执行一次」；
 * 模拟接口通过 {@link IdempotentExempt} 豁免幂等（查询语义）。
 *
 * <p><b>拆分说明：</b>本类从原 {@code FlowDefinitionController} 拆分而来，仅保留设计 / 导入导出 / 模拟类接口。
 * 部署 / 发布 / 查询类接口见 {@link FlowDefinitionController}；
 * 版本生命周期管理类接口见 {@link FlowDefinitionVersionController}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDefinitionService 流程定义服务
 * @see FlowInstanceService 流程实例服务（模拟接口使用）
 * @see FlowDefinitionController 部署 / 发布 / 查询接口
 * @see FlowDefinitionVersionController 版本生命周期管理接口
 */
@Slf4j
@RestController
@Tag(name = "workflow-definition-design", description = "工作流流程定义设计与模拟接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDefinitionDesignController {

    /** 流程定义服务 */
    private final FlowDefinitionService definitionService;
    /** 流程实例服务（simulate 接口调用） */
    private final FlowInstanceService instanceService;

    /**
     * P2-40: 更新节点坐标（供前端设计器保存布局）
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param coordinate   坐标 JSON 字符串
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:updateNodeCoordinate:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdefinition.updateNodeCoordinate", threshold = 50)
    @PostMapping("/definition/{definitionId}/node/{nodeCode}/coordinate")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'updateNodeCoordinate'")
    @Operation(summary = "更新流程节点坐标")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Void> updateNodeCoordinate(@PathVariable String definitionId,
                                             @PathVariable String nodeCode,
                                             @RequestBody String coordinate) {
        definitionService.updateNodeCoordinate(definitionId, nodeCode, coordinate);
        return BaseResponse.success();
    }

    /**
     * P2-41: 编辑未发布的流程定义草稿
     *
     * @param id  流程定义 ID
     * @param dto 部署参数（含更新后的元数据与节点/跳转）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:updateDefinition:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdefinition.updateDefinition", threshold = 50)
    @PutMapping("/definition/{id}")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'updateDefinition'")
    @Operation(summary = "编辑未发布的流程定义草稿")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Void> updateDefinition(@PathVariable String id,
                                         @Valid @RequestBody FlowDeployProcessDTO dto) {
        definitionService.updateDefinition(id, dto);
        return BaseResponse.success();
    }

    /**
     * GAP-V2-06: 导出流程定义为 JSON（含定义元数据 + 节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 JSON 字符串
     */
    @GetMapping("/definition/{id}/export")
    @Operation(summary = "导出流程定义为 JSON")
    public BaseResponse<String> exportDefinition(@PathVariable String id) {
        return BaseResponse.success(definitionService.exportDefinition(id));
    }

    /**
     * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
     *
     * @param json     导出的 JSON 字符串
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 统一响应结果，包含新创建的流程定义 ID
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:importDefinition:lock", ttlSeconds = 5)
    @PostMapping("/definition/import")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.IMPORT, content = "'importDefinition'")
    @Operation(summary = "从 JSON 导入流程定义")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_IMPORT)
    public BaseResponse<String> importDefinition(@RequestBody String json,
                                         @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(definitionService.importDefinition(json, tid));
    }

    /**
     * GAP-V2-08: 流程模拟运行 — 使用模拟变量驱动引擎走一遍流程，不创建实际实例
     *
     * <p>P1-10: 由原 Map body + RequestParam 改造为 {@link FlowDefinitionSimulateDTO} 强类型 DTO。
     *
     * @param dto 模拟参数（flowCode / variables / version）
     * @return 统一响应结果，包含模拟路径列表
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/definition/simulate")
    @Operation(summary = "流程模拟运行")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public BaseResponse<List<Map<String, Object>>> simulate(@Valid @RequestBody FlowDefinitionSimulateDTO dto) {
        String tid = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(instanceService.simulate(dto.getFlowCode(),
                String.valueOf(dto.getVersion()), dto.getVariables(), tid));
    }

    /**
     * P2-5: 变更影响分析报告 — 评估老版本定义升级到新版本对在途实例的影响。
     *
     * <p>对标 Activiti/Flowable 的"流程定义升级影响分析"：
     * <ul>
     *   <li>对比两个版本的节点 / 跳转差异</li>
     *   <li>统计老版本在途实例数 + 按当前节点分组分布</li>
     *   <li>识别卡死节点（HIGH 风险）和受影响节点（MEDIUM 风险）</li>
     *   <li>输出整体风险等级（HIGH / MEDIUM / LOW / NONE）与迁移建议</li>
     * </ul>
     *
     * <p>典型用法：发布新版本前调用此接口评估影响，根据 riskLevel 决定发布策略。
     *
     * @param oldDefinitionId 老版本流程定义 ID
     * @param newDefinitionId 新版本流程定义 ID
     * @return 统一响应结果，包含完整的影响分析报告
     */
    @GetMapping("/definition/migrationImpact")
    @Operation(summary = "变更影响分析报告（评估版本升级对在途实例的影响）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Map<String, Object>> analyzeMigrationImpact(
            @RequestParam String oldDefinitionId,
            @RequestParam String newDefinitionId) {
        return BaseResponse.success(definitionService.analyzeMigrationImpact(oldDefinitionId, newDefinitionId));
    }
}
