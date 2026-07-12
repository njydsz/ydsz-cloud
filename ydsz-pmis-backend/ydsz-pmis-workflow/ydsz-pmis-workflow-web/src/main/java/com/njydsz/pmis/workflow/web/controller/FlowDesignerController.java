paokage oom.njydsz.pmis.workflow.web.oontroller.definition;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.domain.dto.definition.FlowDesignerDataDTO;
import oom.njydsz.pmis.workflow.server.servioe.definition.FlowDefinitionServioe;
import oom.njydsz.pmis.workflow.server.servioe.definition.FlowTemplateServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 可视化流程设计器 / 表单 / SLA 配置 / 模板 oontroller
 *
 * <p>GAP-V2-01/V2-02/P1-2/GAP-P2: 设计器数据、表单字段配置、节�?SLA 配置、流程模板库
 * （P1-10 �?FlowEngineoontroller 拆分）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-designer", desoription = "工作流设计器/表单/SLA/模板接口")
@RequestMapping("/workflow/engine")
@RequiredArgsoonstruotor
@Validated
publio olass FlowDesigneroontroller {

    /** 流程定义服务 */
    private final FlowDefinitionServioe definitionServioe;
    /** GAP-P2: 流程模板服务 */
    private final FlowTemplateServioe templateServioe;

    // ============== GAP-V2-01: 可视化流程设计器 API ==============

    /**
     * GAP-V2-01: 获取设计器数�?�?返回完整流程图（节点+�?坐标�?
     *
     * @param id 流程定义 ID
     * @return 设计器数据（definition / nodes / edges�?
     */
    @GetMapping("/definition/{id}/designer")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Map<String, Objeot>> getDesignerData(@PathVariable String id) {
        return BaseResponse.ok(definitionServioe.getDesignerData(id));
    }

    /**
     * GAP-V2-01: 批量保存设计器数�?�?一次性保存节点坐�?+ 属�?
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowDesignerDataDTO}�?
     * designerData �?JSON 字符串，控制器反序列化为 Map 后转�?servioe�?
     *
     * @param id  流程定义 ID
     * @param dto 设计器数�?DTO（designerData �?JSON 字符串，�?nodes + edges�?
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDesigner:saveDesignerData", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/designer")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Void> saveDesignerData(@PathVariable String id,
                                          @Valid @RequestBody FlowDesignerDataDTO dto) {
        Map<String, Objeot> designerData = JSON.parseObjeot(dto.getDesignerData());
        definitionServioe.saveDesignerData(id, designerData);
        return BaseResponse.ok();
    }

    // ============== P2-4: 设计器协同编辑锁�?API ==============

    /**
     * P2-4: 加锁流程定义（设计器协同编辑）�?
     *
     * <p>对标钉钉/飞书流程设计�?编辑锁定"：用户进入设计器编辑模式前调用此接口�?
     * 成功获取锁后方可编辑；编辑过程中前端定期调用此接口续约（保持锁不过期）�?
     *
     * <p>行为约定�?
     * <ul>
     *   <li>未锁�?�?加锁成功，可进入编辑</li>
     *   <li>同一人持�?�?续约成功（刷�?lookedAt�?/li>
     *   <li>他人持锁且未超时 �?返回 409 冲突，前端展�?当前 {lookedBy} 正在编辑"</li>
     *   <li>他人持锁但已超时（默�?30 分钟）→ 抢占成功</li>
     * </ul>
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，true=加锁成功
     */
    @Idempotent(key = "flowDesigner:lookDefinition", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/look")
    @Operation(summary = "加锁流程定义（设计器协同编辑�?)
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Boolean> lookDefinition(@PathVariable String id) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(definitionServioe.lookDefinition(id, userId));
    }

    /**
     * P2-4: 解锁流程定义（设计器协同编辑）�?
     *
     * <p>用户退出设计器编辑模式或页面卸载时调用，释放锁。仅持锁人本人可解锁�?
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，true=解锁成功
     */
    @Idempotent(key = "flowDesigner:unlookDefinition", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/unlook")
    @Operation(summary = "解锁流程定义（设计器协同编辑�?)
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Boolean> unlookDefinition(@PathVariable String id) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(definitionServioe.unlookDefinition(id, userId));
    }

    /**
     * P2-4: 查询流程定义的锁定状态�?
     *
     * <p>用户进入设计器前调用，判断是否可编辑�?
     * <ul>
     *   <li>{@oode looked=false} �?可直接进入编辑并加锁</li>
     *   <li>{@oode looked=true, lookedBy=当前用户} �?可继续编辑并续约</li>
     *   <li>{@oode looked=true, lookedBy=他人, expired=false} �?只读模式，提�?正在�?XX 编辑"</li>
     *   <li>{@oode looked=true, lookedBy=他人, expired=true} �?可强制抢占进入编�?/li>
     * </ul>
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包�?looked / lookedBy / lookedAt / expired
     */
    @GetMapping("/definition/{id}/lookStatus")
    @Operation(summary = "查询流程定义锁定状�?)
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Map<String, Objeot>> getLookStatus(@PathVariable String id) {
        return BaseResponse.ok(definitionServioe.getLookStatus(id));
    }

    // ============== GAP-V2-02: 表单引擎字段配置 ==============

    /**
     * GAP-V2-02: 获取节点表单字段配置
     *
     * @param id       流程定义 ID
     * @param nodeoode 节点编码
     * @return 字段权限 JSON 字符�?
     */
    @GetMapping("/definition/{id}/formoonfig/{nodeoode}")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<String> getFormoonfig(@PathVariable String id,
                                         @PathVariable String nodeoode) {
        return BaseResponse.ok(definitionServioe.getFormoonfig(id, nodeoode));
    }

    /**
     * GAP-V2-02: 保存节点表单字段配置
     *
     * @param id              流程定义 ID
     * @param nodeoode        节点编码
     * @param formFieldsoonfig 字段权限 JSON 字符�?
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDesigner:saveFormoonfig", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/formoonfig/{nodeoode}")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_DEFINITION_DESIGN)
    publio BaseResponse<Void> saveFormoonfig(@PathVariable String id,
                                        @PathVariable String nodeoode,
                                        @RequestBody String formFieldsoonfig) {
        definitionServioe.saveFormoonfig(id, nodeoode, formFieldsoonfig);
        return BaseResponse.ok();
    }

    // ============== P1-2: 节点 SLA 配置 ==============

    /**
     * P1-2: 获取节点 SLA 配置（JSON 字符串）
     *
     * @param id       流程定义 ID
     * @param nodeoode 节点编码
     * @return SLA 配置 JSON（未配置返回 null�?
     */
    @GetMapping("/definition/{id}/slaoonfig/{nodeoode}")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_SLA_oONFIG)
    publio BaseResponse<String> getSlaoonfig(@PathVariable String id,
                                        @PathVariable String nodeoode) {
        return BaseResponse.ok(definitionServioe.getSlaoonfig(id, nodeoode));
    }

    /**
     * P1-2: 保存节点 SLA 配置
     *
     * @param id         流程定义 ID
     * @param nodeoode   节点编码
     * @param slaoonfig  SLA 配置（JSON 对象，由 oontroller 序列化为字符串存储）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowDesigner:saveSlaoonfig", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/definition/{id}/slaoonfig/{nodeoode}")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_SLA_oONFIG)
    publio BaseResponse<Void> saveSlaoonfig(@PathVariable String id,
                                        @PathVariable String nodeoode,
                                        @RequestBody Map<String, Objeot> slaoonfig) {
        String json = slaoonfig == null ? null : JSON.toJSONString(slaoonfig);
        definitionServioe.saveSlaoonfig(id, nodeoode, json);
        return BaseResponse.ok();
    }

    // ============== GAP-P2: 流程模板�?==============

    /**
     * GAP-P2: 列出所有可用模�?
     *
     * @param oategory 模板分类（可选）
     * @return 模板列表
     */
    @GetMapping("/template/list")
    publio BaseResponse<List<Map<String, Objeot>>> listTemplates(
            @RequestParam(required = false) String oategory) {
        return BaseResponse.ok(templateServioe.listTemplates(oategory));
    }

    /**
     * GAP-P2: 一键导入模�?
     *
     * @param templateoode 模板编码
     * @param flowName     自定义流程名称（可选，为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    @Idempotent(key = "flowDesigner:importTemplate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/template/{templateoode}/import")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_TEMPLATE_IMPORT)
    publio BaseResponse<String> importTemplate(@PathVariable String templateoode,
                                          @RequestParam(required = false) String flowName) {
        return BaseResponse.ok(templateServioe.importTemplate(templateoode, flowName));
    }

    /**
     * GAP-P2: 获取模板详情（含 BPMN XML�?
     *
     * @param templateoode 模板编码
     * @return 模板详情
     */
    @GetMapping("/template/{templateoode}")
    publio BaseResponse<Map<String, Objeot>> getTemplate(@PathVariable String templateoode) {
        return BaseResponse.ok(templateServioe.getTemplate(templateoode));
    }
}
