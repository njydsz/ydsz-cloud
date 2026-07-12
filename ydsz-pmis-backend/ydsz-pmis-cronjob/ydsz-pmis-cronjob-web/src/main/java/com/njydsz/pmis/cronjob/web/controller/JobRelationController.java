package com.njydsz.pmis.cronjob.web.controller.job;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.domain.dto.job.JobRelationSaveDTO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobRelationDO;
import com.njydsz.pmis.cronjob.server.service.job.JobRelationService;
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
 * @deprecated P3-2-merge: 推荐使用 DAG 管理 API ({@code /cronjob/dag}) 管理工作流。
 * 本 Controller 保留向后兼容，新功能应使用 DAG 体系。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Deprecated
@Tag(name = "任务依赖关系")
@RestController
@RequestMapping("/cronjob/relation")
@RequiredArgsConstructor
public class JobRelationController {

    /** 任务依赖关系服务 */
    private final JobRelationService jobRelationService;

    /**
     * 添加任务依赖关系。
     *
     * @param dto 依赖关系保存请求体
     * @return 统一响应结果，包含新增关系 ID
     */
    @Operation(summary = "添加任务依赖关系")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "添加任务依赖", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobRelation:addRelation", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> addRelation(@Valid @RequestBody JobRelationSaveDTO dto) {
        return BaseResponse.ok(jobRelationService.addRelation(
                dto.getParentJobId(), dto.getChildJobId(), dto.getFailStrategy()));
    }

    /**
     * 删除任务依赖关系。
     *
     * @param relationId 依赖关系 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除任务依赖关系")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_JOB_UPDATE)
    @OperationLog(module = "任务调度", action = "删除任务依赖", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobRelation:removeRelation", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{relationId}")
    public BaseResponse<Void> removeRelation(@PathVariable String relationId) {
        jobRelationService.removeRelation(relationId);
        return BaseResponse.ok();
    }

    /**
     * 查询任务的后继依赖。
     *
     * @param parentJobId 父任务 ID
     * @return 统一响应结果，包含后继依赖关系列表
     */
    @Operation(summary = "查询任务后继依赖")
    @GetMapping("/children/{parentJobId}")
    public BaseResponse<List<JobRelationDO>> getChildren(@PathVariable String parentJobId) {
        return BaseResponse.ok(jobRelationService.getChildren(parentJobId));
    }

    /**
     * 查询任务的前置依赖。
     *
     * @param childJobId 子任务 ID
     * @return 统一响应结果，包含前置依赖关系列表
     */
    @Operation(summary = "查询任务前置依赖")
    @GetMapping("/parents/{childJobId}")
    public BaseResponse<List<JobRelationDO>> getParents(@PathVariable String childJobId) {
        return BaseResponse.ok(jobRelationService.getParents(childJobId));
    }

    /**
     * 查询全部依赖关系（DAG 全图）。
     *
     * @return 统一响应结果，包含全部依赖关系列表
     */
    @Operation(summary = "查询全部依赖关系（DAG 全图）")
    @GetMapping("/all")
    public BaseResponse<List<JobRelationDO>> getAllRelations() {
        return BaseResponse.ok(jobRelationService.getAllRelations());
    }
}
