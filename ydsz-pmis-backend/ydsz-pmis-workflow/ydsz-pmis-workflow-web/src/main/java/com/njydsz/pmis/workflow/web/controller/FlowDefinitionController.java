package com.njydsz.pmis.workflow.web.controller.definition;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.domain.dto.definition.FlowDefinitionSimulateDTO;
import com.njydsz.pmis.workflow.domain.dto.definition.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import com.njydsz.pmis.workflow.server.service.definition.FlowDefinitionService;
import com.njydsz.pmis.workflow.server.service.instance.FlowInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 流程定义管理 Controller
 *
 * <p>流程定义的部署 / 发布 / 查询 / 切换版本 / 导入导出 / 模拟运行
 * （P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-definition", description = "工作流流程定义接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDefinitionController {

    /** 流程定义服务 */
    private final FlowDefinitionService definitionService;
    /** 流程实例服务（simulate 接口调用） */
    private final FlowInstanceService instanceService;

    /**
     * 部署流程定义
     *
     * @param dto 流程部署参数
     * @return 统一响应结果，包含流程定义 ID
     */
    @Idempotent(key = "flowDefinition:deploy", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/deploy")
    @Operation(summary = "部署流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<String> deploy(@Valid @RequestBody FlowDeployProcessDTO dto) {
        String id = definitionService.deploy(dto);
        return Result.ok(id);
    }

    /**
     * GAP-P1-6: BPMN 部署包 .zip 批量导入流程定义。
     *
     * <p>对标 Activiti/Flowable 的 zip 部署能力。上传 .zip 文件，遍历其中的
     * {@code .bpmn} / {@code .bpmn20.xml} 文件逐个部署，单个失败不影响其他文件。
     *
     * @param file     zip 文件（multipart/form-data）
     * @return 统一响应结果，包含 successCount / failedItems
     */
    @Idempotent(key = "flowDefinition:batchDeployFromZip", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping(value = "/definition/batchDeployZip", consumes = "multipart/form-data")
    @Operation(summary = "BPMN 部署包 .zip 批量导入")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<Map<String, Object>> batchDeployFromZip(
            @RequestParam("file")
            org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("zip 文件不能为空");
        }
        try {
            return Result.ok(definitionService.batchDeployFromZip(file.getBytes(), null));
        } catch (IOException e) {
            return Result.fail("读取 zip 文件失败: " + e.getMessage());
        }
    }

    /**
     * 发布流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:publish", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/publish")
    @Operation(summary = "发布流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> publish(@PathVariable String id) {
        definitionService.publish(id);
        return Result.ok();
    }

    /**
     * 废弃流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:deprecate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/deprecate")
    @Operation(summary = "废弃流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> deprecate(@PathVariable String id) {
        definitionService.deprecate(id);
        return Result.ok();
    }

    /**
     * 按编码查询已发布流程定义
     *
     * @param code      流程编码
     * @param version   版本号（可选）
     * @param tenantId  租户 ID（可选）
     * @return 统一响应结果，包含流程定义
     */
    @GetMapping("/definition/code/{code}")
    @Operation(summary = "按编码查询已发布流程定义")
    public Result<FlowDefinitionDO> getByCode(@PathVariable String code,
                                          @RequestParam(required = false) String version,
                                          @RequestParam(required = false) String tenantId) {
        return Result.ok(definitionService.getPublished(code, version, tenantId));
    }

    /**
     * 分页查询流程定义
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param category 分类（可选）
     * @param flowCode 流程编码（可选）
     * @return 统一响应结果，包含流程定义列表
     */
    @GetMapping("/definition/page")
    @Operation(summary = "分页查询流程定义")
    public Result<List<FlowDefinitionDO>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNo,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String flowCode) {
        return Result.ok(definitionService.page(pageNo, pageSize, category, flowCode));
    }

    /**
     * P2-21: 流程定义详情查询（含节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 definition / nodes / skips
     */
    @GetMapping("/definition/{id}")
    @Operation(summary = "查询流程定义详情（含节点与跳转）")
    public Result<Map<String, Object>> getDefinitionDetail(@PathVariable String id) {
        return Result.ok(definitionService.getDetail(id));
    }

    /**
     * P2-8 (GAP-53): 流程定义预览 — 只读模式返回定义详情 + readOnly 标记
     *
     * <p>前端用 bpmn-js 以只读模式渲染（禁用编辑 palette），展示流程全貌。
     * 数据与 {@link #getDefinitionDetail} 一致，额外携带 {@code readOnly=true} 标志。
     */
    @GetMapping("/definition/{id}/preview")
    @Operation(summary = "流程定义预览（只读）")
    public Result<Map<String, Object>> getDefinitionPreview(@PathVariable String id) {
        Map<String, Object> detail = definitionService.getDetail(id);
        detail.put("readOnly", true);
        return Result.ok(detail);
    }

    /**
     * P2-27: 切换流程定义的激活版本
     *
     * @param code         流程编码
     * @param definitionId 目标流程定义 ID
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:switchVersion", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{code}/switchVersion")
    @Operation(summary = "切换流程定义的激活版本")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> switchVersion(@PathVariable String code,
                                      @RequestParam String definitionId,
                                      @RequestParam(required = false) String tenantId) {
        definitionService.switchActiveVersion(code, definitionId, tenantId);
        return Result.ok();
    }

    /**
     * P2-28: 启用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:enable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/enable")
    @Operation(summary = "启用流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> enable(@PathVariable String id) {
        definitionService.enable(id);
        return Result.ok();
    }

    /**
     * P2-28: 停用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:disable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/disable")
    @Operation(summary = "停用流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> disable(@PathVariable String id) {
        definitionService.disable(id);
        return Result.ok();
    }

    /**
     * P2-40: 更新节点坐标（供前端设计器保存布局）
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param coordinate   坐标 JSON 字符串
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:updateNodeCoordinate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{definitionId}/node/{nodeCode}/coordinate")
    @Operation(summary = "更新流程节点坐标")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> updateNodeCoordinate(@PathVariable String definitionId,
                                             @PathVariable String nodeCode,
                                             @RequestBody String coordinate) {
        definitionService.updateNodeCoordinate(definitionId, nodeCode, coordinate);
        return Result.ok();
    }

    /**
     * P2-41: 编辑未发布的流程定义草稿
     *
     * @param id  流程定义 ID
     * @param dto 部署参数（含更新后的元数据与节点/跳转）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDefinition:updateDefinition", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/definition/{id}")
    @Operation(summary = "编辑未发布的流程定义草稿")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> updateDefinition(@PathVariable String id,
                                         @Valid @RequestBody FlowDeployProcessDTO dto) {
        definitionService.updateDefinition(id, dto);
        return Result.ok();
    }

    /**
     * GAP-V2-06: 导出流程定义为 JSON（含定义元数据 + 节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 JSON 字符串
     */
    @GetMapping("/definition/{id}/export")
    @Operation(summary = "导出流程定义为 JSON")
    public Result<String> exportDefinition(@PathVariable String id) {
        return Result.ok(definitionService.exportDefinition(id));
    }

    /**
     * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
     *
     * @param json     导出的 JSON 字符串
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 统一响应结果，包含新创建的流程定义 ID
     */
    @Idempotent(key = "flowDefinition:importDefinition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/import")
    @Operation(summary = "从 JSON 导入流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_IMPORT)
    public Result<String> importDefinition(@RequestBody String json,
                                         @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(definitionService.importDefinition(json, tid));
    }

    /**
     * 列出流程定义的所有历史版本
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含版本列表
     */
    @GetMapping("/definition/{id}/versions")
    @Operation(summary = "列出流程定义的所有历史版本")
    public Result<List<Map<String, Object>>> listVersions(@PathVariable String id) {
        return Result.ok(definitionService.listVersions(id));
    }

    /**
     * 版本差异对比
     *
     * @param id 流程定义 ID
     * @param v1 版本号 1
     * @param v2 版本号 2
     * @return 统一响应结果，包含 nodeChanges 和 skipChanges
     */
    @GetMapping("/definition/{id}/diff")
    @Operation(summary = "流程定义版本差异对比")
    public Result<Map<String, Object>> diffVersions(@PathVariable String id,
                                                     @RequestParam Integer v1,
                                                     @RequestParam Integer v2) {
        return Result.ok(definitionService.diffVersions(id, v1, v2));
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
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<List<Map<String, Object>>> simulate(@Valid @RequestBody FlowDefinitionSimulateDTO dto) {
        String tid = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(instanceService.simulate(dto.getFlowCode(),
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
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Map<String, Object>> analyzeMigrationImpact(
            @RequestParam String oldDefinitionId,
            @RequestParam String newDefinitionId) {
        return Result.ok(definitionService.analyzeMigrationImpact(oldDefinitionId, newDefinitionId));
    }

    /**
     * P0-2: 流程定义一键回滚
     *
     * <p>将指定 flowCode 的激活版本切换回上一个已发布版本，
     * 并自动迁移在途实例。HIGH 风险时阻止回滚。
     *
     * @param flowCode 流程编码
     * @return 统一响应结果，包含回滚报告
     */
    @PostMapping("/definition/rollback")
    @Operation(summary = "一键回滚流程定义到上一版本")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Map<String, Object>> rollbackDefinition(
            @RequestParam String flowCode) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(definitionService.rollbackDefinition(flowCode, tenantId));
    }
}
