package com.remisoft.cronjob.web.controller.job;

import org.springframework.context.ApplicationContext;
import com.remisoft.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.cronjob.domain.job.MapContext;
import com.remisoft.cronjob.domain.job.MapProcessor;
import com.remisoft.cronjob.domain.job.ProcessResult;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.lock.annotation.IdempotentExempt;
import com.remisoft.common.util.id.TracerUtils;
import com.remisoft.cronjob.server.core.dispatch.RemoteSubTaskRequest;
import com.remisoft.cronjob.server.core.dispatch.RemoteTaskRequest;
import com.remisoft.cronjob.server.core.dispatch.TaskDispatcher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.remisoft.common.lock.annotation.Idempotent;
import com.remisoft.common.audit.annotation.Audit;
import com.remisoft.common.audit.enums.AuditAction;
import com.remisoft.common.audit.enums.AuditType;
import com.remisoft.common.core.code.BaseResultCode;

/**
 * 内部任务执行接口 Controller（P1-4 远程派发接收端）。
 *
 * <p>集群模式下，每个 cronjob 节点都暴露此接口，接收 Leader 节点通过 HTTP 派发的远程分片任务。
 * Leader 通过 {@code RemoteTaskClient} 发送 HTTP POST 到
 * {@code http://{host}:{port}/api/v1/cronjob/internal/execute}，
 * 本 Controller 接收后调用 {@link TaskDispatcher#executeLocally} 在本地执行。
 *
 * <p>同时支持 MapReduce 子任务的远程派发（{@link #executeSubTask}）：Leader 将大数据量任务
 * 拆分为子任务后分发到各 Worker 节点并行执行，子任务执行结果返回 Leader 汇总。
 *
 * <h3>安全考虑</h3>
 * <ul>
 *   <li>仅限内网调用，生产环境应通过网络策略（K8s NetworkPolicy / SecurityGroup）限制访问来源</li>
 *   <li>不走 {@code @AuthApiPermission} 权限校验，因为是节点间内部通信</li>
 *   <li>请求体由 Leader 节点构造，信任内网来源</li>
 *   <li>对每个请求恢复 traceId 到 MDC，保证全链路追踪</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>参数校验失败：返回 400 + code!=0</li>
 *   <li>锁被持有：返回 200 + code=0 + data=null（正常跳过，不是错误）</li>
 *   <li>执行异常：返回 200 + code=0 + data=null（执行器已记录 FAILED 日志）</li>
 * </ul>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   Leader 节点（quartz 调度器）
 *     → RemoteTaskClient（HTTP 派发）
 *       → remi-cronjob-web.InternalJobController（本 Controller）
 *         → TaskDispatcher.executeLocally
 *           → JobProcessor（GLUE / Bean / Shell 等执行器）
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "内部任务执行（远程派发接收端）", description = "集群节点间任务派发的 HTTP 接收端，接收 Leader 节点分片")
@RestController
@RequestMapping("/api/v1/cronjob/internal")
@RequiredArgsConstructor
public class InternalJobController {

    /** 任务派发器（封装本地执行逻辑） */
    private final TaskDispatcher taskDispatcher;
    /** P0-1: Spring 应用上下文（用于获取 MapProcessor Bean） */
    private final ApplicationContext applicationContext;

    /**
     * 接收远程派发请求并在本地执行任务。
     *
     * <p>处理流程：
     * <ol>
     *   <li>参数校验：job 和 jobKey 必填</li>
     *   <li>从请求中恢复 traceId 到 MDC（保证全链路追踪）</li>
     *   <li>调用 {@link TaskDispatcher#executeLocally} 在本地执行任务</li>
     *   <li>finally 中清理 traceId，避免线程复用导致 MDC 污染</li>
     * </ol>
     *
     * <p>注意：执行异常时不返回 error，而是返回 success(null)。原因是执行器端已记录 FAILED 日志，
     * 调用方通过日志 ID 即可查询失败原因；返回 error 会导致 Leader 误判为派发失败触发重试。
     *
     * @param request 远程派发请求（job + triggerType + shardIndex + shardTotal + traceId）
     * @return 统一响应结果，data 为执行日志 ID（锁被持有或执行失败时为 null）
     */
    @Operation(summary = "接收远程派发请求并本地执行")
    @IdempotentExempt("定时触发接口，无需幂等")
    @RateLimit(resource = "cronjob.internaljob.execute", threshold = 50)
    @Idempotent(key = "remi:cronjob:InternalJobController:execute:lock", ttlSeconds = 5)
    @PostMapping("/execute")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.OTHER, content = "'execute'")
    public BaseResponse<String> execute(@RequestBody RemoteTaskRequest request) {
        if (request == null || request.getJob() == null) {
            log.warn("[InternalJob] 远程派发请求参数为空");
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "请求参数为空");
        }
        if (request.getJob().getJobKey() == null) {
            log.warn("[InternalJob] 远程派发请求 jobKey 为空");
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "jobKey 不能为空");
        }
        // P1-4: 从请求中恢复 traceId 到 MDC，保证全链路追踪
        String traceId = request.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            TracerUtils.setTraceId(traceId);
        } else {
            TracerUtils.getOrCreateTraceId();
        }
        try {
            log.info("[InternalJob] 接收远程派发: key={} triggerType={} shard={}/{} traceId={}",
                    request.getJob().getJobKey(), request.getTriggerType(),
                    request.getShardIndex(), request.getShardTotal(), TracerUtils.getTraceId());
            String logId = taskDispatcher.executeLocally(
                    request.getJob(), request.getTriggerType(),
                    request.getShardIndex(), request.getShardTotal());
            return BaseResponse.success(logId);
        } catch (Exception e) {
            log.error("[InternalJob] 远程派发执行异常: key={} reason={}",
                    request.getJob().getJobKey(), e.getMessage(), e);
            // 执行异常时返回 null（执行器端已记录 FAILED 日志，或锁被持有）
            return BaseResponse.success(null);
        } finally {
            TracerUtils.clear();
        }
    }

    /**
     * P0-1: 接收 MapReduce 子任务远程派发请求并在本地执行。
     *
     * <p>处理流程：
     * <ol>
     *   <li>参数校验：jobKey / handler 必填</li>
     *   <li>从请求中恢复 traceId 到 MDC</li>
     *   <li>从 ApplicationContext 通过 handler Bean 名称获取 MapProcessor</li>
     *   <li>构造子任务 MapContext，调用 processor.process()</li>
     *   <li>返回执行结果（含 success/result/errorMessage）</li>
     * </ol>
     *
     * <p>注意：与 {@link #execute} 不同，本接口始终返回 success（包含执行结果对象），
     * 因为子任务执行结果是 Leader 端做汇总归并的依据，需要传回。
     *
     * @param request 子任务派发请求（jobId/logId/jobKey/taskName/handler/taskParams/traceId）
     * @return 统一响应结果，data 为子任务执行结果对象（{@link ProcessResultVO}）
     */
    @Operation(summary = "接收 MapReduce 子任务远程派发并本地执行")
    @IdempotentExempt("定时触发接口，无需幂等")
    @RateLimit(resource = "cronjob.internaljob.executeSubTask", threshold = 50)
    @Idempotent(key = "remi:cronjob:InternalJobController:executeSubTask:lock", ttlSeconds = 5)
    @PostMapping("/executeSubTask")
    @Audit(module = "任务管理", type = AuditType.OPERATION, action = AuditAction.OTHER, content = "'executeSubTask'")
    public BaseResponse<ProcessResult> executeSubTask(@RequestBody RemoteSubTaskRequest request) {
        if (request == null || request.getJobKey() == null || request.getHandler() == null) {
            log.warn("[InternalJob] 子任务请求参数为空");
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "请求参数为空");
        }
        String traceId = request.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            TracerUtils.setTraceId(traceId);
        } else {
            TracerUtils.getOrCreateTraceId();
        }
        try {
            log.info("[InternalJob] 接收子任务派发: key={} taskName={} handler={} traceId={}",
                    request.getJobKey(), request.getTaskName(), request.getHandler(), TracerUtils.getTraceId());
            // 获取 MapProcessor Bean
            MapProcessor processor;
            try {
                processor = applicationContext.getBean(request.getHandler(), MapProcessor.class);
            } catch (Exception e) {
                log.error("[InternalJob] 获取 MapProcessor Bean 失败: handler={} reason={}",
                        request.getHandler(), e.getMessage());
                return BaseResponse.success(CronjobConverter.INSTANT.toProcessResultVO(
                        ProcessResult.failed("获取 MapProcessor Bean 失败: " + e.getMessage())));
            }
            // 构造子任务上下文并执行
            MapContext context = new MapContext(
                    request.getJobId(), request.getLogId(), request.getJobKey(),
                    request.getTaskName(), request.getTaskParams(), false);
            ProcessResult result;
            try {
                result = processor.process(context);
                if (result == null) {
                    result = ProcessResult.success();
                }
            } catch (Exception e) {
                log.error("[InternalJob] 子任务执行异常: key={} taskName={} reason={}",
                        request.getJobKey(), request.getTaskName(), e.getMessage(), e);
                result = ProcessResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return BaseResponse.success(CronjobConverter.INSTANT.toProcessResultVO(result));
        } finally {
            TracerUtils.clear();
        }
    }
}
