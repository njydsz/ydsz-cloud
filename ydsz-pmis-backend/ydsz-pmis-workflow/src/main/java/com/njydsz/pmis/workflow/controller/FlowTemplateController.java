package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.service.FlowTemplateService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程模板市场 HTTP API
 *
 * <p>提供流程模板的查询、导入、导出能力。
 * 预置 15 套行业审批流程模板，支持按分类筛选、查看详情（含 BPMN XML）、
 * 一键导入为草稿流程定义，以及将已发布流程导出为模板。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "流程模板市场")
@RestController
@RequestMapping("/workflow/template")
@RequiredArgsConstructor
@Validated
public class FlowTemplateController {

    private final FlowTemplateService templateService;

    /**
     * 模板列表
     *
     * @param category 模板分类（HR/FINANCE/ADMIN/PROJECT），为空则返回全部
     * @return 模板列表（含 templateCode / templateName / category / description / icon / useCount / formPath）
     */
    @Operation(summary = "模板列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return Result.ok(templateService.listTemplates(category));
    }

    /**
     * 模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情，含完整的 BPMN 2.0 XML 流程定义
     */
    @Operation(summary = "模板详情（含 BPMN XML）")
    @GetMapping("/{templateCode}")
    public Result<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
        return Result.ok(templateService.getTemplate(templateCode));
    }

    /**
     * 导入模板 — 从模板市场导入手创建流程定义
     *
     * <p>读取模板的 BPMN XML，通过 FlowDefinitionService.deploy 部署为草稿流程定义。
     * 导入成功后自动增加模板的 use_count。
     *
     * @param templateCode 模板编码
     * @param flowName     自定义流程名称（可选，为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    @Operation(summary = "导入模板")
    @PostMapping("/{templateCode}/import")
    public Result<Long> importTemplate(@PathVariable String templateCode,
                                       @RequestParam(required = false) String flowName) {
        return Result.ok(templateService.importTemplate(templateCode, flowName));
    }

    /**
     * 导出为模板 — 将已发布流程定义导出到模板市场
     *
     * <p>读取流程定义的节点/跳转数据，生成对应的 BPMN XML 并存入 pmis_flow_template 表。
     * 若模板编码已存在则更新，否则新建。
     *
     * @param definitionId 流程定义 ID
     * @param templateName 模板名称
     * @param category     模板分类（HR/FINANCE/ADMIN/PROJECT/GENERAL）
     * @return 操作结果
     */
    @Operation(summary = "导出为模板")
    @PostMapping("/export/{definitionId}")
    public Result<Void> exportAsTemplate(@PathVariable @Min(1) Long definitionId,
                                         @RequestParam String templateName,
                                         @RequestParam(required = false, defaultValue = "GENERAL") String category) {
        templateService.exportAsTemplate(definitionId, templateName, category);
        return Result.ok();
    }
}