package com.njydsz.pmis.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;
import com.njydsz.pmis.workflow.server.service.FlowTemplateRecommendService;
import com.njydsz.pmis.workflow.server.service.FlowTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程模板市场 HTTP API
 *
 * <p>提供流程模板的查询、导入、导出能力。
 * 预置 15 套行业审批流程模板，支持按分类筛选、查看详情（含 BPMN XML）、
 * 一键导入为草稿流程定义，以及将已发布流程导出为模板。
 *
 * <p>P2-9: 模板继承与版本化能力
 * <ul>
 *   <li>{@code GET /{templateCode}/versions} 列出全部历史版本</li>
 *   <li>{@code GET /{templateCode}/versions/{version}} 获取指定版本详情</li>
 *   <li>{@code POST /{templateCode}/new-version} 创建新版本</li>
 *   <li>{@code POST /{templateCode}/clone} 克隆为独立新模板</li>
 *   <li>{@code POST /{parentTemplateCode}/inherit} 从父模板继承创建子模板</li>
 *   <li>{@code GET /{parentTemplateCode}/inherited} 列出继承自指定父模板的子模板</li>
 * </ul>
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

    /** 流程模板服务，负责模板查询、导入、导出与版本管理 */
    private final FlowTemplateService templateService;
    /** P2-2: 模板智能推荐服务 */
    private final FlowTemplateRecommendService recommendService;

    /**
     * 模板列表
     *
     * @param category 模板分类（HR/FINANCE/ADMIN/PROJECT），为空则返回全部
     * @return 模板列表（含 templateCode / templateName / category / description / icon / useCount / formPath）
     */
    @Operation(summary = "模板列表")
    @GetMapping("/list")
    public BaseResponse<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return BaseResponse.ok(templateService.listTemplates(category));
    }

    /**
     * 模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情，含完整的 BPMN 2.0 XML 流程定义
     */
    @Operation(summary = "模板详情（含 BPMN XML）")
    @GetMapping("/{templateCode}")
    public BaseResponse<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
        return BaseResponse.ok(templateService.getTemplate(templateCode));
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
    @Idempotent(key = "flowTemplate:importTemplate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{templateCode}/import")
    public BaseResponse<String> importTemplate(@PathVariable String templateCode,
                                          @RequestParam(required = false) String flowName) {
        return BaseResponse.ok(templateService.importTemplate(templateCode, flowName));
    }

    /**
     * 导出为模板 — 将已发布流程定义导出到模板市场
     *
     * <p>读取流程定义的节点/跳转数据，生成对应的 BPMN XML 并存入 pmis_flow_template 表。
     * P2-9: 若模板编码已存在则创建新版本（旧版本降级为 is_latest=0），否则新建。
     *
     * @param definitionId 流程定义 ID
     * @param templateName 模板名称
     * @param category     模板分类（HR/FINANCE/ADMIN/PROJECT/GENERAL）
     * @return 操作结果
     */
    @Operation(summary = "导出为模板")
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/export/{definitionId}")
    public BaseResponse<Void> exportAsTemplate(@PathVariable String definitionId,
                                         @RequestParam String templateName,
                                         @RequestParam(required = false, defaultValue = "GENERAL") String category) {
        templateService.exportAsTemplate(definitionId, templateName, category);
        return BaseResponse.ok();
    }

    // ============================== P2-9: 模板继承与版本化 ==============================

    /**
     * P2-9: 列出模板的全部历史版本。
     *
     * @param templateCode 模板编码
     * @return 版本列表（按版本号降序）
     */
    @Operation(summary = "P2-9: 列出模板全部版本")
    @GetMapping("/{templateCode}/versions")
    public BaseResponse<List<Map<String, Object>>> listTemplateVersions(@PathVariable String templateCode) {
        return BaseResponse.ok(templateService.listTemplateVersions(templateCode));
    }

    /**
     * P2-9: 获取指定版本的模板详情。
     *
     * @param templateCode 模板编码
     * @param version      版本号（从 1 开始）
     * @return 模板详情，含完整 BPMN XML 与版本元信息
     */
    @Operation(summary = "P2-9: 获取指定版本模板详情")
    @GetMapping("/{templateCode}/versions/{version}")
    public BaseResponse<Map<String, Object>> getTemplateVersion(@PathVariable String templateCode,
                                                          @PathVariable Integer version) {
        return BaseResponse.ok(templateService.getTemplateVersion(templateCode, version));
    }

    /**
     * P2-9: 创建模板新版本。
     *
     * <p>复制当前最新版本的内容为新版本，旧版本自动降级为 is_latest=0。
     *
     * @param templateCode 模板编码
     * @param versionLabel 版本标签（可空，如 v2.0-rc1）
     * @return 新版本号
     */
    @Operation(summary = "P2-9: 创建模板新版本")
    @Idempotent(key = "flowTemplate:createNewVersion", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{templateCode}/newVersion")
    public BaseResponse<Integer> createNewVersion(@PathVariable String templateCode,
                                            @RequestParam(required = false) String versionLabel) {
        return BaseResponse.ok(templateService.createNewVersion(templateCode, versionLabel));
    }

    /**
     * P2-9: 克隆模板为独立新模板。
     *
     * <p>复制源模板（最新版本）的全部内容到新 template_code，新模板独立演进。
     *
     * @param templateCode 源模板编码
     * @param newTemplateCode 新模板编码（必须不存在）
     * @param newTemplateName 新模板名称
     * @param newCategory     新模板分类（可空，默认沿用源模板分类）
     * @return 新模板编码
     */
    @Operation(summary = "P2-9: 克隆模板为独立新模板")
    @Idempotent(key = "flowTemplate:cloneTemplate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{templateCode}/clone")
    public BaseResponse<String> cloneTemplate(@PathVariable String templateCode,
                                        @RequestParam String newTemplateCode,
                                        @RequestParam String newTemplateName,
                                        @RequestParam(required = false) String newCategory) {
        return BaseResponse.ok(templateService.cloneTemplate(templateCode, newTemplateCode,
                newTemplateName, newCategory));
    }

    /**
     * P2-9: 从父模板继承创建子模板。
     *
     * <p>复制父模板（最新版本）的全部内容到新 template_code，新模板保留 parent_template_id 关联。
     *
     * @param parentTemplateCode 父模板编码
     * @param newTemplateCode    新模板编码（必须不存在）
     * @param newTemplateName    新模板名称
     * @param newCategory        新模板分类（可空，默认沿用父模板分类）
     * @return 新模板编码
     */
    @Operation(summary = "P2-9: 从父模板继承创建子模板")
    @Idempotent(key = "flowTemplate:inheritFromParent", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{parentTemplateCode}/inherit")
    public BaseResponse<String> inheritFromParent(@PathVariable String parentTemplateCode,
                                            @RequestParam String newTemplateCode,
                                            @RequestParam String newTemplateName,
                                            @RequestParam(required = false) String newCategory) {
        return BaseResponse.ok(templateService.inheritFromParent(parentTemplateCode, newTemplateCode,
                newTemplateName, newCategory));
    }

    /**
     * P2-9: 列出继承自指定父模板的所有子模板。
     *
     * @param parentTemplateCode 父模板编码
     * @return 子模板列表（仅最新版本）
     */
    @Operation(summary = "P2-9: 列出继承自指定父模板的子模板")
    @GetMapping("/{parentTemplateCode}/inherited")
    public BaseResponse<List<Map<String, Object>>> listInheritedTemplates(
            @PathVariable String parentTemplateCode) {
        return BaseResponse.ok(templateService.listInheritedTemplates(parentTemplateCode));
    }

    /**
     * P2-9: 将子模板的内容同步为父模板最新版本。
     *
     * <p>仅 INHERIT 类型子模板可同步；CLONE 类型因语义为独立演进，不支持同步。
     * 同步后将在子模板 template_code 下创建新版本，旧版本自动降级。
     *
     * @param childTemplateCode 子模板编码（必须为 INHERIT 类型）
     * @return 同步后的新版本号
     */
    @Operation(summary = "P2-9: 子模板同步父模板最新版本")
    @Idempotent(key = "flowTemplate:syncFromParent", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{childTemplateCode}/sync")
    public BaseResponse<Integer> syncFromParent(@PathVariable String childTemplateCode) {
        return BaseResponse.ok(templateService.syncFromParent(childTemplateCode));
    }

    // ============================== P2-2: 模板智能推荐 ==============================

    /**
     * P2-2: 智能推荐模板列表。
     *
     * <p>基于用户历史发起记录 + 模板热度 + 业务类型匹配，
     * 为当前用户推荐最可能需要的审批模板。
     *
     * @param topN 推荐数量（默认 5，上限 10）
     * @return 推荐模板列表
     */
    @Operation(summary = "P2-2: 智能推荐模板")
    @GetMapping("/recommend")
    public BaseResponse<List<Map<String, Object>>> recommend(
            @RequestParam(defaultValue = "5") int topN) {
        String userId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(recommendService.recommendTemplates(userId, tenantId, topN));
    }

    /**
     * P2-2: 基于业务类型推荐模板。
     *
     * @param businessType 业务类型
     * @param topN         推荐数量（默认 5）
     * @return 推荐模板列表
     */
    @Operation(summary = "P2-2: 按业务类型推荐模板")
    @GetMapping("/recommend/byBusinessType")
    public BaseResponse<List<Map<String, Object>>> recommendByBusinessType(
            @RequestParam String businessType,
            @RequestParam(defaultValue = "5") int topN) {
        String userId = AuthContext.getUserId();
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.ok(recommendService.recommendByBusinessType(userId, tenantId, businessType, topN));
    }
}
