package com.njydsz.pmis.cronjob.web.controller.dag;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.cronjob.server.core.dag.DagInstanceControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DAG 工作流控制接口（P1-4 暂停/恢复/手动重试）。
 *
 * <p>提供对运行中 DAG 实例的控制操作：
 * <ul>
 *   <li>POST /cronjob/dag/instance/{instanceId}/pause - 暂停 DAG 实例</li>
 *   <li>POST /cronjob/dag/instance/{instanceId}/resume - 恢复 DAG 实例</li>
 *   <li>POST /cronjob/dag/instance/{instanceId}/cancel - 取消 DAG 实例</li>
 *   <li>POST /cronjob/dag/instance/{instanceId}/retry-node - 手动重试指定节点</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Tag(name = "DAG 工作流控制")
@RestController
@RequestMapping("/cronjob/dag/instance")
@RequiredArgsConstructor
public class DagInstanceControlController {

    /** DAG 实例控制服务（暂停/恢复/取消/重试） */
    private final DagInstanceControlService dagInstanceControlService;

    /**
     * 暂停 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，true 表示暂停成功
     */
    @Operation(summary = "暂停 DAG 实例")
    @Idempotent(key = "dagInstanceControl:pause", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{instanceId}/pause")
    public Result<Boolean> pause(@PathVariable String instanceId) {
        boolean success = dagInstanceControlService.pause(instanceId);
        return success ? Result.ok(true) : Result.fail("暂停失败：实例不存在或非 RUNNING 状态");
    }

    /**
     * 恢复 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，true 表示恢复成功
     */
    @Operation(summary = "恢复 DAG 实例")
    @Idempotent(key = "dagInstanceControl:resume", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{instanceId}/resume")
    public Result<Boolean> resume(@PathVariable String instanceId) {
        boolean success = dagInstanceControlService.resume(instanceId);
        return success ? Result.ok(true) : Result.fail("恢复失败：实例不存在或非 PAUSED 状态");
    }

    /**
     * 取消 DAG 实例。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，true 表示取消成功
     */
    @Operation(summary = "取消 DAG 实例")
    @Idempotent(key = "dagInstanceControl:cancel", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{instanceId}/cancel")
    public Result<Boolean> cancel(@PathVariable String instanceId) {
        boolean success = dagInstanceControlService.cancel(instanceId);
        return success ? Result.ok(true) : Result.fail("取消失败：实例不存在或已终态");
    }

    /**
     * 手动重试指定失败节点。
     *
     * @param instanceId DAG 实例 ID
     * @param jobKey     任务 KEY（节点标识）
     * @return 统一响应结果，true 表示重试成功
     */
    @Operation(summary = "手动重试指定失败节点")
    @Idempotent(key = "dagInstanceControl:retryNode", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{instanceId}/retryNode")
    public Result<Boolean> retryNode(@PathVariable String instanceId,
                                      @RequestParam String jobKey) {
        boolean success = dagInstanceControlService.retryNode(instanceId, jobKey);
        return success ? Result.ok(true) : Result.fail("重试失败：节点不存在或非 FAILED 状态");
    }
}
