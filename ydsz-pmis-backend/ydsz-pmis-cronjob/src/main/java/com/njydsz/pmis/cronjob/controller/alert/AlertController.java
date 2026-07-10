package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.alert.AlertRuleSaveDTO;
import com.njydsz.pmis.cronjob.entity.job.JobAlertLogDO;
import com.njydsz.pmis.cronjob.entity.job.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.service.alert.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则管理 Controller（P5 告警 + 监控）。
 *
 * <p>提供告警规则的增删改查 API 与告警日志查询 API。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "任务告警规则")
@RestController
@RequestMapping("/cronjob/alert")
@RequiredArgsConstructor
public class AlertController {

    /** 告警规则与日志服务 */
    private final AlertService alertService;

    /**
     * 创建告警规则。
     *
     * @param dto 告警规则保存请求体
     * @return 统一响应结果，包含新增规则 ID
     */
    @Operation(summary = "创建告警规则")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_CREATE)
    @OperationLog(module = "任务调度", action = "创建告警规则", bizType = "CRONJOB_ALERT")
    @Idempotent(key = "alert:create-rule", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rule")
    public Result<String> createRule(@Valid @RequestBody AlertRuleSaveDTO dto) {
        return Result.ok(alertService.createRule(dto));
    }

    /**
     * 更新告警规则。
     *
     * @param id  规则 ID
     * @param dto 告警规则保存请求体
     * @return 统一响应结果
     */
    @Operation(summary = "更新告警规则")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_UPDATE)
    @OperationLog(module = "任务调度", action = "更新告警规则", bizType = "CRONJOB_ALERT")
    @Idempotent(key = "alert:update-rule", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/rule/{id}")
    public Result<Void> updateRule(@PathVariable String id, @Valid @RequestBody AlertRuleSaveDTO dto) {
        alertService.updateRule(id, dto);
        return Result.ok();
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除告警规则")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_DELETE)
    @OperationLog(module = "任务调度", action = "删除告警规则", bizType = "CRONJOB_ALERT")
    @Idempotent(key = "alert:delete-rule", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable String id) {
        alertService.deleteRule(id);
        return Result.ok();
    }

    /**
     * 查询告警规则详情。
     *
     * @param id 规则 ID
     * @return 统一响应结果，包含告警规则详情
     */
    @Operation(summary = "查询告警规则详情")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/rule/{id}")
    public Result<JobAlertRuleDO> getRuleById(@PathVariable String id) {
        return Result.ok(alertService.getRuleById(id));
    }

    /**
     * 查询全部告警规则。
     *
     * @return 统一响应结果，包含告警规则列表
     */
    @Operation(summary = "查询全部告警规则")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/rules")
    public Result<List<JobAlertRuleDO>> listRules() {
        return Result.ok(alertService.listRules());
    }

    /**
     * 启用或禁用告警规则。
     *
     * @param id      规则 ID
     * @param enabled 启用状态（1=启用，0=禁用）
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用告警规则")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_UPDATE)
    @OperationLog(module = "任务调度", action = "切换告警规则启用状态", bizType = "CRONJOB_ALERT")
    @Idempotent(key = "alert:toggle-rule", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/rule/{id}/toggle")
    public Result<Void> toggleRule(@PathVariable String id, @RequestParam Integer enabled) {
        alertService.toggleRule(id, enabled);
        return Result.ok();
    }

    /**
     * 查询任务告警历史日志。
     *
     * @param jobId 任务 ID
     * @param since 起始时间（可选，ISO 8601 格式）
     * @return 统一响应结果，包含告警日志列表
     */
    @Operation(summary = "查询任务告警历史")
    @PrePermission(PermissionCodes.CRONJOB_ALERT_VIEW)
    @GetMapping("/logs/{jobId}")
    public Result<List<JobAlertLogDO>> queryAlertLogs(
            @PathVariable String jobId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return Result.ok(alertService.queryAlertLogs(jobId, since));
    }
}
