package com.njydsz.pmis.workflow.controller.instance;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.engine.FlowUrgeLimiter;
import com.njydsz.pmis.workflow.service.analytics.FlowReportService;
import com.njydsz.pmis.workflow.service.impl.instance.FlowAssigneeDedupService;
import com.njydsz.pmis.workflow.service.impl.instance.FlowCountersignDynamicService;
import com.njydsz.pmis.workflow.service.instance.FlowInstanceMergeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 工作流高级功能 Controller
 *
 * <p>P2-4/P2-5/P2-6/P2-7/P2-8 高级功能 API 聚合。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-advanced", description = "工作流高级功能接口")
@RequestMapping("/workflow/advanced")
@RequiredArgsConstructor
public class FlowAdvancedController {

    private final FlowReportService reportService;
    private final FlowInstanceMergeService mergeService;
    private final FlowCountersignDynamicService countersignDynamicService;
    private final FlowAssigneeDedupService dedupService;
    private final FlowUrgeLimiter urgeLimiter;

    // ==================== P2-4: 审批数据周报/月报 ====================

    @GetMapping("/report/weekly")
    @Operation(summary = "P2-4: 获取周报数据")
    public Result<Map<String, Object>> weeklyReport() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(reportService.generateWeeklyReport(tenantId));
    }

    @GetMapping("/report/monthly")
    @Operation(summary = "P2-4: 获取月报数据")
    public Result<Map<String, Object>> monthlyReport() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(reportService.generateMonthlyReport(tenantId));
    }

    @Idempotent(key = "flowAdvanced:sendWeekly", ttlSeconds = 10, message = "请勿重复提交")
    @PostMapping("/report/weekly/send")
    @Operation(summary = "P2-4: 推送周报")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Boolean> sendWeekly() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(reportService.sendWeeklyReport(tenantId));
    }

    @Idempotent(key = "flowAdvanced:sendMonthly", ttlSeconds = 10, message = "请勿重复提交")
    @PostMapping("/report/monthly/send")
    @Operation(summary = "P2-4: 推送月报")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Boolean> sendMonthly() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(reportService.sendMonthlyReport(tenantId));
    }

    // ==================== P2-5: 多实例合并审批 ====================

    @Idempotent(key = "flowAdvanced:merge", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/merge")
    @Operation(summary = "P2-5: 合并多个流程实例")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<String> merge(@RequestParam List<String> instanceIds) {
        String userId = SecurityContext.getUserId();
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(mergeService.mergeInstances(instanceIds, userId, tenantId));
    }

    @GetMapping("/merge/{mergeGroupId}")
    @Operation(summary = "P2-5: 查询合并组详情")
    public Result<Map<String, Object>> getMergeGroup(@PathVariable String mergeGroupId) {
        return Result.ok(mergeService.getMergeGroup(mergeGroupId));
    }

    @Idempotent(key = "flowAdvanced:mergePass", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/merge/{mergeGroupId}/pass")
    @Operation(summary = "P2-5: 批量通过合并组")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Integer> mergePass(@PathVariable String mergeGroupId,
                                       @RequestParam(required = false) String comment) {
        String userId = SecurityContext.getUserId();
        return Result.ok(mergeService.batchPassMerged(mergeGroupId, userId, comment));
    }

    @Idempotent(key = "flowAdvanced:mergeReject", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/merge/{mergeGroupId}/reject")
    @Operation(summary = "P2-5: 批量驳回合并组")
    @PrePermission(PermissionCodes.WORKFLOW_TASK_OPERATE)
    public Result<Integer> mergeReject(@PathVariable String mergeGroupId,
                                          @RequestParam(required = false) String comment) {
        String userId = SecurityContext.getUserId();
        return Result.ok(mergeService.batchRejectMerged(mergeGroupId, userId, comment));
    }

    @GetMapping("/mergeable")
    @Operation(summary = "P2-5: 查询可合并的实例列表")
    public Result<List<Map<String, Object>>> mergeable() {
        String userId = SecurityContext.getUserId();
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        return Result.ok(mergeService.listMergeable(userId, tenantId));
    }

    // ==================== P2-6: 会签动态完成条件 ====================

    @Idempotent(key = "flowAdvanced:updateCondition", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/countersign/{taskId}/votePassRate")
    @Operation(summary = "P2-6: 动态修改会签通过率阈值")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> updateVotePassRate(@PathVariable String taskId,
                                             @RequestParam BigDecimal votePassRate) {
        String userId = SecurityContext.getUserId();
        countersignDynamicService.updateCompletionCondition(taskId, votePassRate, userId);
        return Result.ok();
    }

    @Idempotent(key = "flowAdvanced:updateApproveCount", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/countersign/{taskId}/approveCount")
    @Operation(summary = "P2-6: 动态修改会签所需通过人数")
    @PrePermission(PermissionCodes.WORKFLOW_INSTANCE_CONTROL)
    public Result<Void> updateApproveCount(@PathVariable String taskId,
                                             @RequestParam Integer approveCount) {
        String userId = SecurityContext.getUserId();
        countersignDynamicService.updateApproveCount(taskId, approveCount, userId);
        return Result.ok();
    }

    // ==================== P2-7: 跨节点办理人去重 ====================

    @GetMapping("/dedup/{instanceId}/check/{userId}")
    @Operation(summary = "P2-7: 检查用户是否已审批过")
    public Result<Boolean> hasApproved(@PathVariable String instanceId,
                                         @PathVariable String userId) {
        return Result.ok(dedupService.hasAlreadyApproved(instanceId, userId));
    }

    @GetMapping("/dedup/{instanceId}/approvedUsers")
    @Operation(summary = "P2-7: 获取实例已审批人列表")
    public Result<List<String>> approvedUsers(@PathVariable String instanceId) {
        return Result.ok(dedupService.getApprovedUserIds(instanceId).stream().toList());
    }

    // ==================== P2-8: 催办限流可视化 ====================

    @GetMapping("/urge/cooldown/{instanceId}")
    @Operation(summary = "P2-8: 查询催办剩余冷却时间")
    public Result<Map<String, Object>> urgeCooldown(@PathVariable String instanceId) {
        String userId = SecurityContext.getUserId();
        long cooldownSeconds = FlowUrgeLimiter.DEFAULT_COOLDOWN_SECONDS;
        long remaining = 0;
        try {
            // 尝试获取剩余 TTL
            List<Long> ttls = urgeLimiter.getCooldownSeconds(userId,
                    List.of(Long.parseLong(instanceId)), "INSTANCE");
            if (ttls != null && !ttls.isEmpty()) {
                remaining = ttls.get(0);
            }
        } catch (NumberFormatException e) {
            // instanceId 不是数字，返回 0
            remaining = 0;
        }
        boolean canUrge = remaining <= 0;
        return Result.ok(Map.of(
                "canUrge", canUrge,
                "remainingSeconds", remaining,
                "cooldownSeconds", cooldownSeconds,
                "remainingMinutes", remaining / 60
        ));
    }
}
