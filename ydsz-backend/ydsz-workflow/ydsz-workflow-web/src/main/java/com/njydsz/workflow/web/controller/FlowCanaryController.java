package com.njydsz.workflow.web.controller.ai;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.server.service.FlowCanaryService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度发布 Controller
 *
 * <p>P3-1: 流程灰度发布接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-canary", description = "工作流灰度发布接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowCanaryController {

    /** P3-1: 灰度发布服务 */
    private final FlowCanaryService canaryService;

    /**
     * P3-1: 启动灰度发布
     *
     * <p>将指定定义标记为灰度版，按 initialPercent 切流。
     *
     * <p>P0-1 修复：操作人 ID/姓名从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param definitionId   灰度版定义 ID
     * @param initialPercent 初始灰度比例（0-100）
     * @param strategy       切流策略：USER_HASH / RANDOM / WHITELIST
     * @param note           备注
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:publishCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/publish")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> publishCanary(
            @PathVariable String definitionId,
            @RequestParam(defaultValue = "10") int initialPercent,
            @RequestParam(defaultValue = "USER_HASH") String strategy,
            @RequestParam(required = false) String note) {
        canaryService.publishCanary(definitionId, initialPercent, strategy,
                AuthContext.getUserId(), AuthContext.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * P3-1: 调整灰度比例（逐步放量/缩量）
     *
     * <p>P0-1 修复：操作人 ID/姓名从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param definitionId 定义 ID
     * @param newPercent   新比例（0-100）
     * @param note         备注
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:adjustCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/adjust")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> adjustCanary(
            @PathVariable String definitionId,
            @RequestParam int newPercent,
            @RequestParam(required = false) String note) {
        canaryService.adjustCanaryPercent(definitionId, newPercent,
                AuthContext.getUserId(), AuthContext.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * P3-1: 全量发布 - 灰度版晋升为稳定版
     *
     * <p>P0-1 修复：操作人 ID/姓名从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param definitionId 灰度版定义 ID
     * @param note         备注
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:promoteCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/promote")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> promoteCanary(
            @PathVariable String definitionId,
            @RequestParam(required = false) String note) {
        canaryService.promoteCanary(definitionId,
                AuthContext.getUserId(), AuthContext.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * P3-1: 灰度回滚
     *
     * <p>P0-1 修复：操作人 ID/姓名从 SecurityContext 获取，不再暴露为 URL 参数。
     *
     * @param definitionId 灰度版定义 ID
     * @param note         备注（含回滚原因）
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowCanaryController:rollbackCanary:lock", ttlSeconds = 5)
    @PostMapping("/canary/{definitionId}/rollback")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_CANARY_MANAGE)
    public BaseResponse<Void> rollbackCanary(
            @PathVariable String definitionId,
            @RequestParam(required = false) String note) {
        canaryService.rollbackCanary(definitionId,
                AuthContext.getUserId(), AuthContext.getUsername(), note);
        return BaseResponse.success();
    }

    /**
     * P3-1: 查询某 flowCode 的灰度发布历史
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可选）
     * @return rollout 日志列表
     */
    @GetMapping("/canary/{flowCode}/rolloutLog")
    public BaseResponse<List<Map<String, Object>>> rolloutLog(
            @PathVariable String flowCode,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(canaryService.listCanaryRolloutLog(flowCode, tid));
    }
}
