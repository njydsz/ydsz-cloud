package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.dto.JobRelationSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import com.njydsz.pmis.cronjob.service.JobRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务依赖关系 Controller（P4 DAG 工作流）。
 *
 * <p>提供任务依赖关系的增删查 API，支持构建 DAG 工作流。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "任务依赖关系")
@RestController
@RequestMapping("/cronjob/relation")
@RequiredArgsConstructor
public class JobRelationController {

    private final JobRelationService jobRelationService;

    @Operation(summary = "添加任务依赖关系")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "添加任务依赖", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-relation:add-relation", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> addRelation(@Valid @RequestBody JobRelationSaveDTO dto) {
        return Result.ok(jobRelationService.addRelation(
                dto.getParentJobId(), dto.getChildJobId(), dto.getFailStrategy()));
    }

    @Operation(summary = "删除任务依赖关系")
    @PrePermission(PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "删除任务依赖", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-relation:remove-relation", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{relationId}")
    public Result<Void> removeRelation(@PathVariable String relationId) {
        jobRelationService.removeRelation(relationId);
        return Result.ok();
    }

    @Operation(summary = "查询任务后继依赖")
    @GetMapping("/children/{parentJobId}")
    public Result<List<JobRelationDO>> getChildren(@PathVariable String parentJobId) {
        return Result.ok(jobRelationService.getChildren(parentJobId));
    }

    @Operation(summary = "查询任务前置依赖")
    @GetMapping("/parents/{childJobId}")
    public Result<List<JobRelationDO>> getParents(@PathVariable String childJobId) {
        return Result.ok(jobRelationService.getParents(childJobId));
    }

    @Operation(summary = "查询全部依赖关系（DAG 全图）")
    @GetMapping("/all")
    public Result<List<JobRelationDO>> getAllRelations() {
        return Result.ok(jobRelationService.getAllRelations());
    }
}
