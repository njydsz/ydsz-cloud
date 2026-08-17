package com.njydsz.workflow.web.controller.instance;

import java.util.LinkedHashMap;
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
import com.njydsz.workflow.domain.dto.FlowAttachmentPreviewVO;
import com.njydsz.workflow.domain.query.FlowCcQueryDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.dto.post.FlowDelegateAuthPostDTO;
import com.njydsz.workflow.infra.entity.FlowAuditLogDO;
import com.njydsz.workflow.infra.entity.FlowCcDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.vo.FlowAttachmentVO;
import com.njydsz.workflow.domain.vo.FlowDelegateAuthVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.server.service.FlowAttachmentService;
import com.njydsz.workflow.server.service.FlowCcService;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

/**
 * 任务统一 Controller
 *
 * <p>流程任务的 HTTP 入口，对标钉钉 / 飞书审批中心接口。承担审批人对任务的全方位操作：
 * 核心办理、批量操作、查询统计、辅助操作、待办推送。
 *
 * <p><b>接口分组：</b>
 *
 * <ul>
 *   <li><b>任务详情</b>：{@code GET /task/{taskId}}（含历史轨迹 / 表单权限）
 *   <li><b>办理动作</b>：{@code POST /task/claim}（签收） / {@code pass}（通过） / {@code reject}（驳回） / {@code
 *       transfer}（转办） / {@code delegate}（委派）
 *   <li><b>会签</b>：{@code POST /task/countersignBefore}（前加签） / {@code countersignAfter}（后加签） /
 *       {@code countersignParallel}（并加签）
 *   <li><b>跳转</b>：{@code POST /task/jump}（管理员强制跳转） / {@code freeJump}（办理人自由流跳转）
 *   <li><b>批量操作</b>：{@code POST /task/batchPass} / {@code batchReject} / {@code batchTransfer} / {@code
 *       instance/batchUrge} / {@code task/passAll}
 *   <li><b>查询统计</b>：{@code GET /task/todo} / {@code /done} / {@code /overdue} / {@code /done/search} /
 *       {@code /stats/nodeDuration} / {@code /stats/overdue}
 *   <li><b>辅助操作</b>：{@code POST /task/countersignRemove} / {@code markRead} / {@code communicate} / {@code
 *       saveDraft} / {@code addApprover} / {@code retract} / {@code suspendTask} / {@code activateTask}
 *   <li><b>待办推送</b>：{@code GET /todo/count} / {@code POST /todo/pushMine}
 * </ul>
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验 {@link PermissionCodes#WORKFLOW_TASK_VIEW} /
 * {@code WORKFLOW_TASK_OPERATE} / {@code WORKFLOW_INSTANCE_CONTROL} / {@code
 * WORKFLOW_TASK_FREE_JUMP} 等权限码； 办理动作额外校验「操作人 == 任务办理人」防越权。
 *
 * <p><b>限流：</b>通过 / 驳回 / 转办 / 加签等高频操作通过 {@link RateLimit} 限流， 防止恶意刷接口；幂等操作通过 {@link Idempotent} 注解
 * 5s 防重。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传、权限校验、VO 转换，所有业务逻辑下沉到 {@link FlowTaskService}（门面模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskService 任务服务门面
 * @see WorkflowFacade 工作流门面（业务编排）
 * @see FlowTaskOperateDTO 任务操作 DTO
 * @see FlowRunTaskDO 运行时任务实体
 */
@Slf4j
@RestController
@Tag(name = "workflow-task", description = "工作流任务统一接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowTaskController {

  /** 任务服务 */
  private final FlowTaskService taskService;

  /** 工作流门面，业务调用入口 */
  private final WorkflowFacade workflowFacade;

  /** P1-1: 历史任务 mapper（驳回候选目标节点） */
  private final FlowHisTaskMapper hisTaskMapper;

  /** P1-7: WebSocket 待办数实时推送服务 */
  private final FlowTodoCountPushService todoCountPushService;

  /** P1-8: 加签历史审计日志 mapper */
  private final FlowAuditLogMapper auditLogMapper;

  /** P1-4: 长期授权委派服务 */
  private final FlowDelegateAuthService delegateAuthService;

  /** P0-3: 抄送服务 */
  private final FlowCcService ccService;

  /** 审批附件服务 */
  private final FlowAttachmentService attachmentService;

  // ============== 任务操作 ==============

  /**
   * P2-20: 任务详情查询
   *
   * @param taskId 任务 ID
   * @return 统一响应结果，包含任务详情
   */
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_VIEW)
  @GetMapping("/task/{taskId}")
  public BaseResponse<Map<String, Object>> taskDetail(@PathVariable String taskId) {
    return BaseResponse.success(workflowFacade.getTaskDetail(taskId));
  }

  /**
   * 签收任务
   *
   * @param taskId 任务 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:claim:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.claim", threshold = 50)
  @PostMapping("/task/claim")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'claim'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> claim(@RequestParam String taskId) {
    workflowFacade.claimTask(taskId, AuthContextUtils.getUserId());
    return BaseResponse.success();
  }

  /**
   * 通过任务
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:pass:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.pass", threshold = 50)
  @PostMapping("/task/pass")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'pass'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> pass(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.completeTask(dto);
    return BaseResponse.success();
  }

  /**
   * 驳回任务
   *
   * @param dto 任务操作参数（可含 targetNodeCode 指定驳回目标；不填则按流程默认）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:reject:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.reject", threshold = 50)
  @PostMapping("/task/reject")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.REJECT,
      content = "'reject'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> reject(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.rejectTask(dto);
    return BaseResponse.success();
  }

  /**
   * P1-1: 查询任务所属实例经过的历史节点（驳回候选目标）
   *
   * @param taskId 任务 ID
   * @return 该任务所属实例经过的历史节点列表（按首次完成时间正序）
   */
  @GetMapping("/task/{taskId}/rejectableNodes")
  public BaseResponse<List<Map<String, Object>>> rejectableNodes(@PathVariable String taskId) {
    FlowRunTaskDO task = taskService.getById(taskId);
    if (task == null) {
      return BaseResponse.success(List.of());
    }
    List<Map<String, Object>> nodes = hisTaskMapper.listPassedNodes(task.getInstanceId());
    return BaseResponse.success(nodes);
  }

  /**
   * 转办任务
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:transfer:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.transfer", threshold = 50)
  @PostMapping("/task/transfer")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'transfer'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> transfer(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.transferTask(dto);
    return BaseResponse.success();
  }

  /**
   * 委派任务
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:delegate:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.delegate", threshold = 50)
  @PostMapping("/task/delegate")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'delegate'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> delegate(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.delegateTask(dto);
    return BaseResponse.success();
  }

  /**
   * 前加签
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:countersignBefore:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.countersignBefore", threshold = 50)
  @PostMapping("/task/countersignBefore")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'countersignBefore'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> countersignBefore(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.countersignBeforeTask(dto);
    return BaseResponse.success();
  }

  /**
   * 后加签
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:countersignAfter:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.countersignAfter", threshold = 50)
  @PostMapping("/task/countersignAfter")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'countersignAfter'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> countersignAfter(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.countersignAfterTask(dto);
    return BaseResponse.success();
  }

  /**
   * GAP-P0-3: 并加签 — 与原审批人并行审批，所有人审完才推进
   *
   * @param dto 任务操作参数
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:countersignParallel:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.countersignParallel", threshold = 50)
  @PostMapping("/task/countersignParallel")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'countersignParallel'")
  @Operation(summary = "并加签（与原审批人并行审批）")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> countersignParallel(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.countersignParallelTask(dto);
    return BaseResponse.success();
  }

  /**
   * P2-25: 自由跳转 — 管理员强制跳转到任意节点
   *
   * @param dto 任务操作参数（需含 taskId + targetNodeCode）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:jump:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.jump", threshold = 50)
  @PostMapping("/task/jump")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'jump'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> jump(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.jumpTask(dto);
    return BaseResponse.success();
  }

  /**
   * GAP-P2-9: 自由流跳转 — 当前办理人运行时动态指定下一节点 + 办理人
   *
   * @param dto 任务操作参数（需含 taskId + targetNodeCode + action=JUMP，可选 targetAssignees）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:freeJump:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.freeJump", threshold = 50)
  @PostMapping("/task/freeJump")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'freeJump'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_FREE_JUMP)
  public BaseResponse<Void> freeJump(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    dto.setAction("JUMP");
    workflowFacade.jumpTask(dto);
    return BaseResponse.success();
  }

  // ============== 批量操作 ==============

  /**
   * 批量通过
   *
   * @param taskIds 任务 ID 列表
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:batchPass:lock", ttlSeconds = 5)
  @PostMapping("/task/batchPass")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'batchPass'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> batchPass(@RequestBody List<String> taskIds) {
    workflowFacade.batchPass(taskIds, AuthContextUtils.getUserId(), AuthContextUtils.getUsername());
    return BaseResponse.success();
  }

  /**
   * 批量驳回
   *
   * @param dtos 任务操作参数列表
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:batchReject:lock", ttlSeconds = 5)
  @PostMapping("/task/batchReject")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.REJECT,
      content = "'batchReject'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> batchReject(@Valid @RequestBody List<FlowTaskOperateDTO> dtos) {
    for (FlowTaskOperateDTO dto : dtos) {
      dto.setUserId(AuthContextUtils.getUserId());
      dto.setUserName(AuthContextUtils.getUsername());
    }
    workflowFacade.batchReject(dtos);
    return BaseResponse.success();
  }

  /**
   * 批量转办
   *
   * @param dtos 任务操作参数列表
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:batchTransfer:lock", ttlSeconds = 5)
  @PostMapping("/task/batchTransfer")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'batchTransfer'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> batchTransfer(@Valid @RequestBody List<FlowTaskOperateDTO> dtos) {
    for (FlowTaskOperateDTO dto : dtos) {
      dto.setUserId(AuthContextUtils.getUserId());
      dto.setUserName(AuthContextUtils.getUsername());
    }
    workflowFacade.batchTransfer(dtos);
    return BaseResponse.success();
  }

  /**
   * P2-34: 批量催办
   *
   * @param instanceIds 流程实例 ID 列表
   * @param comment 催办备注（可选）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:batchUrge:lock", ttlSeconds = 5)
  @PostMapping("/instance/batchUrge")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'batchUrge'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Map<String, Object>> batchUrge(
      @RequestBody List<String> instanceIds, @RequestParam(required = false) String comment) {
    return BaseResponse.success(
        workflowFacade.batchUrge(instanceIds, AuthContextUtils.getUserId(), comment));
  }

  /**
   * P2-35: 一键通过 — 查询当前用户全部待办（上限 100 条）并逐一通过
   *
   * @return 统一响应结果，包含通过数量
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:passAll:lock", ttlSeconds = 5)
  @PostMapping("/task/passAll")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'passAll'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Integer> passAll() {
    return BaseResponse.success(
        taskService.passAll(AuthContextUtils.getUserId(), AuthContextUtils.getUsername()));
  }

  // ============== 查询统计 ==============

  /**
   * 待办任务查询
   *
   * @param page 页码
   * @param size 每页大小
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @return 统一响应结果，包含分页待办列表
   */
  @GetMapping("/task/todo")
  public PageResponse<List<FlowRunTaskVO>> todo(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime) {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    PageResponse<List<FlowRunTaskDO>> pageResult =
        taskService.pageTodo(userId, tenantId, flowCode, businessType, startTime, endTime, page, size);
    List<FlowRunTaskVO> vos = WorkflowConverter.INSTANT.flowRunTaskListToVO(pageResult.getData());
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
  }

  /**
   * 已办任务查询
   *
   * @param page 页码
   * @param size 每页大小
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @return 统一响应结果，包含分页已办列表
   */
  @GetMapping("/task/done")
  public PageResponse<List<FlowRunTaskVO>> done(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime) {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    PageResponse<List<FlowRunTaskDO>> pageResult =
        taskService.pageDone(userId, tenantId, flowCode, businessType, startTime, endTime, page, size);
    List<FlowRunTaskVO> vos = WorkflowConverter.INSTANT.flowRunTaskListToVO(pageResult.getData());
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
  }

  /**
   * P2-32: 超期任务查询
   *
   * @return 统一响应结果，包含超期任务列表
   */
  @GetMapping("/task/overdue")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<List<FlowRunTaskVO>> overdue() {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    List<FlowRunTaskDO> tasks = taskService.listOverdue(userId, tenantId);
    return BaseResponse.success(WorkflowConverter.INSTANT.flowRunTaskListToVO(tasks));
  }

  /**
   * 已办多维筛选
   *
   * @param page 页码
   * @param size 每页大小
   * @param flowCode 流程编码（可选）
   * @param businessType 业务类型（可选）
   * @param startTime 开始时间下界（可选）
   * @param endTime 开始时间上界（可选）
   * @param keyword 关键字（可选，匹配 title / businessNo）
   * @return 统一响应结果，包含分页已办列表
   */
  @GetMapping("/task/done/search")
  public PageResponse<List<FlowRunTaskVO>> doneSearch(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime,
      @RequestParam(required = false) String keyword) {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    PageResponse<List<FlowRunTaskDO>> pageResult =
        taskService.pageDoneSearch(
            userId, tenantId, flowCode, businessType, startTime, endTime, keyword, page, size);
    List<FlowRunTaskVO> vos = WorkflowConverter.INSTANT.flowRunTaskListToVO(pageResult.getData());
    return PageResponse.success(
        pageResult.getTotal(), pageResult.getPageNum(), pageResult.getPageSize(), vos);
  }

  /**
   * P2-31: 节点耗时统计
   *
   * @param flowCode 流程编码（可选）
   * @param startTime 统计开始时间（可选）
   * @param endTime 统计结束时间（可选）
   * @return 统一响应结果，包含节点耗时统计列表
   */
  @GetMapping("/stats/nodeDuration")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<List<Map<String, Object>>> nodeDurationStats(
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime) {
    return BaseResponse.success(taskService.nodeDurationStats(flowCode, startTime, endTime));
  }

  /**
   * 超期统计
   *
   * @param flowCode 流程编码（可选）
   * @param startTime 统计开始时间（可选）
   * @param endTime 统计结束时间（可选）
   * @return 统一响应结果，包含超期统计列表
   */
  @GetMapping("/stats/overdue")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<List<Map<String, Object>>> overdueStats(
      @RequestParam(required = false) String flowCode,
      @RequestParam(required = false) LocalDateTime startTime,
      @RequestParam(required = false) LocalDateTime endTime) {
    return BaseResponse.success(taskService.overdueStats(flowCode, startTime, endTime));
  }

  // ============== 辅助操作 ==============

  /**
   * GAP-P1: 减签 — 从会签任务中移除指定审批人
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:countersignRemove:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.countersignRemove", threshold = 50)
  @PostMapping("/task/countersignRemove")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'countersignRemove'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> countersignRemove(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    taskService.countersignRemove(dto);
    return BaseResponse.success();
  }

  /**
   * GAP-P2: 已阅 — 标记任务已阅
   *
   * @param taskId 任务 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:markRead:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.markRead", threshold = 50)
  @PostMapping("/task/{taskId}/read")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'markRead'")
  public BaseResponse<Void> markRead(@PathVariable String taskId) {
    String userId = AuthContextUtils.getUserId();
    taskService.markRead(taskId, userId);
    return BaseResponse.success();
  }

  /**
   * GAP-P2: 沟通 — 在任务下添加沟通评论
   *
   * @param dto 任务操作参数（需含 taskId + userId + comment）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:communicate:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.communicate", threshold = 50)
  @PostMapping("/task/communicate")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'communicate'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> communicate(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    taskService.communicate(dto);
    return BaseResponse.success();
  }

  /**
   * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
   *
   * @param dto 任务操作参数（需含 taskId + userId + comment）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:saveDraft:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.saveDraft", threshold = 50)
  @PostMapping("/task/saveDraft")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'saveDraft'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> saveDraft(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.saveDraft(dto);
    return BaseResponse.success();
  }

  /**
   * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
   *
   * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:addApprover:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.addApprover", threshold = 50)
  @PostMapping("/task/addApprover")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'addApprover'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> addApprover(@Valid @RequestBody FlowTaskOperateDTO dto) {
    dto.setUserId(AuthContextUtils.getUserId());
    dto.setUserName(AuthContextUtils.getUsername());
    workflowFacade.addApprover(dto);
    return BaseResponse.success();
  }

  /**
   * P1-3: 取回审批 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
   *
   * @param hisTaskId 历史任务 ID（ydsz_flow_his_task.id）
   * @param comment 取回说明（可选）
   * @return 统一响应结果，包含新创建的待办任务 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:retract:lock", ttlSeconds = 5)
  @PostMapping("/task/{hisTaskId}/retract")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'retract'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<String> retract(
      @PathVariable String hisTaskId, @RequestParam(required = false) String comment) {
    return BaseResponse.success(
        taskService.retract(hisTaskId, AuthContextUtils.getUserId(), comment));
  }

  /**
   * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
   *
   * @param taskId 任务 ID
   * @param reason 挂起原因（可选）
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:suspendTask:lock", ttlSeconds = 5)
  @PostMapping("/task/{taskId}/suspend")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.DISABLE,
      content = "'suspendTask'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> suspendTask(
      @PathVariable String taskId, @RequestParam(required = false) String reason) {
    workflowFacade.suspendTask(taskId, AuthContextUtils.getUserId(), reason);
    return BaseResponse.success();
  }

  /**
   * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
   *
   * @param taskId 任务 ID
   * @return 统一响应结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:activateTask:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.activateTask", threshold = 50)
  @PostMapping("/task/{taskId}/activate")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.ENABLE,
      content = "'activateTask'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Void> activateTask(@PathVariable String taskId) {
    workflowFacade.activateTask(taskId, AuthContextUtils.getUserId());
    return BaseResponse.success();
  }

  // ============== P1-7: WebSocket 待办数实时推送 ==============

  /**
   * P1-7: 查询当前用户的待办数（HTTP 拉模式，作为 WebSocket 推送的兜底）
   *
   * @return 包含 todoCount、userId、timestamp 的响应
   */
  @GetMapping("/todo/count")
  public BaseResponse<Map<String, Object>> myTodoCount() {
    String userId = AuthContextUtils.getUserId();
    if (userId == null) {
      return BaseResponse.success(Map.of("userId", 0, "todoCount", 0));
    }
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
    long count = tasks == null ? 0 : tasks.size();
    return BaseResponse.success(
        Map.of(
            "userId", userId,
            "todoCount", count,
            "timestamp", System.currentTimeMillis()));
  }

  /**
   * P1-7: 手动触发推送当前用户待办数到 WebSocket（前端重连后调一次同步）
   *
   * @return 是否成功
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:pushMyTodoCount:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowtask.pushMyTodoCount", threshold = 50)
  @PostMapping("/todo/pushMine")
  @Audit(
      module = "流程任务",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'pushMyTodoCount'")
  public BaseResponse<Boolean> pushMyTodoCount() {
    String userId = AuthContextUtils.getUserId();
    if (userId == null) {
      return BaseResponse.success(false);
    }
    todoCountPushService.pushTodoCount(userId);
    return BaseResponse.success(true);
  }

  // ============== P1-8: 加签历史独立视图 ==============

  /** 加签类型常量 */
  private static final List<String> COUNTERSIGN_ACTIONS =
      List.of(
          "COUNTERSIGN_BEFORE", "COUNTERSIGN_AFTER", "COUNTERSIGN_PARALLEL", "COUNTERSIGN_REMOVE");

  /**
   * 查询指定流程实例的加签历史记录。
   *
   * @param instanceId 流程实例 ID
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/countersign/instance/{instanceId}")
  @Operation(summary = "查询流程实例的加签历史")
  public BaseResponse<List<Map<String, Object>>> countersignByInstanceId(
      @PathVariable String instanceId,
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(instanceId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  /**
   * 查询指定任务的加签历史记录。
   *
   * @param taskId 任务 ID
   * @param pageNo 页码（默认 1）
   * @param pageSize 每页大小（默认 20，上限 100）
   * @return 加签历史列表
   */
  @GetMapping("/countersign/task/{taskId}")
  @Operation(summary = "查询任务的加签历史")
  public BaseResponse<List<Map<String, Object>>> countersignByTaskId(
      @PathVariable String taskId,
      @RequestParam(defaultValue = "1") @Min(1) int pageNo,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    List<FlowAuditLogDO> logs = auditLogMapper.selectByTaskId(taskId);
    List<Map<String, Object>> filtered =
        logs == null
            ? List.of()
            : logs.stream()
                .filter(log -> COUNTERSIGN_ACTIONS.contains(log.getAction()))
                .map(this::toCountersignVO)
                .toList();
    int total = filtered.size();
    int fromIndex = Math.min((pageNo - 1) * pageSize, total);
    int toIndex = Math.min(fromIndex + pageSize, total);
    List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);
    return PageResponse.success((long) total, (long) pageNo, (long) pageSize, pageData);
  }

  /** 将审计日志转换为加签视图 VO */
  private Map<String, Object> toCountersignVO(FlowAuditLogDO log) {
    Map<String, Object> vo = new LinkedHashMap<>();
    vo.put("id", log.getId());
    vo.put("instanceId", log.getInstanceId());
    vo.put("taskId", log.getTaskId());
    vo.put("flowCode", log.getFlowCode());
    vo.put("nodeCode", log.getNodeCode());
    vo.put("nodeName", log.getNodeName());
    vo.put("action", log.getAction());
    vo.put("actionName", getActionName(log.getAction()));
    vo.put("operatorId", log.getOperatorId());
    vo.put("operatorName", log.getOperatorName());
    vo.put("targetId", log.getTargetId());
    vo.put("targetName", log.getTargetName());
    vo.put("comment", log.getComment());
    vo.put("operatedAt", log.getOperatedAt());
    return vo;
  }

  /** 获取加签操作名称 */
  private String getActionName(String action) {
    if (action == null) {
      return "未知";
    }
    return switch (action) {
      case "COUNTERSIGN_BEFORE" -> "前加签";
      case "COUNTERSIGN_AFTER" -> "后加签";
      case "COUNTERSIGN_PARALLEL" -> "并加签";
      case "COUNTERSIGN_REMOVE" -> "减签";
      default -> "未知加签操作";
    };
  }

  // ============== P1-4: 长期授权委派 ==============

  /**
   * P1-4: 创建长期授权委派
   *
   * @param dto 授权参数
   * @return 授权记录 ID
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:createDelegateAuth:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.createDelegateAuth", threshold = 50)
  @PostMapping("/delegateAuth/create")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'createDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<String> createDelegateAuth(@Valid @RequestBody FlowDelegateAuthPostDTO dto) {
    var auth = WorkflowConverter.INSTANT.postDtoToEntity(dto);
    if (auth.getOwnerUserId() == null) {
      auth.setOwnerUserId(AuthContextUtils.getUserId());
    }
    String id = delegateAuthService.create(auth);
    return BaseResponse.success(id);
  }

  /**
   * P1-4: 撤回授权。
   *
   * @param id 授权记录 ID
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:revokeDelegateAuth:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.revokeDelegateAuth", threshold = 50)
  @PostMapping("/delegateAuth/{id}/revoke")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'revokeDelegateAuth'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<Void> revokeDelegateAuth(@PathVariable String id) {
    String ownerId = AuthContextUtils.getUserId();
    delegateAuthService.revoke(id, ownerId);
    return BaseResponse.success();
  }

  /**
   * P1-4: 启用/停用授权。
   *
   * @param id 授权记录 ID
   * @param status 目标状态
   * @return 空响应
   */
  @Idempotent(
      key = "ydsz:workflow:FlowTaskController:updateDelegateAuthStatus:lock",
      ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowdelegate.updateDelegateAuthStatus", threshold = 50)
  @PostMapping("/delegateAuth/{id}/status")
  @Audit(
      module = "流程委派",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'updateDelegateAuthStatus'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DELEGATE_MANAGE)
  public BaseResponse<Void> updateDelegateAuthStatus(
      @PathVariable String id, @RequestParam String status) {
    String operatorId = AuthContextUtils.getUserId();
    delegateAuthService.updateStatus(id, status, operatorId);
    return BaseResponse.success();
  }

  /**
   * P1-4: 查"我设置的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/mine")
  public BaseResponse<List<FlowDelegateAuthVO>> listMyDelegateAuths(
      @RequestParam(required = false) String status) {
    String ownerId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowDelegateAuthListToVO(
            delegateAuthService.listMine(ownerId, tenantId, status)));
  }

  /**
   * P1-4: 查"代理给我的"授权列表。
   *
   * @param status 状态筛选（可选）
   * @return 授权列表
   */
  @GetMapping("/delegateAuth/asDelegate")
  public BaseResponse<List<FlowDelegateAuthVO>> listAsDelegate(
      @RequestParam(required = false) String status) {
    String delegateUserId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowDelegateAuthListToVO(
            delegateAuthService.listAsDelegate(delegateUserId, tenantId, status)));
  }

  // ============== P0-3: 抄送中心 ==============

  /**
   * P0-3: 抄送中心 - 分页查询
   *
   * @param query 查询条件
   * @return 抄送分页结果
   */
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @RateLimit(resource = "workflow.FlowCcDO.pageCc", threshold = 50)
  @Idempotent(key = "ydsz:workflow:FlowTaskController:pageCc:lock", ttlSeconds = 5)
  @PostMapping("/cc/page")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'pageCc'")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CC_VIEW)
  public BaseResponse<List<FlowCcDO>> pageCc(@Valid @RequestBody FlowCcQueryDTO query) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    int pageNo = query.getPageNum();
    int pageSize = query.getPageSize();
    return ccService.listCcByUser(
        userId, query.getReadStatus(), query.getFlowCode(), tenantId, pageNo, pageSize);
  }

  /**
   * P0-3: 抄送未读数（前端导航栏徽标）。
   *
   * @return 未读抄送条数
   */
  @GetMapping("/cc/unreadCount")
  public BaseResponse<Long> ccUnreadCount() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(ccService.countUnread(userId, tenantId));
  }

  /**
   * P0-3: 抄送标记已读。
   *
   * @param id 抄送记录 ID
   * @return 操作结果
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:ccMarkRead:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCcDO.ccMarkRead", threshold = 50)
  @PostMapping("/cc/{id}/read")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ccMarkRead'")
  public BaseResponse<Boolean> ccMarkRead(@PathVariable String id) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    ccService.markRead(tenantId, userId, id);
    return BaseResponse.success(Boolean.TRUE);
  }

  /**
   * P0-3: 抄送全部标记已读。
   *
   * @return 已标记已读的记录数
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:ccMarkAllRead:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowCcDO.ccMarkAllRead", threshold = 50)
  @PostMapping("/cc/readAll")
  @Audit(
      module = "流程抄送",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'ccMarkAllRead'")
  public BaseResponse<Integer> ccMarkAllRead() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(ccService.markAllRead(tenantId, userId));
  }

  // ============== 审批附件 ==============

  /**
   * 查询任务附件。
   *
   * @param taskId 任务 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/task/{taskId}")
  public BaseResponse<List<FlowAttachmentVO>> listByTask(@PathVariable String taskId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowAttachmentListToVO(attachmentService.listByTask(taskId)));
  }

  /**
   * 查询实例附件。
   *
   * @param instanceId 流程实例 ID
   * @return 附件列表
   */
  @GetMapping("/attachment/instance/{instanceId}")
  public BaseResponse<List<FlowAttachmentVO>> listByInstance(@PathVariable String instanceId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.flowAttachmentListToVO(
            attachmentService.listByInstance(instanceId)));
  }

  /**
   * 删除附件（逻辑删除）
   *
   * @param attachmentId 附件 ID
   * @param operatorId 操作人 ID（用于审计日志）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowTaskController:delete:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.FlowAttachmentDO.delete", threshold = 50)
  @DeleteMapping("/attachment/{attachmentId}")
  @Audit(
      module = "流程附件",
      type = AuditType.OPERATION,
      action = AuditAction.DELETE,
      content = "'delete'")
  public BaseResponse<Void> delete(
      @PathVariable String attachmentId, @RequestParam String operatorId) {
    attachmentService.delete(attachmentId, operatorId);
    return BaseResponse.success();
  }

  /**
   * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
   *
   * @param attachmentId 附件 ID
   * @return 统一响应结果，包含预览 VO
   */
  @GetMapping("/attachment/{attachmentId}/preview")
  @Operation(summary = "附件在线预览（根据文件类型返回预览策略）")
  public BaseResponse<FlowAttachmentPreviewVO> preview(@PathVariable String attachmentId) {
    return BaseResponse.success(attachmentService.previewAttachment(attachmentId));
  }
}
