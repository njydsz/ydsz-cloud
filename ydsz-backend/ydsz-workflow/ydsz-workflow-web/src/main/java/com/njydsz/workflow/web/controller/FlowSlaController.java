package com.njydsz.workflow.web.controller.analytics;

import org.springframework.validation.annotation.Validated;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTaskService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SLA 超时自动策略 Controller
 *
 * <p>P1-6: SLA 扫描与处理接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-sla", description = "工作流 SLA 接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowSlaController {

    /** P1-6: SLA 超时自动策略服务 */
    private final FlowSlaService slaService;
    /** 任务服务（slaProcess 中按 id 查任务） */
    private final FlowTaskService taskService;

    /**
     * P1-6: 手动触发 SLA 扫描（管理后台调试用，cronjob 默认每 60s 自动扫描）
     *
     * @return 本轮扫描处理的任务数
     */
    @Idempotent(key = "ydsz:workflow:FlowSlaController:slaScan:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowsla.slaScan", threshold = 50)
    @PostMapping("/sla/scan")
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
    @Idempotent(key = "ydsz:workflow:FlowSlaController:slaProcess:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowsla.slaProcess", threshold = 50)
    @PostMapping("/sla/process/{taskId}")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_SLA_CONFIG)
    public BaseResponse<Boolean> slaProcess(@PathVariable String taskId) {
        FlowRunTask task = taskService.getById(taskId);
        if (task == null) {
            return BaseResponse.error(BaseResultCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        boolean ok = slaService.processOverdue(task);
        return BaseResponse.success(ok);
    }
}
