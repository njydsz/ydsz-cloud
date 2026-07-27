package com.njydsz.cronjob.web.controller.dag;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstanceDO;
import com.njydsz.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * DAG 工作流实例 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 实例的查询、暂停/恢复/取消、上下文管理等 HTTP 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流实例")
@RestController
@RequestMapping("/cronjob/dag/instance")
@RequiredArgsConstructor
public class JobDagInstanceController {

    /** DAG 实例服务 */
    private final JobDagInstanceService jobDagInstanceService;

    /**
     * 查询 DAG 实例详情。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，包含 DAG 实例信息
     */
    @Operation(summary = "查询 DAG 实例详情")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}")
    public BaseResponse<JobDagInstanceDO> getInstanceById(@PathVariable String instanceId) {
        return BaseResponse.success(jobDagInstanceService.getInstanceById(instanceId));
    }

    /**
     * 查询指定 DAG 的实例列表。
     *
     * @param dagId DAG ID
     * @param limit 最多返回条数（默认 20）
     * @return 统一响应结果，包含 DAG 实例列表
     */
    @Operation(summary = "查询 DAG 的实例列表")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/dag/{dagId}")
    public BaseResponse<List<JobDagInstanceDO>> listByDagId(@PathVariable String dagId,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return BaseResponse.success(jobDagInstanceService.listByDagId(dagId, limit));
    }

    /**
     * 按状态查询 DAG 实例。
     *
     * @param status 实例状态（RUNNING/PAUSED/SUCCESS/FAILED/CANCELLED）
     * @return 统一响应结果，包含 DAG 实例列表
     */
    @Operation(summary = "按状态查询 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/status/{status}")
    public BaseResponse<List<JobDagInstanceDO>> listByStatus(@PathVariable String status) {
        return BaseResponse.success(jobDagInstanceService.listByStatus(status));
    }

    /**
     * 查询 DAG 实例的节点列表。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，包含节点实例列表
     */
    @Operation(summary = "查询 DAG 实例的节点列表")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}/nodes")
    public BaseResponse<List<JobDagNodeInstanceDO>> listNodes(@PathVariable String instanceId) {
        return BaseResponse.success(jobDagInstanceService.listNodes(instanceId));
    }

    /**
     * 获取 DAG 实例可视化数据。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，包含可视化数据（节点状态/边/时间线）
     */
    @Operation(summary = "获取 DAG 实例可视化数据（P4-1）")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{instanceId}/visualization")
    public BaseResponse<DagInstanceVisualizationVO> getVisualization(@PathVariable String instanceId) {
        return BaseResponse.success(jobDagInstanceService.getVisualization(instanceId));
    }

    /**
     * 暂停 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:pauseInstance:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.jobdaginstance.pauseInstance", threshold = 50)
    @PutMapping("/{instanceId}/pause")
    public BaseResponse<Void> pauseInstance(@PathVariable String instanceId) {
        jobDagInstanceService.pauseInstance(instanceId);
        return BaseResponse.success();
    }

    /**
     * 恢复 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:resumeInstance:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.jobdaginstance.resumeInstance", threshold = 50)
    @PutMapping("/{instanceId}/resume")
    public BaseResponse<Void> resumeInstance(@PathVariable String instanceId) {
        jobDagInstanceService.resumeInstance(instanceId);
        return BaseResponse.success();
    }

    /**
     * 取消 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "取消 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:cancelInstance:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.jobdaginstance.cancelInstance", threshold = 50)
    @PutMapping("/{instanceId}/cancel")
    public BaseResponse<Void> cancelInstance(@PathVariable String instanceId) {
        jobDagInstanceService.cancelInstance(instanceId);
        return BaseResponse.success();
    }

    /**
     * 更新 DAG 实例上下文（用于节点间参数传递）。
     *
     * @param instanceId DAG 实例 ID
     * @param contextJson 上下文 JSON 字符串
     * @return 统一响应结果
     */
    @Operation(summary = "更新 DAG 实例上下文")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagInstanceController:updateContext:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.jobdaginstance.updateContext", threshold = 50)
    @PutMapping("/{instanceId}/context")
    public BaseResponse<Void> updateContext(@PathVariable String instanceId, @RequestBody String contextJson) {
        jobDagInstanceService.updateContext(instanceId, contextJson);
        return BaseResponse.success();
    }
}
