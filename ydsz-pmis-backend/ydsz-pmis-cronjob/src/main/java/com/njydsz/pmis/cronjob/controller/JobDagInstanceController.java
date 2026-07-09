package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.service.JobDagInstanceService;
import com.njydsz.pmis.cronjob.vo.DagInstanceVisualizationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DAG 工作流实例 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 实例的查询、暂停/恢复/取消、上下文管理等 HTTP 接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流实例")
@RestController
@RequestMapping("/cronjob/dag/instance")
@RequiredArgsConstructor
public class JobDagInstanceController {

    private final JobDagInstanceService jobDagInstanceService;

    @Operation(summary = "查询 DAG 实例详情")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}")
    public Result<JobDagInstanceDO> getInstanceById(@PathVariable String instanceId) {
        return Result.ok(jobDagInstanceService.getInstanceById(instanceId));
    }

    @Operation(summary = "查询 DAG 的实例列表")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/dag/{dagId}")
    public Result<List<JobDagInstanceDO>> listByDagId(@PathVariable String dagId,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(jobDagInstanceService.listByDagId(dagId, limit));
    }

    @Operation(summary = "按状态查询 DAG 实例")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/status/{status}")
    public Result<List<JobDagInstanceDO>> listByStatus(@PathVariable String status) {
        return Result.ok(jobDagInstanceService.listByStatus(status));
    }

    @Operation(summary = "查询 DAG 实例的节点列表")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}/nodes")
    public Result<List<JobDagNodeInstanceDO>> listNodes(@PathVariable String instanceId) {
        return Result.ok(jobDagInstanceService.listNodes(instanceId));
    }

    @Operation(summary = "获取 DAG 实例可视化数据（P4-1）")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}/visualization")
    public Result<DagInstanceVisualizationVO> getVisualization(@PathVariable String instanceId) {
        return Result.ok(jobDagInstanceService.getVisualization(instanceId));
    }

    @Operation(summary = "暂停 DAG 实例")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "暂停DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag-instance:pause-instance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/pause")
    public Result<Void> pauseInstance(@PathVariable String instanceId) {
        jobDagInstanceService.pauseInstance(instanceId);
        return Result.ok();
    }

    @Operation(summary = "恢复 DAG 实例")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "恢复DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag-instance:resume-instance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/resume")
    public Result<Void> resumeInstance(@PathVariable String instanceId) {
        jobDagInstanceService.resumeInstance(instanceId);
        return Result.ok();
    }

    @Operation(summary = "取消 DAG 实例")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "取消DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag-instance:cancel-instance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/cancel")
    public Result<Void> cancelInstance(@PathVariable String instanceId) {
        jobDagInstanceService.cancelInstance(instanceId);
        return Result.ok();
    }

    @Operation(summary = "更新 DAG 实例上下文")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "更新DAG实例上下文", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag-instance:update-context", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/context")
    public Result<Void> updateContext(@PathVariable String instanceId, @RequestBody String contextJson) {
        jobDagInstanceService.updateContext(instanceId, contextJson);
        return Result.ok();
    }
}
