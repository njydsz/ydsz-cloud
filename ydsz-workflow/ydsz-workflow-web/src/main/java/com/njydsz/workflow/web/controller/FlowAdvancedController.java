package com.njydsz.workflow.web.controller.instance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.domain.vo.StringVO;
import com.njydsz.workflow.server.engine.FlowUrgeLimiter;
import com.njydsz.workflow.server.service.FlowInstanceMergeService;
import com.njydsz.workflow.server.service.FlowOfflineAutoForwardService;
import com.njydsz.workflow.server.service.FlowReportService;
import com.njydsz.workflow.server.service.impl.instance.FlowAssigneeDedupService;
import com.njydsz.workflow.server.service.impl.instance.FlowCountersignDynamicService;

/**
 * 工作流高级功能 Controller（P2-4 / P2-5 / P2-6 / P2-7 / P2-8）
 *
 * <p>聚合工作流「高级能力」HTTP API，覆盖以下五大子领域：
 *
 * <ul>
 *   <li><b>P2-4 审批数据周报/月报</b>：自动生成并推送审批效率报告
 *   <li><b>P2-5 多实例合并审批</b>：把多个相同流程的实例合并为一组统一审批
 *   <li><b>P2-6 会签动态完成条件</b>：运行时调整会签的通过率 / 人数阈值
 *   <li><b>P2-7 跨节点办理人去重</b>：检查 / 查询实例已审批人
 *   <li><b>P2-8 催办限流可视化</b>：查询催办冷却剩余时间
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/advanced/**}
 *
 * <p><b>权限模型：</b>所有写接口通过 {@link AuthApiPermission} 校验 {@link
 * PermissionCodes#WORKFLOW_INSTANCE_CONTROL} 或 {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限码。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（避免双击重复触发）
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>读接口（dedup / urge / mergeable）无锁，可高 QPS 调用
 * </ul>
 *
 * <p><b>设计原则：</b>Controller 仅做参数透传、权限校验、VO 转换； 报表生成、实例合并、会签阈值计算、催办限流逻辑下沉到对应 Service。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowReportService 周报 / 月报服务
 * @see FlowInstanceMergeService 多实例合并服务
 * @see FlowCountersignDynamicService 动态会签服务
 * @see FlowAssigneeDedupService 跨节点办理人去重服务
 * @see FlowUrgeLimiter 催办限流器
 */
@Slf4j
@RestController
@Tag(name = "workflow-advanced", description = "工作流高级功能接口")
@RequestMapping("/api/v1/workflow/advanced")
@RequiredArgsConstructor
public class FlowAdvancedController {

  /** 周报 / 月报服务 */
  private final FlowReportService reportService;

  /** 多实例合并服务 */
  private final FlowInstanceMergeService mergeService;

  /** 动态会签服务 */
  private final FlowCountersignDynamicService countersignDynamicService;

  /** 跨节点办理人去重服务 */
  private final FlowAssigneeDedupService dedupService;

  /** 催办限流器 */
  private final FlowUrgeLimiter urgeLimiter;

  /** 离线代理自动转发服务，负责离线用户待办的自动/手动转发 */
  private final FlowOfflineAutoForwardService offlineAutoForwardService;

  // ==================== P2-4: 审批数据周报/月报 ====================

  /**
   * P2-4: 获取审批周报数据
   *
   * <p>生成当前租户的本周审批统计：发起数 / 完成数 / 通过率 / 平均耗时 / Top 5 审批人。
   *
   * <p>报表数据按 tenantId 隔离。
   *
   * @return 周报数据 Map（含发起数、完成数、通过率、平均耗时、Top 5 审批人等字段）
   */
  @GetMapping("/report/weekly")
  @Operation(summary = "P2-4: 获取周报数据")
  public BaseResponse<Map<String, Object>> weeklyReport() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(reportService.generateWeeklyReport(tenantId));
  }

  /**
   * P2-4: 获取审批月报数据
   *
   * <p>生成当前租户的本月审批统计，统计维度同周报。
   *
   * <p>报表数据按 tenantId 隔离。
   *
   * @return 月报数据 Map
   */
  @GetMapping("/report/monthly")
  @Operation(summary = "P2-4: 获取月报数据")
  public BaseResponse<Map<String, Object>> monthlyReport() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(reportService.generateMonthlyReport(tenantId));
  }

  /**
   * P2-4: 推送审批周报
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>触发周报推送：按租户下管理员配置的接收人列表，通过 {@code ydsz-message} 通知中心 发送 IM 消息（钉钉 / 飞书 / 企微）+ 邮件。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_INSTANCE_CONTROL} 权限。
   *
   * @return 是否推送成功
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:sendWeekly:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowadvanced.sendWeekly", threshold = 50)
  @PostMapping("/report/weekly/send")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'sendWeekly'")
  @Operation(summary = "P2-4: 推送周报")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Boolean> sendWeekly() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(reportService.sendWeeklyReport(tenantId));
  }

  /**
   * P2-4: 推送审批月报
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>触发月报推送，逻辑同周报推送。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_INSTANCE_CONTROL} 权限。
   *
   * @return 是否推送成功
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:sendMonthly:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowadvanced.sendMonthly", threshold = 50)
  @PostMapping("/report/monthly/send")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'sendMonthly'")
  @Operation(summary = "P2-4: 推送月报")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Boolean> sendMonthly() {
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(reportService.sendMonthlyReport(tenantId));
  }

  // ==================== P2-5: 多实例合并审批 ====================

  /**
   * P2-5: 合并多个流程实例
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>把多个相同流程的运行中实例合并为一组，后续审批人可对整组合并操作（通过 / 驳回一次性完成）。
   *
   * <p>典型场景：HR 批量处理多份转正申请，财务批量审批多笔同类型报销。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限。
   *
   * @param instanceIds 待合并的实例 ID 列表
   * @return 合并组 ID（VO 形式）
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:merge:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowadvanced.merge", threshold = 50)
  @PostMapping("/merge")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'merge'")
  @Operation(summary = "P2-5: 合并多个流程实例")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<StringVO> merge(@RequestParam List<String> instanceIds) {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(
        WorkflowConverter.INSTANT.entityToVO(
            mergeService.mergeInstances(instanceIds, userId, tenantId)));
  }

  /**
   * P2-5: 查询合并组详情
   *
   * <p>返回合并组元数据 + 组内实例列表 + 合并时间 / 操作人 / 状态。
   *
   * @param mergeGroupId 合并组 ID
   * @return 合并组详情 Map
   */
  @GetMapping("/merge/{mergeGroupId}")
  @Operation(summary = "P2-5: 查询合并组详情")
  public BaseResponse<Map<String, Object>> getMergeGroup(@PathVariable String mergeGroupId) {
    return BaseResponse.success(mergeService.getMergeGroup(mergeGroupId));
  }

  /**
   * P2-5: 批量通过合并组
   *
   * <p>幂等保护 5 秒。
   *
   * <p>对合并组内全部实例一次性执行「通过」操作，写入审计日志。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限。
   *
   * @param mergeGroupId 合并组 ID
   * @param comment 审批意见（可选）
   * @return 成功通过实例数
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:mergePass:lock", ttlSeconds = 5)
  @PostMapping("/merge/{mergeGroupId}/pass")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'mergePass'")
  @Operation(summary = "P2-5: 批量通过合并组")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Integer> mergePass(
      @PathVariable String mergeGroupId, @RequestParam(required = false) String comment) {
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(mergeService.batchPassMerged(mergeGroupId, userId, comment));
  }

  /**
   * P2-5: 批量驳回合并组
   *
   * <p>幂等保护 5 秒。
   *
   * <p>对合并组内全部实例一次性执行「驳回」操作，写入审计日志。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_TASK_OPERATE} 权限。
   *
   * @param mergeGroupId 合并组 ID
   * @param comment 驳回意见（可选，建议填写驳回原因）
   * @return 成功驳回实例数
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:mergeReject:lock", ttlSeconds = 5)
  @PostMapping("/merge/{mergeGroupId}/reject")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.REJECT,
      content = "'mergeReject'")
  @Operation(summary = "P2-5: 批量驳回合并组")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_TASK_OPERATE)
  public BaseResponse<Integer> mergeReject(
      @PathVariable String mergeGroupId, @RequestParam(required = false) String comment) {
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(mergeService.batchRejectMerged(mergeGroupId, userId, comment));
  }

  /**
   * P2-5: 查询当前用户可合并的实例列表
   *
   * <p>筛选条件：当前用户作为审批人 / 发起人、流程编码相同、状态为 RUNNING、 尚未被合并、且不在其它合并组内的实例。
   *
   * <p>用于「合并审批」页面的待合并实例候选列表。
   *
   * @return 可合并的实例列表
   */
  @GetMapping("/mergeable")
  @Operation(summary = "P2-5: 查询可合并的实例列表")
  public BaseResponse<List<Map<String, Object>>> mergeable() {
    String userId = AuthContextUtils.getUserId();
    String tenantId = AuthContextUtils.getTenantIdOrDefault();
    return BaseResponse.success(mergeService.listMergeable(userId, tenantId));
  }

  // ==================== P2-6: 会签动态完成条件 ====================

  /**
   * P2-6: 动态修改会签通过率阈值
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>运行时调整进行中会签任务的「通过率」完成条件（如把 80% 调到 60%）。
   *
   * <p>后续会签投票按新阈值判定：达成即推进流程，未达成继续等。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_INSTANCE_CONTROL} 权限。
   *
   * @param taskId 会签任务 ID
   * @param votePassRate 新的通过率阈值（0~1 之间的 BigDecimal）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:updateCondition:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowadvanced.updateVotePassRate", threshold = 50)
  @PostMapping("/countersign/{taskId}/votePassRate")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'updateVotePassRate'")
  @Operation(summary = "P2-6: 动态修改会签通过率阈值")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> updateVotePassRate(
      @PathVariable String taskId, @RequestParam BigDecimal votePassRate) {
    String userId = AuthContextUtils.getUserId();
    countersignDynamicService.updateCompletionCondition(taskId, votePassRate, userId);
    return BaseResponse.success();
  }

  /**
   * P2-6: 动态修改会签所需通过人数
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>运行时调整会签任务的「通过人数」完成条件（如把 3 人通过调到 2 人通过）。
   *
   * <p>与 {@link #updateVotePassRate} 二选一，互斥生效。
   *
   * <p>要求 {@link PermissionCodes#WORKFLOW_INSTANCE_CONTROL} 权限。
   *
   * @param taskId 会签任务 ID
   * @param approveCount 新的通过人数阈值（≥1 的整数）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:updateApproveCount:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowadvanced.updateApproveCount", threshold = 50)
  @PostMapping("/countersign/{taskId}/approveCount")
  @Audit(
      module = "高级功能",
      type = AuditType.OPERATION,
      action = AuditAction.APPROVE,
      content = "'updateApproveCount'")
  @Operation(summary = "P2-6: 动态修改会签所需通过人数")
  @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
  public BaseResponse<Void> updateApproveCount(
      @PathVariable String taskId, @RequestParam Integer approveCount) {
    String userId = AuthContextUtils.getUserId();
    countersignDynamicService.updateApproveCount(taskId, approveCount, userId);
    return BaseResponse.success();
  }

  // ==================== P2-7: 跨节点办理人去重 ====================

  /**
   * P2-7: 检查用户是否已审批过该实例
   *
   * <p>用于前端按钮置灰：审批面板加载时调用，若已审批则禁用「通过 / 驳回」按钮。
   *
   * @param instanceId 流程实例 ID
   * @param userId 用户 ID
   * @return 是否已审批
   */
  @GetMapping("/dedup/{instanceId}/check/{userId}")
  @Operation(summary = "P2-7: 检查用户是否已审批过")
  public BaseResponse<Boolean> hasApproved(
      @PathVariable String instanceId, @PathVariable String userId) {
    return BaseResponse.success(dedupService.hasAlreadyApproved(instanceId, userId));
  }

  /**
   * P2-7: 获取实例已审批人列表
   *
   * <p>查询该实例的全部历史审批人（含跨节点），用于审批面板 / 审批轨迹展示。
   *
   * @param instanceId 流程实例 ID
   * @return 已审批人 ID 列表 VO
   */
  @GetMapping("/dedup/{instanceId}/approvedUsers")
  @Operation(summary = "P2-7: 获取实例已审批人列表")
  public BaseResponse<List<StringVO>> approvedUsers(@PathVariable String instanceId) {
    return BaseResponse.success(
        WorkflowConverter.INSTANT.stringListToVO(
            dedupService.getApprovedUserIds(instanceId).stream().toList()));
  }

  // ==================== P2-8: 催办限流可视化 ====================

  /**
   * P2-8: 查询催办剩余冷却时间
   *
   * <p>对标钉钉/飞书审批「催一下」按钮的禁用逻辑：催办后默认冷却期 30 分钟， 此端点返回当前用户对该实例的剩余冷却秒数 / 是否可催办。
   *
   * <p>前端依据返回结果置灰或启用催办按钮。
   *
   * @param instanceId 流程实例 ID
   * @return Map（含 canUrge / remainingSeconds / cooldownSeconds / remainingMinutes 字段）
   */
  @GetMapping("/urge/cooldown/{instanceId}")
  @Operation(summary = "P2-8: 查询催办剩余冷却时间")
  public BaseResponse<Map<String, Object>> urgeCooldown(@PathVariable String instanceId) {
    String userId = AuthContextUtils.getUserId();
    long cooldownSeconds = FlowUrgeLimiter.DEFAULT_COOLDOWN_SECONDS;
    long remaining = resolveCooldownRemaining(userId, instanceId);
    boolean canUrge = remaining <= 0;
    return BaseResponse.success(
        Map.of(
            "canUrge", canUrge,
            "remainingSeconds", remaining,
            "cooldownSeconds", cooldownSeconds,
            "remainingMinutes", remaining / 60));
  }

  /**
   * 解析催办剩余冷却秒数。
   *
   * <p>若 instanceId 非数字或限流器查询返回空，则返回 0（表示无冷却）。
   *
   * @param userId 当前用户 ID
   * @param instanceId 流程实例 ID（可能非数字）
   * @return 剩余冷却秒数
   */
  private long resolveCooldownRemaining(String userId, String instanceId) {
    long instanceIdLong;
    try {
      instanceIdLong = Long.parseLong(instanceId);
    } catch (NumberFormatException e) {
      return 0;
    }
    List<Long> ttls = urgeLimiter.getCooldownSeconds(userId, List.of(instanceIdLong), "INSTANCE");
    if (ttls != null && !ttls.isEmpty()) {
      return ttls.get(0);
    }
    return 0;
  }

  // ==================== 离线代理自动转发 ====================

  /**
   * 按代理授权规则自动转发已有待办
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>业务流程：
   *
   * <ol>
   *   <li>读取 {@code authId} 对应的代理授权（含 {@code ownerUserId}、{@code delegateUserId}、生效时间）
   *   <li>扫描 {@code ownerUserId} 全部 PENDING/CLAIMED 任务
   *   <li>事务内批量改派 + 写委派日志
   *   <li>触发通知给代理人
   * </ol>
   *
   * @param authId 代理授权记录 ID
   * @return 成功转发的任务数
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:autoForward:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowofflineforward.autoForward", threshold = 50)
  @PostMapping("/offlineForward/auto")
  @Audit(
      module = "离线转发",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'autoForward'")
  @Operation(summary = "按代理授权规则自动转发已有待办")
  public BaseResponse<Integer> autoForward(@RequestParam String authId) {
    return BaseResponse.success(offlineAutoForwardService.autoForwardByAuth(authId));
  }

  /**
   * 手动触发离线转发
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p>适用于：HR 标记员工离职 / 管理员临时调岗等需要<b>立即</b>把某人待办转给指定代理人的场景。
   *
   * @param userId 离线用户 ID（源用户）
   * @param delegateUserId 代理人 ID（目标用户）
   * @return 成功转发的任务数
   */
  @Idempotent(key = "ydsz:workflow:FlowAdvancedController:manualForward:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowofflineforward.manualForward", threshold = 50)
  @PostMapping("/offlineForward/manual")
  @Audit(
      module = "离线转发",
      type = AuditType.OPERATION,
      action = AuditAction.GRANT,
      content = "'manualForward'")
  @Operation(summary = "手动触发离线转发")
  public BaseResponse<Integer> manualForward(
      @RequestParam String userId, @RequestParam String delegateUserId) {
    String operatorId = AuthContextUtils.getUserId();
    return BaseResponse.success(
        offlineAutoForwardService.manualForward(userId, delegateUserId, operatorId));
  }
}
