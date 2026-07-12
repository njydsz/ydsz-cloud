package com.njydsz.pmis.cronjob.web.controller.dag;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.pmis.cronjob.server.vo.DagInstanceVisualizationVO;
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
        return BaseResponse.ok(jobDagInstanceService.getInstanceById(instanceId));
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
        return BaseResponse.ok(jobDagInstanceService.listByDagId(dagId, limit));
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
        return BaseResponse.ok(jobDagInstanceService.listByStatus(status));
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
        return BaseResponse.ok(jobDagInstanceService.listNodes(instanceId));
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
        return BaseResponse.ok(jobDagInstanceService.getVisualization(instanceId));
    }

    /**
     * 暂停 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "暂停 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "暂停DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDagInstance:pauseInstance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/pause")
    public BaseResponse<Void> pauseInstance(@PathVariable String instanceId) {
        jobDagInstanceService.pauseInstance(instanceId);
        return BaseResponse.ok();
    }

    /**
     * 恢复 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "恢复 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "恢复DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDagInstance:resumeInstance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/resume")
    public BaseResponse<Void> resumeInstance(@PathVariable String instanceId) {
        jobDagInstanceService.resumeInstance(instanceId);
        return BaseResponse.ok();
    }

    /**
     * 取消 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果
     */
    @Operation(summary = "取消 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "取消DAG实例", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDagInstance:cancelInstance", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/cancel")
    public BaseResponse<Void> cancelInstance(@PathVariable String instanceId) {
        jobDagInstanceService.cancelInstance(instanceId);
        return BaseResponse.ok();
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
    @OperationLog(module = "任务调度", action = "更新DAG实例上下文", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDagInstance:updateContext", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{instanceId}/context")
    public BaseResponse<Void> updateContext(@PathVariable String instanceId, @RequestBody String contextJson) {
        jobDagInstanceService.updateContext(instanceId, contextJson);
        return BaseResponse.ok();
    }
}
