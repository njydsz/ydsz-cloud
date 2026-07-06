package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.service.FlowSlaService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * SLA 超时自动策略 Controller
 *
 * <p>P1-6: SLA 扫描与处理接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
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
    @PostMapping("/sla/scan")
    @PrePermission(PermissionCodes.WORKFLOW_SLA_CONFIG)
    public Result<Integer> slaScan() {
        int processed = slaService.scanAndProcess();
        return Result.ok(processed);
    }

    /**
     * P1-6: 手动触发单条任务的 SLA 处理
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @PostMapping("/sla/process/{taskId}")
    @PrePermission(PermissionCodes.WORKFLOW_SLA_CONFIG)
    public Result<Boolean> slaProcess(@PathVariable @Min(1) Long taskId) {
        FlowRunTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return Result.failed(BizErrorCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        boolean ok = slaService.processOverdue(task);
        return Result.ok(ok);
    }
}
