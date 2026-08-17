package com.njydsz.workflow.web.controller.instance;

import java.time.LocalDateTime;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.dto.FlowAutoTriggerCreateDTO;
import com.njydsz.workflow.domain.dto.FlowInstanceVariablesDTO;
import com.njydsz.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowInstanceMigrationService;
import com.njydsz.workflow.server.service.FlowInstanceService;

/**
 * 流程实例统一 Controller
 *
 * <p>流程实例的 HTTP 入口，承担工作流引擎「运行时」的启动、生命周期控制、查询视图、变量读写、表单渲染、催办、加签历史等全套能力。
 *
 * <p><b>接口分组：</b>
 *
 * <ul>
 *   <li><b>启动</b>：{@code POST /instance/start}（单条） / {@code POST /instance/batchStart}（批量）
 *   <li><b>业务查询</b>：{@code GET /instance/byBusiness}（按业务类型 + 业务 ID 查询实例视图）
 *   <li><b>控制</b>：{@code POST /instance/{id}/terminate}（终止） / {@code /suspend}（挂起） / {@code
 *       /activate}（激活） / {@code /recall}（撤回） / {@code /rollback}（回滚） / {@code /resubmit}（驳回后快速重审）
 *   <li><b>审计与时间线</b>：{@code GET /instance/{id}/auditTrail} / {@code /timeline} / {@code /diagram} / {@code
 *       /replay}
 *   <li><b>加签历史</b>：{@code GET /countersign/instance/{instanceId}} / {@code /task/{taskId}} / {@code /myInitiated}
 *   <li><b>分页查询</b>：{@code GET /instance/page} / {@code /my} / {@code /all}
 *   <li><b>变量读写</b>：{@code GET /instance/{id}/variables} / {@code POST /instance/{id}/variables}
 *   <li><b>催办</b>：{@code POST /instance/{id}/urge} / {@code POST /instance/{id}/urge/node}
 *   <li><b>表单渲染</b>：{@code GET /instance/{instanceId}/formRender}
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_INSTANCE_START} 等权限码； 启动类接口通过 {@link Audit} 注解写入审计日志（{@link
 * AuditType#OPERATION} + {@link AuditAction#CREATE}）。
 *
 * <p><b>限流：</b>启动类接口通过 {@link RateLimit} 限流（{@code 50 QPS}）， 终止 / 撤回等高危操作通过 {@link Idempotent} 5s
 * 防重。
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验；所有业务逻辑下沉到 {@link FlowInstanceService} 与 {@link
 * WorkflowFacade}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowInstanceService 流程实例服务
 * @see WorkflowFacade 工作流门面
 * @see FlowStartProcessDTO 启动参数 DTO
 */
@Slf4j
@RestController
@Tag(name = "workflow-instance", description = "工作流流程实例统一接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowInstanceController {

  /** 流程实例服务 */
  private final FlowInstanceService instanceService;

  /** 工作流门面，业务调用入口 */
  private final WorkflowFacade workflowFacade;

  /** GAP-V2-09: 流程实例迁移服务 */
  private final FlowInstanceMigrationService instanceMigrationService;

  /** 流程自动触发规则服务，负责规则注册、删除与启用/禁用管理 */
  private final FlowAutoTriggerService autoTriggerService;

  /**
   * 启动流程实例
   *
   * @param dto 流程启动参数
   * @return 统一响应结果，包含流程实例 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:startProcess:lock", ttlSeconds = 5)
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'启动流程:' + #dto.flowCode")
  @RateLimit(resource = "workflow.flowinstance.startProcess", threshold = 50)
  @PostMapping("/instance/start")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
  public BaseResponse<String> startProcess(@Valid @RequestBody FlowStartProcessDTO dto) {
    return BaseResponse.success(workflowFacade.startProcess(dto));
  }

  /**
   * P2-6: 批量启动流程实例。
   *
   * <p>对标钉钉/飞书"批量发起审批"能力：一次性提交多个流程实例，每个实例独立事务， 单个失败不影响其他实例的发起。
   *
   * @param dtos 流程启动参数列表
   * @return 统一响应结果，包含 successCount / failedCount / instanceIds / failedItems
   */
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'批量启动流程: ' + #dtos.size() + ' 条")
  @PostMapping("/instance/batchStart")
  @Operation(summary = "批量启动流程实例")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
  public BaseResponse<Map<String, Object>> batchStartInstances(
      @Valid @RequestBody List<FlowStartProcessDTO> dtos) {
    return BaseResponse.success(instanceService.batchStartInstances(dtos));
  }

  /**
   * 按业务类型与业务 ID 查询流程实例
   *
   * @param businessType 业务类型
   * @param businessId 业务 ID
   * @return 统一响应结果，包含流程实例视图
   */
  @GetMapping("/instance/byBusiness")
  public BaseResponse<FlowInstanceViewDTO> getByBusiness(
      @RequestParam String businessType, @RequestParam String businessId) {
    return BaseResponse.success(workflowFacade.getByBusiness(businessType, businessId));
  }

  /**
   * 终止流程实例
   *
   * @param id 流程实例 ID
   * @param reason 终止原因（可选）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:terminate:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/terminate")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'terminate'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> terminate(
      @PathVariable String id, @RequestParam(required = false) String reason) {
    workflowFacade.terminateProcess(id, reason);
    return BaseResponse.success();
  }

  /**
   * 挂起流程实例
   *
   * @param id 流程实例 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:suspend:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowinstance.suspend", threshold = 50)
  @PostMapping("/instance/{id}/suspend")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.DISABLE,
      content = "'suspend'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> suspend(@PathVariable String id) {
    workflowFacade.suspendProcess(id);
    return BaseResponse.success();
  }

  /**
   * 激活流程实例
   *
   * @param id 流程实例 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:activate:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowinstance.activate", threshold = 50)
  @PostMapping("/instance/{id}/activate")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'activate'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> activate(@PathVariable String id) {
    workflowFacade.activateProcess(id);
    return BaseResponse.success();
  }

  /**
   * 撤回流程（仅发起人可撤回，仅运行中可撤回）
   *
   * @param id 流程实例 ID
   * @param targetNodeCode 目标节点编码（可选，为空时撤回到开始节点下游第一节点）
   * @return 统一响应结果，包含是否撤回成功
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:recall:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/recall")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'recall'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
  public BaseResponse<Boolean> recall(
      @PathVariable String id, @RequestParam(required = false) String targetNodeCode) {
    return BaseResponse.success(
        instanceService.recall(id, AuthContextUtils.getUserId(), targetNodeCode));
  }

  /**
   * P1-1: 查询可撤回的历史节点列表。
   *
   * @param id 流程实例 ID
   * @return 统一响应结果，包含可撤回节点列表
   */
  @GetMapping("/instance/{id}/recallableNodes")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_START)
  public BaseResponse<List<Map<String, Object>>> listRecallableNodes(@PathVariable String id) {
    return BaseResponse.success(
        instanceService.listRecallableNodes(id, AuthContextUtils.getUserId()));
  }

  /**
   * P2-3: 回滚已完成的流程实例（撤销）
   *
   * @param id 流程实例 ID
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数（可选，默认 7）
   * @return 统一响应结果，包含是否回滚成功
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:rollback:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/rollback")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.RESTORE,
      content = "'rollback'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_ROLLBACK)
  public BaseResponse<Boolean> rollback(
      @PathVariable String id,
      @RequestParam String reason,
      @RequestParam(required = false, defaultValue = "7") int maxRollbackDays) {
    return BaseResponse.success(
        instanceService.rollback(id, AuthContextUtils.getUserId(), reason, maxRollbackDays));
  }

  /**
   * P2-2 (GAP-10): 驳回后快速重审 — 基于被驳回的原实例重新提交
   *
   * <p>仅发起人或拥有 workflow:instance:resubmit 权限的管理员可操作。
   *
   * <p>P1-8: 支持 redoMode 参数：
   *
   * <ul>
   *   <li>RESTART（默认）：仅 REJECTED 实例可重做，在原实例上重置状态并从开始节点重新推进；
   *   <li>NEW_INSTANCE：任意终态（COMPLETED/REJECTED/TERMINATED/ROLLED_BACK）均可重做， 创建全新实例，复用原实例的
   *       flowCode/businessType/businessId/initiator，合并变量。
   * </ul>
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:resubmit:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/resubmit")
  @Audit(
      module = "流程实例",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'resubmit'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_RESUBMIT)
  public BaseResponse<String> resubmit(
      @PathVariable String id,
      @RequestParam(required = false) String comment,
      @RequestParam(required = false, defaultValue = "RESTART") String redoMode,
      @RequestBody(required = false) Map<String, Object> variables) {
    return BaseResponse.success(
        workflowFacade.resubmitProcess(
            id, AuthContextUtils.getUserId(), variables, comment, redoMode));
  }

  /**
   * 审计轨迹查询
   *
   * @param id 流程实例 ID
   * @return 统一响应结果，包含审计轨迹列表
   */
  @GetMapping("/instance/{id}/auditTrail")
  public BaseResponse<List<Map<String, Object>>> auditTrail(@PathVariable String id) {
    return BaseResponse.success(workflowFacade.listAuditTrail(id));
  }

  /**
   * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
   *
   * @param id 流程实例 ID
   * @return 统一响应结果，包含时间线列表
   */
  @GetMapping("/instance/{id}/timeline")
  public BaseResponse<List<Map<String, Object>>> timeline(@PathVariable String id) {
    return BaseResponse.success(workflowFacade.getTimeline(id));
  }

  /**
   * P2-22: 流程图查询（高亮当前节点）
   *
   * @param id 流程实例 ID
   * @return 统一响应结果，包含 definition / nodes / skips，nodes 中每个节点带 active 标记
   */
  @GetMapping("/instance/{id}/diagram")
  public BaseResponse<Map<String, Object>> diagram(@PathVariable String id) {
    return BaseResponse.success(workflowFacade.getDiagram(id));
  }

  /**
   * P2-4: 流程回放步骤序列
   *
   * @param id 流程实例 ID
   * @return 步骤列表（按 timestamp 升序）
   */
  @GetMapping("/instance/{id}/replay")
  public BaseResponse<List<Map<String, Object>>> replay(@PathVariable String id) {
    return BaseResponse.success(workflowFacade.getReplaySteps(id));
  }

  /**
   * P2-23: 实例多维分页查询
   *
   * @param pageNo 页码
   * @param pageSize 每页大小
   * @param businessType 业务类型（可选）
   * @param initiatorId 发起人 ID（可选）
   * @param flowStatus 流程状态（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param tenantId 租户 ID（可选）
   * @return 统一响应结果，包含分页实例列表
   */
  @GetMapping("/instance/page")
  public PageResponse<List<FlowInstanceVO>> instancePage(
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) String initiatorId,
      @RequestParam(required = false) String flowStatus,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime,
      @RequestParam(required = false) String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    PageResponse<List<FlowInstance>> pageResult =
        instanceService.page(
            businessType, initiatorId, flowStatus, startTime, endTime, tid, pageNo, pageSize);
    List<FlowInstance> instances = pageResult.getData();
    List<FlowInstanceVO> vos = WorkflowConverter.INSTANT.flowInstanceListToVO(instances);
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
  }

  /**
   * P0-1: 我发起的流程实例分页查询（登录用户视图）
   *
   * @param flowCode 流程编码（可选，当前不参与过滤，保留以兼容前端入参）
   * @param flowName 流程名称（可选，当前不参与过滤，保留以兼容前端入参）
   * @param status 流程状态（可选，对应 flowStatus）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param pageNum 页码（默认 1）
   * @param pageSize 每页大小（默认 20，最大 100）
   * @return 统一响应结果，包含分页实例列表
   */
  @GetMapping("/instance/my")
  public PageResponse<List<FlowInstanceVO>> instanceMy(
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) String flowName,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime,
      @RequestParam(defaultValue = "1") @Min(1) int pageNum,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    PageResponse<List<FlowInstance>> pageResult =
        instanceService.page(
            null,
            AuthContextUtils.getUserId(),
            status,
            startTime,
            endTime,
            AuthContextUtils.getTenantIdOrDefault(),
            pageNum,
            pageSize);
    List<FlowInstance> instances = pageResult.getData();
    List<FlowInstanceVO> vos = WorkflowConverter.INSTANT.flowInstanceListToVO(instances);
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
  }

  /**
   * GAP-P0-1: 全部流程实例查询（管理员视图）
   *
   * <p>对标钉钉/飞书/企微审批中心"全部"Tab。需要 {@code workflow:monitor:view} 权限。
   *
   * @param page 页码
   * @param size 每页大小
   * @param businessType 业务类型（可选）
   * @param flowStatus 流程状态（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @return 统一响应结果，包含分页实例 Map 列表
   */
  @GetMapping("/instance/all")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_MONITOR_VIEW)
  public PageResponse<List<Map<String, Object>>> instanceAll(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) String flowStatus,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime) {
    return workflowFacade.listAllInstances(
        businessType, flowStatus, startTime, endTime, page, size);
  }

  /**
   * P2-24: 读取流程变量
   *
   * @param id 流程实例 ID
   * @return 统一响应结果，包含变量 Map
   */
  @GetMapping("/instance/{id}/variables")
  public BaseResponse<Map<String, Object>> getVariables(@PathVariable String id) {
    return BaseResponse.success(instanceService.getVariables(id));
  }

  /**
   * P2-24: 批量写入流程变量
   *
   * @param id 流程实例 ID
   * @param dto 变量 DTO
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:setVariables:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowinstance.setVariables", threshold = 50)
  @PostMapping("/instance/{id}/variables")
  @Audit(
      module = "流程变量",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'setVariables'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> setVariables(
      @PathVariable String id, @Valid @RequestBody FlowInstanceVariablesDTO dto) {
    instanceService.setVariables(id, dto.getVariables());
    return BaseResponse.success();
  }

  /**
   * 催办
   *
   * @param id 流程实例 ID
   * @param comment 催办备注（可选）
   * @return 统一响应结果，包含被催办人列表
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:urge:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/urge")
  @Audit(
      module = "流程变量",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'urge'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  public BaseResponse<List<String>> urge(
      @PathVariable String id, @RequestParam(required = false) String comment) {
    return BaseResponse.success(workflowFacade.urgeTask(id, AuthContextUtils.getUserId(), comment));
  }

  /**
   * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
   *
   * <p>nodeCode 不传时退化为实例级催办。
   */
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:urgeByNode:lock", ttlSeconds = 5)
  @PostMapping("/instance/{id}/urge/node")
  @Audit(
      module = "流程变量",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'urgeByNode'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_VIEW)
  public BaseResponse<List<String>> urgeByNode(
      @PathVariable String id,
      @RequestParam(required = false) String nodeCode,
      @RequestParam(required = false) String comment) {
    return BaseResponse.success(
        workflowFacade.urgeNodeTask(id, nodeCode, AuthContextUtils.getUserId(), comment));
  }

  /**
   * GAP-V2-02: 获取表单渲染数据 — 审批人打开待办时获取字段权限
   *
   * @param instanceId 流程实例 ID
   * @param taskId 任务 ID（可选，为空取当前节点）
   * @return 渲染数据（nodeCode / formFieldsConfig / variables）
   */
  @GetMapping("/instance/{instanceId}/formRender")
  public BaseResponse<Map<String, Object>> getFormRenderData(
      @PathVariable String instanceId, @RequestParam(required = false) String taskId) {
    return BaseResponse.success(instanceService.getFormRenderData(instanceId, taskId));
  }

  // ============== P1-10: 流程实例迁移 ==============

  /**
   * 执行实例迁移 — 将源定义下运行中实例迁移到目标定义
   *
   * @param dto 迁移参数
   * @return 统一响应结果，包含迁移结果报告
   */
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @RateLimit(resource = "workflow.flowmigration.migrateInstances", threshold = 50)
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:migrateInstances:lock", ttlSeconds = 5)
  @PostMapping("/instance/migrate")
  @Audit(
      module = "流程迁移",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'migrateInstances'")
  @AuthApiPermission
  public BaseResponse<com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO> migrateInstances(
      @Valid @RequestBody com.njydsz.workflow.domain.dto.InstanceMigrationDTO dto) {
    return BaseResponse.success(instanceMigrationService.migrate(dto));
  }

  /**
   * 预览实例迁移（试运行 / dry run）
   *
   * @param dto 迁移参数
   * @return 统一响应结果，包含迁移结果报告
   */
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @RateLimit(resource = "workflow.flowmigration.previewMigration", threshold = 50)
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:previewMigration:lock", ttlSeconds = 5)
  @PostMapping("/instance/migrate/preview")
  @Audit(
      module = "流程迁移",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'previewMigration'")
  @AuthApiPermission
  public BaseResponse<com.njydsz.workflow.domain.dto.InstanceMigrationResultDTO> previewMigration(
      @Valid @RequestBody com.njydsz.workflow.domain.dto.InstanceMigrationDTO dto) {
    return BaseResponse.success(instanceMigrationService.previewMigration(dto));
  }

  /**
   * 自动映射节点编码 — 对比源/目标定义节点，按编码自动匹配
   *
   * @param sourceDefinitionId 源定义 ID
   * @param targetDefinitionId 目标定义 ID
   * @return 统一响应结果，包含「旧节点编码 → 新节点编码」的映射
   */
  @GetMapping("/instance/migrate/autoMap")
  public BaseResponse<Map<String, String>> autoMapNodes(
      @RequestParam Long sourceDefinitionId, @RequestParam Long targetDefinitionId) {
    return BaseResponse.success(
        instanceMigrationService.autoMapNodes(sourceDefinitionId, targetDefinitionId));
  }

  // ============== 流程自动触发规则 ==============

  /**
   * 列出所有触发规则
   *
   * <p>返回全部已注册规则（启用 + 禁用），按创建时间倒序排列。
   *
   * @return 触发规则列表（含 sourceFlowCode / targetFlowCode / conditionExpression / enabled）
   */
  @Operation(summary = "列出所有触发规则")
  @GetMapping("/instance/trigger/list")
  public BaseResponse<List<FlowAutoTriggerVO>> listTriggers() {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowAutoTriggerListToVO(autoTriggerService.listAll()));
  }

  /**
   * 创建触发规则
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>注册一条 (源流程 → 目标流程) 触发规则，条件表达式使用 QLExpress 沙箱。
   *
   * @param dto 触发规则 DTO（sourceFlowCode / targetFlowCode / conditionExpression / description）
   * @return 空响应
   */
  @Operation(summary = "创建触发规则")
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:createTrigger:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowautotrigger.create", threshold = 50)
  @PostMapping("/instance/trigger")
  @Audit(
      module = "自动触发",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'createTrigger'")
  public BaseResponse<Void> createTrigger(@Valid @RequestBody FlowAutoTriggerCreateDTO dto) {
    String sourceFlowCode = dto.getSourceFlowCode();
    String targetFlowCode = dto.getTargetFlowCode();
    String conditionExpression = dto.getConditionExpression();
    autoTriggerService.registerTrigger(sourceFlowCode, targetFlowCode, conditionExpression);
    return BaseResponse.success();
  }

  /**
   * 删除触发规则
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p><b>物理删除</b>，不可恢复。如需临时停用建议改用启停切换。
   *
   * @param id 规则 ID
   * @return 空响应
   */
  @Operation(summary = "删除触发规则")
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:deleteTrigger:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowautotrigger.delete", threshold = 50)
  @DeleteMapping("/instance/trigger/{id}")
  @Audit(
      module = "自动触发",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'deleteTrigger'")
  public BaseResponse<Void> deleteTrigger(@PathVariable String id) {
    autoTriggerService.deleteById(id);
    return BaseResponse.success();
  }

  /**
   * 启用/禁用触发规则
   *
   * <p>幂等保护 5 秒。
   *
   * <p>在 {@code enabled=true} 和 {@code enabled=false} 之间切换，<b>不删除规则</b>。
   *
   * @param id 规则 ID
   * @return 切换后的状态（id / enabled）
   */
  @Operation(summary = "启用/禁用触发规则")
  @Idempotent(key = "ydsz:workflow:FlowInstanceController:toggleTrigger:lock", ttlSeconds = 5)
  @PutMapping("/instance/trigger/{id}/toggle")
  public BaseResponse<Map<String, Object>> toggleTrigger(@PathVariable String id) {
    boolean enabled = autoTriggerService.toggleEnabled(id);
    return BaseResponse.success(Map.of("id", id, "enabled", enabled));
  }
}
