package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.JobSlaSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;
import com.njydsz.pmis.cronjob.service.JobSlaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SLA 管理 Controller（P2-7 SLA 管理）。
 *
 * <p>提供 SLA 规则的 CRUD 接口与违约检查接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "任务 SLA 管理")
@RestController
@RequestMapping("/cronjob/sla")
@RequiredArgsConstructor
public class JobSlaController {

    private final JobSlaService jobSlaService;

    @Operation(summary = "创建 SLA 规则")
    @PrePermission(PermissionCodes.CRONJOB_SLA_CREATE)
    @OperationLog(module = "任务调度", action = "创建 SLA 规则", bizType = "CRONJOB_SLA")
    @Idempotent(key = "job-sla:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody JobSlaSaveDTO dto) {
        return Result.ok(jobSlaService.createSla(dto));
    }

    @Operation(summary = "更新 SLA 规则")
    @PrePermission(PermissionCodes.CRONJOB_SLA_UPDATE)
    @OperationLog(module = "任务调度", action = "更新 SLA 规则", bizType = "CRONJOB_SLA")
    @Idempotent(key = "job-sla:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody JobSlaSaveDTO dto) {
        jobSlaService.updateSla(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除 SLA 规则")
    @PrePermission(PermissionCodes.CRONJOB_SLA_DELETE)
    @OperationLog(module = "任务调度", action = "删除 SLA 规则", bizType = "CRONJOB_SLA")
    @Idempotent(key = "job-sla:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        jobSlaService.deleteSla(id);
        return Result.ok();
    }

    @Operation(summary = "查询 SLA 规则详情")
    @PrePermission(PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/{id}")
    public Result<JobSlaDO> getById(@PathVariable String id) {
        return Result.ok(jobSlaService.getSlaById(id));
    }

    @Operation(summary = "查询全部 SLA 规则")
    @PrePermission(PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/list")
    public Result<List<JobSlaDO>> list() {
        return Result.ok(jobSlaService.listSla());
    }

    @Operation(summary = "启用/禁用 SLA 规则")
    @PrePermission(PermissionCodes.CRONJOB_SLA_UPDATE)
    @OperationLog(module = "任务调度", action = "切换 SLA 启用状态", bizType = "CRONJOB_SLA")
    @Idempotent(key = "job-sla:toggle", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable String id, @RequestParam Integer enabled) {
        jobSlaService.toggleSla(id, enabled);
        return Result.ok();
    }

    @Operation(summary = "检查任务是否违反 SLA")
    @PrePermission(PermissionCodes.CRONJOB_SLA_VIEW)
    @GetMapping("/check")
    public Result<List<JobSlaService.SlaViolation>> checkViolation(@RequestParam String jobId) {
        return Result.ok(jobSlaService.checkViolation(jobId));
    }
}
