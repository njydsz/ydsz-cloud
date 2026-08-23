package com.njydsz.workflow.web.controller.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.dto.FlowDesignerDataDTO;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.server.engine.listener.FlowListenerEventType;
import com.njydsz.workflow.server.engine.listener.FlowListenerPluginExecutor;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowTemplateService;

/**
 * 可视化流程设计器 / 表单 / SLA 配置 / 模板 Controller
 *
 * <p>负责 BPMN 设计器、表单设计器、SLA 配置、模板库的 HTTP 入口，是工作流「定义侧」UI 后端。
 *
 * <p><b>接口分组：</b>
 *
 * <ul>
 *   <li><b>设计器</b>：{@code GET /definition/{id}/designer}（加载设计器数据） / {@code POST
 *       /definition/{id}/designer}（保存设计器数据） / {@code PUT /definition/{id}/node/coordinate}（更新节点坐标）
 *       / {@code POST /definition/{id}/lock}（协同编辑锁） / {@code DELETE /definition/{id}/lock}（解锁）
 *   <li><b>表单配置</b>：{@code GET /definition/{id}/node/{code}/form}（读取字段权限） / {@code POST
 *       .../form}（保存字段权限）
 *   <li><b>SLA 配置</b>：{@code GET /definition/{id}/node/{code}/sla} / {@code POST .../sla}（节点级 SLA）
 *   <li><b>模板库</b>：{@code GET /templates}（模板列表） / {@code GET /templates/recommend}（智能推荐） / {@code
 *       POST /templates/{id}/apply}（应用模板）
 *   <li><b>版本管理</b>：{@code GET /definition/{id}/versions} / {@code GET /definition/{id}/diff}（版本对比）
 *       / {@code POST /definition/{id}/impact}（变更影响分析）
 * </ul>
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_DEFINITION_DESIGN} 等权限码； 协同编辑锁由 {@link Idempotent} 注解保护，避免多人同时编辑冲突。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验、VO 转换； 设计器数据组装、表单 / SLA 配置、模板推荐下沉到 {@link
 * FlowDefinitionService} / {@link FlowTemplateService}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDefinitionService 流程定义服务
 * @see FlowTemplateService 流程模板服务
 * @see FlowDesignerDataDTO 设计器数据传输对象
 */
@Slf4j
@RestController
@Tag(name = "workflow-designer", description = "工作流设计器/表单/SLA/模板接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDesignerController {

  /** 流程定义服务 */
  private final FlowDefinitionService definitionService;

  /** GAP-P2: 流程模板服务 */
  private final FlowTemplateService templateService;

  /**
   * P2-38: 监听器插件执行器，暴露可用插件列表供设计器下拉选择。
   */
  private final FlowListenerPluginExecutor listenerPluginExecutor;

  // ============== GAP-V2-01: 可视化流程设计器 API ==============

  /**
   * GAP-V2-01: 获取设计器数据 — 返回完整流程图（节点+边+坐标）
   *
   * @param id 流程定义 ID
   * @return 设计器数据（definition / nodes / edges）
   */
  @GetMapping("/definition/{id}/designer")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  @Operation(summary = "获取设计器数据（完整流程图：节点+边+坐标）")
  public YdszResponse<Map<String, Object>> getDesignerData(@PathVariable String id) {
    return YdszResponse.success(definitionService.getDesignerData(id));
  }

  /**
   * GAP-V2-01: 批量保存设计器数据 — 一次性保存节点坐标 + 属性
   *
   * <p>P1-10: 由原 Map body 改造为 {@link FlowDesignerDataDTO}， designerData 为 JSON 字符串，控制器反序列化为 Map 后转交
   * service。
   *
   * @param id 流程定义 ID
   * @param dto 设计器数据 DTO（designerData 为 JSON 字符串，含 nodes + edges）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:designer:saveDesignerData", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdesigner.saveDesignerData", threshold = 50)
  @PostMapping("/definition/{id}/designer")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveDesignerData'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  @Operation(summary = "批量保存设计器数据（节点坐标+属性）")
  public YdszResponse<Void> saveDesignerData(
      @PathVariable String id, @Valid @RequestBody FlowDesignerDataDTO dto) {
    Map<String, Object> designerData = YdszJson.parseMap(dto.getDesignerData());
    definitionService.saveDesignerData(id, designerData);
    return YdszResponse.success();
  }

  // ============== P2-4: 设计器协同编辑锁定 API ==============

  /**
   * P2-4: 加锁流程定义（设计器协同编辑）。
   *
   * <p>对标钉钉/飞书流程设计器"编辑锁定"：用户进入设计器编辑模式前调用此接口， 成功获取锁后方可编辑；编辑过程中前端定期调用此接口续约（保持锁不过期）。
   *
   * <p>行为约定：
   *
   * <ul>
   *   <li>未锁定 → 加锁成功，可进入编辑
   *   <li>同一人持锁 → 续约成功（刷新 lockedAt）
   *   <li>他人持锁且未超时 → 返回 409 冲突，前端展示"当前 {lockedBy} 正在编辑"
   *   <li>他人持锁但已超时（默认 30 分钟）→ 抢占成功
   * </ul>
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，true=加锁成功
   */
  @Idempotent(key = "ydsz:workflow:designer:lockDefinition", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdesigner.lockDefinition", threshold = 50)
  @PostMapping("/definition/{id}/lock")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.LOCK,
      content = "'lockDefinition'")
  @Operation(summary = "加锁流程定义（设计器协同编辑）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<Boolean> lockDefinition(@PathVariable String id) {
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(definitionService.lockDefinition(id, userId));
  }

  /**
   * P2-4: 解锁流程定义（设计器协同编辑）。
   *
   * <p>用户退出设计器编辑模式或页面卸载时调用，释放锁。仅持锁人本人可解锁。
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，true=解锁成功
   */
  @Idempotent(key = "ydsz:workflow:designer:unlockDefinition", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdesigner.unlockDefinition", threshold = 50)
  @PostMapping("/definition/{id}/unlock")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.LOCK,
      content = "'unlockDefinition'")
  @Operation(summary = "解锁流程定义（设计器协同编辑）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<Boolean> unlockDefinition(@PathVariable String id) {
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(definitionService.unlockDefinition(id, userId));
  }

  /**
   * P2-4: 查询流程定义的锁定状态。
   *
   * <p>用户进入设计器前调用，判断是否可编辑：
   *
   * <ul>
   *   <li>{@code locked=false} → 可直接进入编辑并加锁
   *   <li>{@code locked=true, lockedBy=当前用户} → 可继续编辑并续约
   *   <li>{@code locked=true, lockedBy=他人, expired=false} → 只读模式，提示"正在被 XX 编辑"
   *   <li>{@code locked=true, lockedBy=他人, expired=true} → 可强制抢占进入编辑
   * </ul>
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，包含 locked / lockedBy / lockedAt / expired
   */
  @GetMapping("/definition/{id}/lockStatus")
  @Operation(summary = "查询流程定义锁定状态")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<Map<String, Object>> getLockStatus(@PathVariable String id) {
    return YdszResponse.success(definitionService.getLockStatus(id));
  }

  // ============== GAP-V2-02: 表单引擎字段配置 ==============

  /**
   * GAP-V2-02: 获取节点表单字段配置
   *
   * @param id 流程定义 ID
   * @param nodeCode 节点编码
   * @return 字段权限 JSON 字符串
   */
  @GetMapping("/definition/{id}/formConfig/{nodeCode}")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  @Operation(summary = "获取节点表单字段配置")
  public YdszResponse<String> getFormConfig(
      @PathVariable String id, @PathVariable String nodeCode) {
    return YdszResponse.success(definitionService.getFormConfig(id, nodeCode));
  }

  /**
   * GAP-V2-02: 保存节点表单字段配置
   *
   * @param id 流程定义 ID
   * @param nodeCode 节点编码
   * @param formFieldsConfig 字段权限 JSON 字符串
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:designer:saveFormConfig", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdesigner.saveFormConfig", threshold = 50)
  @PostMapping("/definition/{id}/formConfig/{nodeCode}")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveFormConfig'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  @Operation(summary = "保存节点表单字段配置")
  public YdszResponse<Void> saveFormConfig(
      @PathVariable String id,
      @PathVariable String nodeCode,
      @RequestBody String formFieldsConfig) {
    definitionService.saveFormConfig(id, nodeCode, formFieldsConfig);
    return YdszResponse.success();
  }

  // ============== P1-2: 节点 SLA 配置 ==============

  /**
   * P1-2: 获取节点 SLA 配置（JSON 字符串）
   *
   * @param id 流程定义 ID
   * @param nodeCode 节点编码
   * @return SLA 配置 JSON（未配置返回 null）
   */
  @GetMapping("/definition/{id}/slaConfig/{nodeCode}")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
  @Operation(summary = "获取节点 SLA 配置")
  public YdszResponse<String> getSlaConfig(@PathVariable String id, @PathVariable String nodeCode) {
    return YdszResponse.success(definitionService.getSlaConfig(id, nodeCode));
  }

  /**
   * P1-2: 保存节点 SLA 配置
   *
   * @param id 流程定义 ID
   * @param nodeCode 节点编码
   * @param slaConfig SLA 配置（JSON 对象，由 controller 序列化为字符串存储）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:designer:saveSlaConfig", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdesigner.saveSlaConfig", threshold = 50)
  @PostMapping("/definition/{id}/slaConfig/{nodeCode}")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveSlaConfig'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
  @Operation(summary = "保存节点 SLA 配置")
  public YdszResponse<Void> saveSlaConfig(
      @PathVariable String id,
      @PathVariable String nodeCode,
      @RequestBody Map<String, Object> slaConfig) {
    String json = slaConfig == null ? null : YdszJson.toJson(slaConfig);
    definitionService.saveSlaConfig(id, nodeCode, json);
    return YdszResponse.success();
  }

  /**
   * P2-38: 获取办理人类型列表（设计器下拉选择）
   *
   * <p>返回所有 {@link FlowAssigneeType} 的枚举名称和中文显示名称，
   * 前端设计器用于办理人规则配置的下拉选项。
   *
   * @return 办理人类型列表（code = 枚举名，desc = 中文名称）
   */
  @GetMapping("/assignee/types")
  @Operation(summary = "获取办理人类型列表（设计器下拉选择）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<List<Map<String, String>>> listAssigneeTypes() {
    List<Map<String, String>> result = new ArrayList<>();
    for (FlowAssigneeType t : FlowAssigneeType.values()) {
      result.add(Map.of("code", t.name(), "desc", t.getDesc()));
    }
    return YdszResponse.success(result);
  }

  // ============== P2-38: 监听器插件配置 API ==============

  /**
   * P2-38: 获取可用的监听器插件列表（设计器下拉选择）
   *
   * <p>返回所有已注册的 {@link com.njydsz.workflow.server.engine.listener.FlowListenerPlugin}
   * Bean 名称，前端设计器可在"节点属性 → 监听器"面板中为此插件绑定事件类型。
   *
   * @return 插件名称列表
   */
  @GetMapping("/listener/plugins")
  @Operation(summary = "获取可用的监听器插件列表（设计器下拉选择）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<List<String>> listListenerPlugins() {
    return YdszResponse.success(listenerPluginExecutor.getAvailablePluginNames());
  }

  /**
   * P2-38: 获取所有支持的事件类型（设计器下拉选择）
   *
   * @return 事件类型列表
   */
  @GetMapping("/listener/eventTypes")
  @Operation(summary = "获取所有监听器事件类型")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public YdszResponse<List<Map<String, String>>> listListenerEventTypes() {
    List<Map<String, String>> result = new ArrayList<>();
    for (FlowListenerEventType t : FlowListenerEventType.values()) {
      result.add(Map.of("code", t.getCode(), "desc", t.getDesc()));
    }
    return YdszResponse.success(result);
  }

  // ============== GAP-P2: 流程模板库 ==============

  /**
   * GAP-P2: 列出所有可用模板
   *
   * @param category 模板分类（可选）
   * @return 模板列表
   */
  @GetMapping("/template/list")
  @Operation(summary = "列出所有可用模板")
  public YdszResponse<List<Map<String, Object>>> listTemplates(
      @RequestParam(required = false) String category) {
    return YdszResponse.success(templateService.listTemplates(category));
  }

  /**
   * GAP-P2: 一键导入模板
   *
   * @param templateCode 模板编码
   * @param flowName 自定义流程名称（可选，为空则使用模板名称）
   * @return 新创建的流程定义 ID
   */
  @Idempotent(key = "ydsz:workflow:designer:importTemplate", ttlSeconds = 5)
  @PostMapping("/template/{templateCode}/import")
  @Audit(
      module = "流程设计器",
      type = AuditType.OPERATION,
      action = AuditAction.IMPORT,
      content = "'importTemplate'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TEMPLATE_IMPORT)
  @Operation(summary = "一键导入模板")
  public YdszResponse<String> importTemplate(
      @PathVariable String templateCode, @RequestParam(required = false) String flowName) {
    return YdszResponse.success(templateService.importTemplate(templateCode, flowName));
  }

  /**
   * GAP-P2: 获取模板详情（含 BPMN XML）
   *
   * @param templateCode 模板编码
   * @return 模板详情
   */
  @GetMapping("/template/{templateCode}")
  @Operation(summary = "获取模板详情（含 BPMN XML）")
  public YdszResponse<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
    return YdszResponse.success(templateService.getTemplate(templateCode));
  }
}
