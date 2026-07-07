package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.dto.JobDagTriggerDTO;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.service.JobDagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DAG 工作流定义 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 定义的增删改查、启用/禁用、手动触发等 HTTP 接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流定义")
@RestController
@RequestMapping("/cronjob/dag")
@RequiredArgsConstructor
public class JobDagController {

    private final JobDagService jobDagService;

    @Operation(summary = "创建 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_CREATE)
    @OperationLog(module = "任务调度", action = "创建DAG", bizType = "CRONJOB_DAG")
    @PostMapping("/")
    public Result<String> createDag(@Valid @RequestBody JobDagSaveDTO dto) {
        return Result.ok(jobDagService.createDag(dto));
    }

    @Operation(summary = "更新 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @PutMapping("/{dagId}")
    public Result<Void> updateDag(@PathVariable String dagId, @Valid @RequestBody JobDagSaveDTO dto) {
        jobDagService.updateDag(dagId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_DELETE)
    @OperationLog(module = "任务调度", action = "删除DAG", bizType = "CRONJOB_DAG")
    @DeleteMapping("/{dagId}")
    public Result<Void> deleteDag(@PathVariable String dagId) {
        jobDagService.deleteDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "启用 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "启用DAG", bizType = "CRONJOB_DAG")
    @PutMapping("/{dagId}/enable")
    public Result<Void> enableDag(@PathVariable String dagId) {
        jobDagService.enableDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "禁用 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "禁用DAG", bizType = "CRONJOB_DAG")
    @PutMapping("/{dagId}/disable")
    public Result<Void> disableDag(@PathVariable String dagId) {
        jobDagService.disableDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "查询 DAG 工作流详情")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{dagId}")
    public Result<JobDagDO> getDagById(@PathVariable String dagId) {
        return Result.ok(jobDagService.getDagById(dagId));
    }

    @Operation(summary = "根据 KEY 查询 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/key/{dagKey}")
    public Result<JobDagDO> getDagByKey(@PathVariable String dagKey) {
        return Result.ok(jobDagService.getDagByKey(dagKey));
    }

    @Operation(summary = "查询所有启用的 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/enabled")
    public Result<List<JobDagDO>> listEnabledDags() {
        return Result.ok(jobDagService.listEnabledDags());
    }

    @Operation(summary = "手动触发 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_TRIGGER)
    @OperationLog(module = "任务调度", action = "触发DAG", bizType = "CRONJOB_DAG")
    @PostMapping("/trigger")
    public Result<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
        return Result.ok(jobDagService.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
    }
}
