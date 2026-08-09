package com.njydsz.cronjob.web.controller.dag;

import org.springframework.web.bind.annotation.PathVariable;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.server.core.dag.DagInstanceControlService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.cronjob.domain.enums.CronjobResultCode;

/**
 * DAG 工作流实例运行时控制 Controller（P1-4）。
 *
 * <p>对运行中的 DAG 实例提供生命周期控制能力，覆盖以下操作：
 * <ul>
 *   <li>暂停：将 RUNNING 转为 PAUSED，调度器停止派发后续节点，正在执行的节点继续完成</li>
 *   <li>恢复：将 PAUSED 转回 RUNNING，调度器继续派发后续节点</li>
 *   <li>取消：终态化实例，停止派发并标记 CANCELLED</li>
 *   <li>重试节点：对 FAILED 状态节点重新触发执行</li>
 * </ul>
 *
 * <h3>状态机</h3>
 * <pre>
 *   PENDING → RUNNING ⇄ PAUSED
 *                ↓
 *           SUCCESS / FAILED / CANCELLED
 * </pre>
 * 所有状态转换由 {@link DagInstanceControlService} 内的 {@code canTransitTo} 校验合法性。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #pause} - 暂停 DAG 实例</li>
 *   <li>{@link #resume} - 恢复 DAG 实例</li>
 *   <li>{@link #cancel} - 取消 DAG 实例</li>
 *   <li>{@link #retryNode} - 重试失败节点</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS / IP）</li>
 *   <li>细粒度权限控制（{@link PermissionCodes#CRONJOB_DAG_UPDATE}）</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 DAG 详情页（暂停/恢复/取消/重试按钮）
 *     → ydsz-gateway
 *       → ydsz-cronjob-web（本 Controller）
 *         → ydsz-cronjob-server.DagInstanceControlService
 *           → ydsz-cronjob-infra.JobDagInstanceMapper / JobDagNodeInstanceMapper
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "DAG 工作流控制", description = "DAG 实例运行时控制：暂停/恢复/取消/重试节点")
@RestController
@RequestMapping("/api/v1/cronjob/dag/instance")
@RequiredArgsConstructor
public class DagInstanceControlController {

    /** DAG 实例控制服务（暂停/恢复/取消/重试） */
    private final DagInstanceControlService dagInstanceControlService;

    /**
     * 暂停 DAG 实例。
     *
     * <p>将 RUNNING 转为 PAUSED：调度器停止派发后续节点，正在执行的节点继续完成。
     * 状态转换校验由 {@code DagInstanceControlService.pause} 内部完成（仅 RUNNING 可被暂停）。
     * 可通过 {@link #resume} 恢复；已 SUCCESS/FAILED/CANCELLED 的实例禁止暂停。
     *
     * @param instanceId DAG 实例 ID（雪花算法）
     * @return 统一响应结果，true 表示暂停成功；false 表示实例不存在或非 RUNNING 状态
     */
    @Operation(summary = "暂停 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:DagInstanceControlController:pause:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.daginstancecontrol.pause", threshold = 50)
    @PostMapping("/{instanceId}/pause")
    @Audit(module = "DAG实例控制", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'pause'")
    public BaseResponse<Boolean> pause(@PathVariable String instanceId) {
        log.info("[DagInstanceControl] 暂停 DAG 实例: instanceId={}", instanceId);
        boolean success = dagInstanceControlService.pause(instanceId);
        return success ? BaseResponse.success(true) : BaseResponse.error(CronjobResultCode.DAG_INSTANCE_NOT_FOUND, "暂停失败：实例不存在或非 RUNNING 状态");
    }

    /**
     * 恢复 DAG 实例。
     *
     * <p>将 PAUSED 转回 RUNNING：调度器重新派发后续未执行节点。
     * 仅 PAUSED 状态可恢复；其他状态返回 false。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，true 表示恢复成功；false 表示实例不存在或非 PAUSED 状态
     */
    @Operation(summary = "恢复 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:DagInstanceControlController:resume:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.daginstancecontrol.resume", threshold = 50)
    @PostMapping("/{instanceId}/resume")
    @Audit(module = "DAG实例控制", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'resume'")
    public BaseResponse<Boolean> resume(@PathVariable String instanceId) {
        log.info("[DagInstanceControl] 恢复 DAG 实例: instanceId={}", instanceId);
        boolean success = dagInstanceControlService.resume(instanceId);
        return success ? BaseResponse.success(true) : BaseResponse.error(CronjobResultCode.DAG_INSTANCE_NOT_FOUND, "恢复失败：实例不存在或非 PAUSED 状态");
    }

    /**
     * 取消 DAG 实例。
     *
     * <p>终态化实例，停止派发所有未执行节点并将状态标记为 CANCELLED。
     * 正在执行的节点不会被强制中断（设计选择：避免脏数据/事务中间态）。
     * 仅 RUNNING/PAUSED 状态可取消；终态实例返回 false。
     *
     * @param instanceId DAG 实例 ID
     * @return 统一响应结果，true 表示取消成功；false 表示实例不存在或已处于终态
     */
    @Operation(summary = "取消 DAG 实例")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:DagInstanceControlController:cancel:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.daginstancecontrol.cancel", threshold = 50)
    @PostMapping("/{instanceId}/cancel")
    @Audit(module = "DAG实例控制", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'cancel'")
    public BaseResponse<Boolean> cancel(@PathVariable String instanceId) {
        log.info("[DagInstanceControl] 取消 DAG 实例: instanceId={}", instanceId);
        boolean success = dagInstanceControlService.cancel(instanceId);
        return success ? BaseResponse.success(true) : BaseResponse.error(CronjobResultCode.DAG_INSTANCE_NOT_FOUND, "取消失败：实例不存在或已终态");
    }

    /**
     * 手动重试指定失败节点。
     *
     * <p>仅对状态为 FAILED 的节点有效：将该节点状态重置为 PENDING，由调度器重新派发执行。
     * 节点重试不影响其他已完成节点的状态；重试成功时 DAG 实例可能从 FAILED 状态转为 RUNNING。
     *
     * @param instanceId DAG 实例 ID
     * @param jobKey     任务 KEY（DAG 节点标识，{@code JobDagNodeInstance.jobKey}）
     * @return 统一响应结果，true 表示重试成功；false 表示节点不存在或非 FAILED 状态
     */
    @Operation(summary = "手动重试指定失败节点")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:DagInstanceControlController:retryNode:lock", ttlSeconds = 5)
    @RateLimit(resource = "cronjob.daginstancecontrol.retryNode", threshold = 50)
    @PostMapping("/{instanceId}/retryNode")
    @Audit(module = "DAG实例控制", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'retryNode'")
    public BaseResponse<Boolean> retryNode(@PathVariable String instanceId,
                                      @RequestParam String jobKey) {
        log.info("[DagInstanceControl] 重试节点: instanceId={} jobKey={}", instanceId, jobKey);
        boolean success = dagInstanceControlService.retryNode(instanceId, jobKey);
        return success ? BaseResponse.success(true) : BaseResponse.error(CronjobResultCode.DAG_NODE_NOT_FOUND, "重试失败：节点不存在或非 FAILED 状态");
    }
}
