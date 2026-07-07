package com.njydsz.pmis.workflow.controller;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.workflow.dto.FlowDesignerDataDTO;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可视化流程设计器 / 表单 / SLA 配置 / 模板 Controller
 *
 * <p>GAP-V2-01/V2-02/P1-2/GAP-P2: 设计器数据、表单字段配置、节点 SLA 配置、流程模板库
 * （P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-designer", description = "工作流设计器/表单/SLA/模板接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDesignerController {

    /** 流程定义服务 */
    private final FlowDefinitionService definitionService;
    /** GAP-P2: 流程模板服务 */
    private final FlowTemplateService templateService;

    // ============== GAP-V2-01: 可视化流程设计器 API ==============

    /**
     * GAP-V2-01: 获取设计器数据 — 返回完整流程图（节点+边+坐标）
     *
     * @param id 流程定义 ID
     * @return 设计器数据（definition / nodes / edges）
     */
    @GetMapping("/definition/{id}/designer")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Map<String, Object>> getDesignerData(@PathVariable String id) {
        return Result.ok(definitionService.getDesignerData(id));
    }

    /**
     * GAP-V2-01: 批量保存设计器数据 — 一次性保存节点坐标 + 属性
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowDesignerDataDTO}，
     * designerData 为 JSON 字符串，控制器反序列化为 Map 后转交 service。
     *
     * @param id  流程定义 ID
     * @param dto 设计器数据 DTO（designerData 为 JSON 字符串，含 nodes + edges）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/designer")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> saveDesignerData(@PathVariable String id,
                                          @Valid @RequestBody FlowDesignerDataDTO dto) {
        Map<String, Object> designerData = JSON.parseObject(dto.getDesignerData());
        definitionService.saveDesignerData(id, designerData);
        return Result.ok();
    }

    // ============== GAP-V2-02: 表单引擎字段配置 ==============

    /**
     * GAP-V2-02: 获取节点表单字段配置
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return 字段权限 JSON 字符串
     */
    @GetMapping("/definition/{id}/form-config/{nodeCode}")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<String> getFormConfig(@PathVariable String id,
                                         @PathVariable String nodeCode) {
        return Result.ok(definitionService.getFormConfig(id, nodeCode));
    }

    /**
     * GAP-V2-02: 保存节点表单字段配置
     *
     * @param id              流程定义 ID
     * @param nodeCode        节点编码
     * @param formFieldsConfig 字段权限 JSON 字符串
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/form-config/{nodeCode}")
    @PrePermission(PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public Result<Void> saveFormConfig(@PathVariable String id,
                                        @PathVariable String nodeCode,
                                        @RequestBody String formFieldsConfig) {
        definitionService.saveFormConfig(id, nodeCode, formFieldsConfig);
        return Result.ok();
    }

    // ============== P1-2: 节点 SLA 配置 ==============

    /**
     * P1-2: 获取节点 SLA 配置（JSON 字符串）
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return SLA 配置 JSON（未配置返回 null）
     */
    @GetMapping("/definition/{id}/sla-config/{nodeCode}")
    @PrePermission(PermissionCodes.WORKFLOW_SLA_CONFIG)
    public Result<String> getSlaConfig(@PathVariable String id,
                                        @PathVariable String nodeCode) {
        return Result.ok(definitionService.getSlaConfig(id, nodeCode));
    }

    /**
     * P1-2: 保存节点 SLA 配置
     *
     * @param id         流程定义 ID
     * @param nodeCode   节点编码
     * @param slaConfig  SLA 配置（JSON 对象，由 controller 序列化为字符串存储）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/sla-config/{nodeCode}")
    @PrePermission(PermissionCodes.WORKFLOW_SLA_CONFIG)
    public Result<Void> saveSlaConfig(@PathVariable String id,
                                        @PathVariable String nodeCode,
                                        @RequestBody Map<String, Object> slaConfig) {
        String json = slaConfig == null ? null : JSON.toJSONString(slaConfig);
        definitionService.saveSlaConfig(id, nodeCode, json);
        return Result.ok();
    }

    // ============== GAP-P2: 流程模板库 ==============

    /**
     * GAP-P2: 列出所有可用模板
     *
     * @param category 模板分类（可选）
     * @return 模板列表
     */
    @GetMapping("/template/list")
    public Result<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return Result.ok(templateService.listTemplates(category));
    }

    /**
     * GAP-P2: 一键导入模板
     *
     * @param templateCode 模板编码
     * @param flowName     自定义流程名称（可选，为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    @PostMapping("/template/{templateCode}/import")
    @PrePermission(PermissionCodes.WORKFLOW_TEMPLATE_IMPORT)
    public Result<Long> importTemplate(@PathVariable String templateCode,
                                       @RequestParam(required = false) String flowName) {
        return Result.ok(templateService.importTemplate(templateCode, flowName));
    }

    /**
     * GAP-P2: 获取模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情
     */
    @GetMapping("/template/{templateCode}")
    public Result<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
        return Result.ok(templateService.getTemplate(templateCode));
    }
}
