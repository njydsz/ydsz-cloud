package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowDefinitionSimulateDTO;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/api/v1/workflow/engine")
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
    @PostMapping("/definition/deploy")
    @Operation(summary = "部署流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<Long> deploy(@Valid @RequestBody FlowDeployProcessDTO dto) {
        Long id = definitionService.deploy(dto);
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
    @PostMapping(value = "/definition/batch-deploy-zip", consumes = "multipart/form-data")
    @Operation(summary = "BPMN 部署包 .zip 批量导入")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<Map<String, Object>> batchDeployFromZip(
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("zip 文件不能为空");
        }
        try {
            return Result.ok(definitionService.batchDeployFromZip(file.getBytes(), null));
        } catch (java.io.IOException e) {
            return Result.fail("读取 zip 文件失败: " + e.getMessage());
        }
    }

    /**
     * 发布流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/publish")
    @Operation(summary = "发布流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> publish(@PathVariable @Min(1) Long id) {
        definitionService.publish(id);
        return Result.ok();
    }

    /**
     * 废弃流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/deprecate")
    @Operation(summary = "废弃流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> deprecate(@PathVariable @Min(1) Long id) {
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
                                          @RequestParam(required = false) Long tenantId) {
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
    public Result<Map<String, Object>> getDefinitionDetail(@PathVariable @Min(1) Long id) {
        return Result.ok(definitionService.getDetail(id));
    }

    /**
     * P2-27: 切换流程定义的激活版本
     *
     * @param code         流程编码
     * @param definitionId 目标流程定义 ID
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{code}/switchVersion")
    @Operation(summary = "切换流程定义的激活版本")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> switchVersion(@PathVariable String code,
                                      @RequestParam Long definitionId,
                                      @RequestParam(required = false) Long tenantId) {
        definitionService.switchActiveVersion(code, definitionId, tenantId);
        return Result.ok();
    }

    /**
     * P2-28: 启用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/enable")
    @Operation(summary = "启用流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> enable(@PathVariable @Min(1) Long id) {
        definitionService.enable(id);
        return Result.ok();
    }

    /**
     * P2-28: 停用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/disable")
    @Operation(summary = "停用流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public Result<Void> disable(@PathVariable @Min(1) Long id) {
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
    @PostMapping("/definition/{definitionId}/node/{nodeCode}/coordinate")
    @Operation(summary = "更新流程节点坐标")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> updateNodeCoordinate(@PathVariable @Min(1) Long definitionId,
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
    @PutMapping("/definition/{id}")
    @Operation(summary = "编辑未发布的流程定义草稿")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> updateDefinition(@PathVariable @Min(1) Long id,
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
    public Result<String> exportDefinition(@PathVariable @Min(1) Long id) {
        return Result.ok(definitionService.exportDefinition(id));
    }

    /**
     * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
     *
     * @param json     导出的 JSON 字符串
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 统一响应结果，包含新创建的流程定义 ID
     */
    @PostMapping("/definition/import")
    @Operation(summary = "从 JSON 导入流程定义")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_IMPORT)
    public Result<Long> importDefinition(@RequestBody String json,
                                         @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
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
    public Result<List<Map<String, Object>>> listVersions(@PathVariable @Min(1) Long id) {
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
    public Result<Map<String, Object>> diffVersions(@PathVariable @Min(1) Long id,
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
    @PostMapping("/definition/simulate")
    @Operation(summary = "流程模拟运行")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public Result<List<Map<String, Object>>> simulate(@Valid @RequestBody FlowDefinitionSimulateDTO dto) {
        Long tid = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(instanceService.simulate(dto.getFlowCode(),
                String.valueOf(dto.getVersion()), dto.getVariables(), tid));
    }
}
