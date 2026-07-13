package com.njydsz.pmis.workflow.web.controller.definition;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.workflow.domain.dto.FlowDesignerDataDTO;
import com.njydsz.pmis.workflow.server.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.server.service.FlowTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Map<String, Object>> getDesignerData(@PathVariable String id) {
        return BaseResponse.ok(definitionService.getDesignerData(id));
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
    @Idempotent(key = "flowDesigner:saveDesignerData", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/designer")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Void> saveDesignerData(@PathVariable String id,
                                          @Valid @RequestBody FlowDesignerDataDTO dto) {
        Map<String, Object> designerData = JSON.parseObject(dto.getDesignerData());
        definitionService.saveDesignerData(id, designerData);
        return BaseResponse.ok();
    }

    // ============== P2-4: 设计器协同编辑锁定 API ==============

    /**
     * P2-4: 加锁流程定义（设计器协同编辑）。
     *
     * <p>对标钉钉/飞书流程设计器"编辑锁定"：用户进入设计器编辑模式前调用此接口，
     * 成功获取锁后方可编辑；编辑过程中前端定期调用此接口续约（保持锁不过期）。
     *
     * <p>行为约定：
     * <ul>
     *   <li>未锁定 → 加锁成功，可进入编辑</li>
     *   <li>同一人持锁 → 续约成功（刷新 lockedAt）</li>
     *   <li>他人持锁且未超时 → 返回 409 冲突，前端展示"当前 {lockedBy} 正在编辑"</li>
     *   <li>他人持锁但已超时（默认 30 分钟）→ 抢占成功</li>
     * </ul>
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，true=加锁成功
     */
    @Idempotent(key = "flowDesigner:lockDefinition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/lock")
    @Operation(summary = "加锁流程定义（设计器协同编辑）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Boolean> lockDefinition(@PathVariable String id) {
        String userId = AuthContext.getUserId();
        return BaseResponse.ok(definitionService.lockDefinition(id, userId));
    }

    /**
     * P2-4: 解锁流程定义（设计器协同编辑）。
     *
     * <p>用户退出设计器编辑模式或页面卸载时调用，释放锁。仅持锁人本人可解锁。
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，true=解锁成功
     */
    @Idempotent(key = "flowDesigner:unlockDefinition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/unlock")
    @Operation(summary = "解锁流程定义（设计器协同编辑）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Boolean> unlockDefinition(@PathVariable String id) {
        String userId = AuthContext.getUserId();
        return BaseResponse.ok(definitionService.unlockDefinition(id, userId));
    }

    /**
     * P2-4: 查询流程定义的锁定状态。
     *
     * <p>用户进入设计器前调用，判断是否可编辑：
     * <ul>
     *   <li>{@code locked=false} → 可直接进入编辑并加锁</li>
     *   <li>{@code locked=true, lockedBy=当前用户} → 可继续编辑并续约</li>
     *   <li>{@code locked=true, lockedBy=他人, expired=false} → 只读模式，提示"正在被 XX 编辑"</li>
     *   <li>{@code locked=true, lockedBy=他人, expired=true} → 可强制抢占进入编辑</li>
     * </ul>
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 locked / lockedBy / lockedAt / expired
     */
    @GetMapping("/definition/{id}/lockStatus")
    @Operation(summary = "查询流程定义锁定状态")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Map<String, Object>> getLockStatus(@PathVariable String id) {
        return BaseResponse.ok(definitionService.getLockStatus(id));
    }

    // ============== GAP-V2-02: 表单引擎字段配置 ==============

    /**
     * GAP-V2-02: 获取节点表单字段配置
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return 字段权限 JSON 字符串
     */
    @GetMapping("/definition/{id}/formConfig/{nodeCode}")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<String> getFormConfig(@PathVariable String id,
                                         @PathVariable String nodeCode) {
        return BaseResponse.ok(definitionService.getFormConfig(id, nodeCode));
    }

    /**
     * GAP-V2-02: 保存节点表单字段配置
     *
     * @param id              流程定义 ID
     * @param nodeCode        节点编码
     * @param formFieldsConfig 字段权限 JSON 字符串
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDesigner:saveFormConfig", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/formConfig/{nodeCode}")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
    public BaseResponse<Void> saveFormConfig(@PathVariable String id,
                                        @PathVariable String nodeCode,
                                        @RequestBody String formFieldsConfig) {
        definitionService.saveFormConfig(id, nodeCode, formFieldsConfig);
        return BaseResponse.ok();
    }

    // ============== P1-2: 节点 SLA 配置 ==============

    /**
     * P1-2: 获取节点 SLA 配置（JSON 字符串）
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return SLA 配置 JSON（未配置返回 null）
     */
    @GetMapping("/definition/{id}/slaConfig/{nodeCode}")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
    public BaseResponse<String> getSlaConfig(@PathVariable String id,
                                        @PathVariable String nodeCode) {
        return BaseResponse.ok(definitionService.getSlaConfig(id, nodeCode));
    }

    /**
     * P1-2: 保存节点 SLA 配置
     *
     * @param id         流程定义 ID
     * @param nodeCode   节点编码
     * @param slaConfig  SLA 配置（JSON 对象，由 controller 序列化为字符串存储）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDesigner:saveSlaConfig", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/slaConfig/{nodeCode}")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
    public BaseResponse<Void> saveSlaConfig(@PathVariable String id,
                                        @PathVariable String nodeCode,
                                        @RequestBody Map<String, Object> slaConfig) {
        String json = slaConfig == null ? null : JSON.toJSONString(slaConfig);
        definitionService.saveSlaConfig(id, nodeCode, json);
        return BaseResponse.ok();
    }

    // ============== GAP-P2: 流程模板库 ==============

    /**
     * GAP-P2: 列出所有可用模板
     *
     * @param category 模板分类（可选）
     * @return 模板列表
     */
    @GetMapping("/template/list")
    public BaseResponse<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return BaseResponse.ok(templateService.listTemplates(category));
    }

    /**
     * GAP-P2: 一键导入模板
     *
     * @param templateCode 模板编码
     * @param flowName     自定义流程名称（可选，为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    @Idempotent(key = "flowDesigner:importTemplate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/template/{templateCode}/import")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TEMPLATE_IMPORT)
    public BaseResponse<String> importTemplate(@PathVariable String templateCode,
                                          @RequestParam(required = false) String flowName) {
        return BaseResponse.ok(templateService.importTemplate(templateCode, flowName));
    }

    /**
     * GAP-P2: 获取模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情
     */
    @GetMapping("/template/{templateCode}")
    public BaseResponse<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
        return BaseResponse.ok(templateService.getTemplate(templateCode));
    }
}
