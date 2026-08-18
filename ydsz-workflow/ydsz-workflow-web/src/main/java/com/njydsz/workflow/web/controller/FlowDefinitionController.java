package com.njydsz.workflow.web.controller.definition;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import com.njydsz.workflow.server.service.FlowCustomButtonService;

/**
 * 流程定义统一 Controller
 *
 * <p>提供流程定义的部署 / 发布 / 废弃 / 查询 / 版本管理 / 设计器交互 / 导入导出 / 变更影响分析等全套 REST 接口。
 * 是设计器、流程中心、运维控制台的数据入口。所有接口对标 Activiti / Flowable API 风格。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>部署</b>：{@code POST /definition/deploy}（单条部署） / {@code POST
 *       /definition/batchDeployZip}（BPMN zip 批量部署）
 *   <li><b>发布控制</b>：{@code POST /definition/{id}/publish}（发布，带版本兼容性校验） / {@code POST
 *       /definition/{id}/deprecate}（废弃）
 *   <li><b>查询</b>：{@code GET /definition/code/{code}}（按编码查询已发布定义） / {@code GET
 *       /definition/page}（分页查询） / {@code GET /definition/{id}}（详情，含节点与跳转） / {@code GET
 *       /definition/{id}/preview}（只读预览）
 *   <li><b>版本管理</b>：{@code POST /definition/{code}/switchVersion}（切换激活版本） / {@code POST
 *       /definition/{id}/enable}（启用） / {@code POST /definition/{id}/disable}（停用） / {@code GET
 *       /definition/{id}/versions}（版本列表） / {@code GET /definition/{id}/diff}（版本差异对比） /
 *       {@code POST /definition/rollback}（一键回滚到上一版本）
 *   <li><b>设计器</b>：{@code POST /definition/{definitionId}/node/{nodeCode}/coordinate}（更新节点坐标） /
 *       {@code PUT /definition/{id}}（编辑未发布草稿）
 *   <li><b>导入导出</b>：{@code GET /definition/{id}/export}（JSON 导出） / {@code POST
 *       /definition/import}（JSON 导入，创建为草稿）
 *   <li><b>变更影响分析</b>：{@code GET /definition/migrationImpact}（评估版本升级对在途实例的影响）
 * </ul>
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 注解配置 {@link
 * PermissionCodes#WORKFLOW_DEFINITION_DEPLOY} 等权限码，与 RBAC 权限中心对接。
 *
 * <p><b>限流：</b>部署类接口通过 {@link RateLimit} 限流（{@code 50 QPS}）， 防止批量部署拖垮后端；幂等操作通过 {@link Idempotent}
 * 注解保证 「同一请求 5s 内只执行一次」。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传与权限校验，所有业务逻辑下沉到 {@link FlowDefinitionService}，符合「瘦 Controller /
 * 胖 Service」规范。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDefinitionService 流程定义服务
 * @see FlowDeployProcessDTO 部署参数 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-definition", description = "工作流流程定义统一接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDefinitionController {

  /** 流程定义服务 */
  private final FlowDefinitionService definitionService;

  /** P0-1: BPMN 事件订阅服务（消息关联 / 错误抛出） */
  private final FlowEventSubscriptionService eventSubscriptionService;

  /** P1-6: SLA 超时自动策略服务 */
  private final FlowSlaService slaService;

  /** 条件表达式服务，负责结构化条件 JSON 与表达式字符串的双向转换与校验 */
  private final FlowConditionExprService conditionExprService;

  /** 自定义按钮服务，负责节点按钮配置的查询、保存与执行 */
  private final FlowCustomButtonService customButtonService;

  /** 任务服务（slaProcess 中按 id 查任务） */
  private final com.njydsz.workflow.server.service.FlowTaskService taskService;

  /**
   * 部署流程定义
   *
   * @param dto 流程部署参数
   * @return 统一响应结果，包含流程定义 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:deploy:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.deploy", threshold = 50)
  @PostMapping("/definition/deploy")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'deploy'")
  @Operation(summary = "部署流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
  public BaseResponse<String> deploy(@Valid @RequestBody FlowDeployProcessDTO dto) {
    String id = definitionService.deploy(dto);
    return BaseResponse.success(id);
  }

  /**
   * GAP-P1-6: BPMN 部署包 .zip 批量导入流程定义。
   *
   * <p>对标 Activiti/Flowable 的 zip 部署能力。上传 .zip 文件，遍历其中的 {@code .bpmn} / {@code .bpmn20.xml}
   * 文件逐个部署，单个失败不影响其他文件。
   *
   * @param file zip 文件（multipart/form-data）
   * @return 统一响应结果，包含 successCount / failedItems
   */
  @Idempotent(
      key = "ydsz:workflow:FlowDefinitionController:batchDeployFromZip:lock",
      ttlSeconds = 5)
  @PostMapping(value = "/definition/batchDeployZip", consumes = "multipart/form-data")
  @Operation(summary = "BPMN 部署包 .zip 批量导入")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
  public BaseResponse<Map<String, Object>> batchDeployFromZip(
      @RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "zip 文件不能为空");
    }
    try {
      return BaseResponse.success(definitionService.batchDeployFromZip(file.getBytes(), null));
    } catch (IOException e) {
      return BaseResponse.error(BaseResultCode.BAD_REQUEST, "读取 zip 文件失败: " + e.getMessage());
    }
  }

  /**
   * 发布流程定义（带版本兼容性校验）。
   *
   * <p>P1-4: 发布前自动检测同 flowCode 激活版本的在途实例是否会因节点删除而卡死。 HIGH 风险时默认阻断，可通过 {@code force=true}
   * 强制发布（需管理员权限）。
   *
   * @param id 流程定义 ID
   * @param force 是否强制发布（跳过 HIGH 风险阻断），默认 false
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:publish:lock", ttlSeconds = 5)
  @PostMapping("/definition/{id}/publish")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'publish'")
  @Operation(summary = "发布流程定义（带版本兼容性校验）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> publish(
      @PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
    definitionService.publish(id, force);
    return BaseResponse.success();
  }

  /**
   * 废弃流程定义
   *
   * @param id 流程定义 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:deprecate:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.deprecate", threshold = 50)
  @PostMapping("/definition/{id}/deprecate")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'deprecate'")
  @Operation(summary = "废弃流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> deprecate(@PathVariable String id) {
    definitionService.deprecate(id);
    return BaseResponse.success();
  }

  /**
   * 按编码查询已发布流程定义
   *
   * @param code 流程编码
   * @param version 版本号（可选）
   * @param tenantId 租户 ID（可选）
   * @return 统一响应结果，包含流程定义
   */
  @GetMapping("/definition/code/{code}")
  @Operation(summary = "按编码查询已发布流程定义")
  public BaseResponse<FlowDefinitionVO> getByCode(
      @PathVariable String code,
      @RequestParam(required = false) String version,
      @RequestParam(required = false) String tenantId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.entityToVO(
            definitionService.getPublished(code, version, tenantId)));
  }

  /**
   * 分页查询流程定义
   *
   * @param pageNo 页码
   * @param pageSize 每页大小
   * @param category 分类（可选）
   * @param flowCode 流程编码（可选）
   * @return 统一响应结果，包含流程定义列表
   */
  @GetMapping("/definition/page")
  @Operation(summary = "分页查询流程定义")
  public BaseResponse<List<FlowDefinitionVO>> page(
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String flowCode) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowDefinitionListToVO(
            definitionService.page(pageNo, pageSize, category, flowCode)));
  }

  /**
   * P2-21: 流程定义详情查询（含节点 + 跳转）
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，包含 definition / nodes / skips
   */
  @GetMapping("/definition/{id}")
  @Operation(summary = "查询流程定义详情（含节点与跳转）")
  public BaseResponse<Map<String, Object>> getDefinitionDetail(@PathVariable String id) {
    return BaseResponse.success(definitionService.getDetail(id));
  }

  /**
   * P2-8 (GAP-53): 流程定义预览 — 只读模式返回定义详情 + readOnly 标记
   *
   * <p>前端用 bpmn-js 以只读模式渲染（禁用编辑 palette），展示流程全貌。 数据与 {@link #getDefinitionDetail} 一致，额外携带 {@code
   * readOnly=true} 标志。
   */
  @GetMapping("/definition/{id}/preview")
  @Operation(summary = "流程定义预览（只读）")
  public BaseResponse<Map<String, Object>> getDefinitionPreview(@PathVariable String id) {
    Map<String, Object> detail = definitionService.getDetail(id);
    detail.put("readOnly", true);
    return BaseResponse.success(detail);
  }

  /**
   * P2-27: 切换流程定义的激活版本
   *
   * @param code 流程编码
   * @param definitionId 目标流程定义 ID
   * @param tenantId 租户 ID（可选）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:switchVersion:lock", ttlSeconds = 5)
  @PostMapping("/definition/{code}/switchVersion")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'switchVersion'")
  @Operation(summary = "切换流程定义的激活版本")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> switchVersion(
      @PathVariable String code,
      @RequestParam String definitionId,
      @RequestParam(required = false) String tenantId) {
    definitionService.switchActiveVersion(code, definitionId, tenantId);
    return BaseResponse.success();
  }

  /**
   * P2-28: 启用流程定义
   *
   * @param id 流程定义 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:enable:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.enable", threshold = 50)
  @PostMapping("/definition/{id}/enable")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'enable'")
  @Operation(summary = "启用流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> enable(@PathVariable String id) {
    definitionService.enable(id);
    return BaseResponse.success();
  }

  /**
   * P2-28: 停用流程定义
   *
   * @param id 流程定义 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:disable:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.disable", threshold = 50)
  @PostMapping("/definition/{id}/disable")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.DISABLE,
      content = "'disable'")
  @Operation(summary = "停用流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
  public BaseResponse<Void> disable(@PathVariable String id) {
    definitionService.disable(id);
    return BaseResponse.success();
  }

  /**
   * 列出流程定义的所有历史版本
   *
   * @param id 流程定义 ID
   * @return 统一响应结果，包含版本列表
   */
  @GetMapping("/definition/{id}/versions")
  @Operation(summary = "列出流程定义的所有历史版本")
  public BaseResponse<List<Map<String, Object>>> listVersions(@PathVariable String id) {
    return BaseResponse.success(definitionService.listVersions(id));
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
  public BaseResponse<Map<String, Object>> diffVersions(
      @PathVariable String id, @RequestParam Integer v1, @RequestParam Integer v2) {
    return BaseResponse.success(definitionService.diffVersions(id, v1, v2));
  }

  /**
   * P0-2: 流程定义一键回滚
   *
   * <p>将指定 flowCode 的激活版本切换回上一个已发布版本， 并自动迁移在途实例。HIGH 风险时阻止回滚。
   *
   * @param flowCode 流程编码
   * @return 统一响应结果，包含回滚报告
   */
  @PostMapping("/definition/rollback")
  @Operation(summary = "一键回滚流程定义到上一版本")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public BaseResponse<Map<String, Object>> rollbackDefinition(@RequestParam String flowCode) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(definitionService.rollbackDefinition(flowCode, tenantId));
  }

  /**
   * P2-40: 更新节点坐标（供前端设计器保存布局）
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param coordinate 坐标 JSON 字符串
   * @return 统一响应结果
   */
  @Idempotent(
      key = "ydsz:workflow:FlowDefinitionController:updateNodeCoordinate:lock",
      ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.updateNodeCoordinate", threshold = 50)
  @PostMapping("/definition/{definitionId}/node/{nodeCode}/coordinate")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'updateNodeCoordinate'")
  @Operation(summary = "更新流程节点坐标")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public BaseResponse<Void> updateNodeCoordinate(
      @PathVariable String definitionId,
      @PathVariable String nodeCode,
      @RequestBody String coordinate) {
    definitionService.updateNodeCoordinate(definitionId, nodeCode, coordinate);
    return BaseResponse.success();
  }

  /**
   * P2-41: 编辑未发布的流程定义草稿
   *
   * @param id 流程定义 ID
   * @param dto 部署参数（含更新后的元数据与节点/跳转）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:updateDefinition:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowDefinitionDO.updateDefinition", threshold = 50)
  @PutMapping("/definition/{id}")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateDefinition'")
  @Operation(summary = "编辑未发布的流程定义草稿")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DESIGN)
  public BaseResponse<Void> updateDefinition(
      @PathVariable String id, @Valid @RequestBody FlowDeployProcessDTO dto) {
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
   * @param json 导出的 JSON 字符串
   * @param tenantId 租户 ID（可选，默认从上下文获取）
   * @return 统一响应结果，包含新创建的流程定义 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:importDefinition:lock", ttlSeconds = 5)
  @PostMapping("/definition/import")
  @Audit(
      module = "流程定义",
      type = AuditType.OPERATION,
      action = AuditAction.IMPORT,
      content = "'importDefinition'")
  @Operation(summary = "从 JSON 导入流程定义")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_IMPORT)
  public BaseResponse<String> importDefinition(
      @RequestBody String json, @RequestParam(required = false) String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(definitionService.importDefinition(json, tid));
  }

  /**
   * P2-5: 变更影响分析报告 — 评估老版本定义升级到新版本对在途实例的影响。
   *
   * <p>对标 Activiti/Flowable 的"流程定义升级影响分析"：
   *
   * <ul>
   *   <li>对比两个版本的节点 / 跳转差异
   *   <li>统计老版本在途实例数 + 按当前节点分组分布
   *   <li>识别卡死节点（HIGH 风险）和受影响节点（MEDIUM 风险）
   *   <li>输出整体风险等级（HIGH / MEDIUM / LOW / NONE）与迁移建议
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
      @RequestParam String oldDefinitionId, @RequestParam String newDefinitionId) {
    return BaseResponse.success(
        definitionService.analyzeMigrationImpact(oldDefinitionId, newDefinitionId));
  }

  // ============== P0-1: BPMN 事件触发 ==============

  /**
   * 查询引擎信息
   *
   * @return 统一响应结果，包含引擎类型与可用性
   */
  @GetMapping("/info")
  @Operation(summary = "查询工作流引擎信息")
  public BaseResponse<Map<String, Object>> info() {
    return BaseResponse.success(
        Map.of("engineType", "YDSZ-Flow", "available", true));
  }

  /**
   * P0-1: 消息关联 — 外部系统通过消息名称触发 WAITING 的 MESSAGE 订阅
   *
   * @param messageName 消息名称（对应 BPMN messageRef）
   * @param correlationKey 关联键（业务标识，可选）
   * @param payload 消息载荷 JSON（会合并到流程变量）
   * @param tenantId 租户 ID（可选）
   * @return 触发的订阅数量
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:correlateMessage:lock", ttlSeconds = 5)
  @PostMapping("/event/correlateMessage")
  @Audit(
      module = "流程事件",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'correlateMessage'")
  public BaseResponse<Integer> correlateMessage(
      @RequestParam String messageName,
      @RequestParam(required = false) String correlationKey,
      @RequestBody(required = false) String payload,
      @RequestParam(required = false) String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        eventSubscriptionService.correlateMessage(tid, messageName, correlationKey, payload));
  }

  /**
   * P0-1: 抛出错误 — 触发 WAITING 的 ERROR 订阅（边界错误事件）
   *
   * @param errorCode 错误代码（对应 BPMN errorRef）
   * @param instanceId 实例 ID（可选，为空则按 errorCode 全局匹配）
   * @param payload 错误载荷 JSON
   * @param tenantId 租户 ID（可选）
   * @return 触发的订阅数量
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:throwError:lock", ttlSeconds = 5)
  @PostMapping("/event/throwError")
  @Audit(
      module = "流程事件",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'throwError'")
  public BaseResponse<Integer> throwError(
      @RequestParam String errorCode,
      @RequestParam(required = false) String instanceId,
      @RequestBody(required = false) String payload,
      @RequestParam(required = false) String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        eventSubscriptionService.throwError(tid, instanceId, errorCode, payload));
  }

  /**
   * P0-1: 查询实例的事件订阅列表
   *
   * @param instanceId 实例 ID
   * @return 订阅列表（含 WAITING / COMPLETED / CANCELLED 状态）
   */
  @GetMapping("/instance/{instanceId}/eventSubscriptions")
  public BaseResponse<List<FlowEventSubscriptionVO>> listEventSubscriptions(
      @PathVariable String instanceId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowEventSubscriptionListToVO(
            eventSubscriptionService.listByInstance(instanceId)));
  }

  // ============== P1-6: SLA 超时自动策略 ==============

  /**
   * P1-6: 手动触发 SLA 扫描（管理后台调试用，cronjob 默认每 60s 自动扫描）
   *
   * @return 本轮扫描处理的任务数
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:slaScan:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowsla.slaScan", threshold = 50)
  @PostMapping("/sla/scan")
  @Audit(
      module = "SLA管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'slaScan'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
  public BaseResponse<Integer> slaScan() {
    int processed = slaService.scanAndProcess();
    return BaseResponse.success(processed);
  }

  /**
   * P1-6: 手动触发单条任务的 SLA 处理
   *
   * @param taskId 任务 ID
   * @return 是否处理成功
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:slaProcess:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowsla.slaProcess", threshold = 50)
  @PostMapping("/sla/process/{taskId}")
  @Audit(
      module = "SLA管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'slaProcess'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
  public BaseResponse<Boolean> slaProcess(@PathVariable String taskId) {
    FlowRunTaskDO task = taskService.getById(taskId);
    if (task == null) {
      return BaseResponse.error(BaseResultCode.NOT_FOUND, "任务不存在: " + taskId);
    }
    boolean ok = slaService.processOverdue(task);
    return BaseResponse.success(ok);
  }

  // ==================== 条件表达式编辑器 ====================

  /**
   * 结构化条件 JSON 转表达式字符串。
   *
   * @param body 请求体，需包含 conditionJson 和可选的 engine（默认 AVIATOR）
   * @return 转换后的表达式字符串
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:buildExpression:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowconditionexpr.build", threshold = 50)
  @PostMapping("/definition/conditionExpr/build")
  @Audit(
      module = "条件表达式",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'buildExpression'")
  @Operation(summary = "结构化条件 JSON → 表达式字符串")
  public BaseResponse<String> buildExpression(@RequestBody Map<String, String> body) {
    String conditionJson = body.get("conditionJson");
    String engine = body.getOrDefault("engine", "AVIATOR");
    return BaseResponse.success(conditionExprService.buildExpression(conditionJson, engine));
  }

  /**
   * 表达式字符串转结构化条件 JSON。
   *
   * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
   * @return 转换后的结构化条件 JSON 字符串
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:parseExpression:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowconditionexpr.parse", threshold = 50)
  @PostMapping("/definition/conditionExpr/parse")
  @Audit(
      module = "条件表达式",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'parseExpression'")
  @Operation(summary = "表达式字符串 → 结构化条件 JSON")
  public BaseResponse<String> parseExpression(@RequestBody Map<String, String> body) {
    String expression = body.get("expression");
    String engine = body.getOrDefault("engine", "AVIATOR");
    return BaseResponse.success(conditionExprService.parseExpression(expression, engine));
  }

  /**
   * 校验表达式语法。
   *
   * @param body 请求体，需包含 expression 和可选的 engine（默认 AVIATOR）
   * @return 校验结果（valid / errors 等字段）
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:validateExpression:lock", ttlSeconds = 5)
  @PostMapping("/definition/conditionExpr/validate")
  @Operation(summary = "校验表达式语法")
  public BaseResponse<Map<String, Object>> validateExpression(@RequestBody Map<String, String> body) {
    String expression = body.get("expression");
    String engine = body.getOrDefault("engine", "AVIATOR");
    return BaseResponse.success(conditionExprService.validateExpression(expression, engine));
  }

  /**
   * 获取可用的操作符列表。
   *
   * @return 操作符列表
   */
  @GetMapping("/definition/conditionExpr/operators")
  @Operation(summary = "获取可用的操作符列表")
  public BaseResponse<List<Map<String, String>>> operators() {
    return BaseResponse.success(conditionExprService.getOperators());
  }

  /**
   * 获取可用的值类型列表。
   *
   * @return 值类型列表
   */
  @GetMapping("/definition/conditionExpr/valueTypes")
  @Operation(summary = "获取可用的值类型列表")
  public BaseResponse<List<Map<String, String>>> valueTypes() {
    return BaseResponse.success(conditionExprService.getValueTypes());
  }

  /**
   * 获取指定流程定义的可用变量列表。
   *
   * @param definitionId 流程定义 ID
   * @return 变量列表
   */
  @GetMapping("/definition/conditionExpr/variables/{definitionId}")
  @Operation(summary = "获取流程定义的可用变量列表")
  public BaseResponse<List<Map<String, String>>> variables(@PathVariable String definitionId) {
    return BaseResponse.success(conditionExprService.getVariablesByDefinition(definitionId));
  }

  /**
   * 预览表达式执行结果。
   *
   * @param body 请求体，需包含 expression、variables、可选的 engine
   * @return 执行结果
   */
  @PostMapping("/definition/conditionExpr/preview")
  @Operation(summary = "预览表达式执行结果")
  public BaseResponse<Map<String, Object>> previewExpression(@RequestBody Map<String, Object> body) {
    String expression = body.get("expression") instanceof String s ? s : null;
    String engine = body.get("engine") instanceof String s ? s : "AVIATOR";
    Map<String, Object> variables =
        body.get("variables") instanceof Map<?, ?> m ? MapUtils.toStringObjectMap(m) : Map.of();
    return BaseResponse.success(
        conditionExprService.previewExpression(expression, variables, engine));
  }

  /**
   * 获取条件模板列表。
   *
   * @return 模板列表
   */
  @GetMapping("/definition/conditionExpr/templates")
  @Operation(summary = "获取条件模板列表")
  public BaseResponse<List<Map<String, String>>> conditionTemplates() {
    return BaseResponse.success(conditionExprService.getConditionTemplates());
  }

  // ==================== 节点自定义按钮 ====================

  /**
   * 获取节点的自定义按钮列表
   *
   * <p>按 (definitionId, nodeCode) 查询该节点的全部自定义按钮配置， 含按钮编码、名称、类型、关联逻辑、显示顺序、是否必填等。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 按钮配置列表
   */
  @GetMapping("/definition/customButtons")
  @Operation(summary = "获取节点的自定义按钮列表")
  public BaseResponse<List<Map<String, Object>>> listCustomButtons(
      @RequestParam String definitionId, @RequestParam String nodeCode) {
    return BaseResponse.success(customButtonService.getCustomButtons(definitionId, nodeCode));
  }

  /**
   * 保存节点的自定义按钮配置
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p><b>覆盖式</b>保存：先清空旧配置，再批量插入新配置（避免 N+1 循环）。 业务方传入<b>完整</b>的按钮列表，而非增量。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param buttons 按钮配置列表（含 buttonCode / buttonName / type / beanName / methodName / webhookUrl 等）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:saveCustomButtons:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowcustombutton.save", threshold = 50)
  @PostMapping("/definition/customButtons")
  @Audit(
      module = "自定义按钮",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveCustomButtons'")
  @Operation(summary = "保存节点的自定义按钮配置")
  public BaseResponse<Void> saveCustomButtons(
      @RequestParam String definitionId,
      @RequestParam String nodeCode,
      @RequestBody List<Map<String, Object>> buttons) {
    customButtonService.saveCustomButtons(definitionId, nodeCode, buttons);
    return BaseResponse.success();
  }

  /**
   * 执行自定义按钮操作
   *
   * <p>幂等保护 5 秒。
   *
   * <p>运行时执行按钮，触发后端 Java 方法 / HTTP Webhook / 脚本逻辑。 执行结果可回填到流程变量（{@code variables} 参数），影响后续分支走向。
   *
   * @param taskId 任务 ID
   * @param buttonCode 按钮编码
   * @param comment 审批意见（可选）
   * @param variables 流程变量（可选）
   * @return 按钮执行结果（含 success / message / outputVars）
   */
  @Idempotent(key = "ydsz:workflow:FlowDefinitionController:executeCustomButton:lock", ttlSeconds = 5)
  @PostMapping("/definition/customButtons/execute")
  @Operation(summary = "执行自定义按钮操作")
  public BaseResponse<Map<String, Object>> executeCustomButton(
      @RequestParam String taskId,
      @RequestParam String buttonCode,
      @RequestParam(required = false) String comment,
      @RequestBody(required = false) Map<String, Object> variables) {
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(
        customButtonService.executeButton(taskId, buttonCode, userId, comment, variables));
  }
}
