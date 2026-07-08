package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.job.MapContext;
import com.njydsz.pmis.common.job.MapProcessor;
import com.njydsz.pmis.common.job.ProcessResult;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.core.dispatch.RemoteSubTaskRequest;
import com.njydsz.pmis.cronjob.core.dispatch.RemoteTaskRequest;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import org.springframework.context.ApplicationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部任务执行接口（P1-4 远程派发接收端）。
 *
 * <p>每个 cronjob 实例都暴露此接口，接收 Leader 节点的远程分片派发请求。
 * Leader 通过 {@code RemoteTaskClient} 发送 HTTP POST 到
 * {@code http://{host}:{port}/cronjob/internal/execute}，
 * 本 Controller 接收后调用 {@link TaskDispatcher#executeLocally} 在本地执行。
 *
 * <h3>安全考虑</h3>
 * <ul>
 *   <li>仅限内网调用，生产环境应通过网络策略限制访问来源</li>
 *   <li>不走权限校验（@PrePermission），因为是节点间内部通信</li>
 *   <li>请求体由 Leader 构造，信任内网来源</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * <ul>
 *   <li>参数校验失败：返回 400 + code!=0</li>
 *   <li>锁被持有：返回 200 + code=0 + data=null（正常跳过，不是错误）</li>
 *   <li>执行异常：返回 200 + code=0 + data=null（执行器已记录 FAILED 日志）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "内部任务执行（远程派发接收端）")
@RestController
@RequestMapping("/cronjob/internal")
@RequiredArgsConstructor
public class InternalJobController {

    /** 任务派发器 */
    private final TaskDispatcher taskDispatcher;
    /** P0-1: Spring 应用上下文（用于获取 MapProcessor Bean） */
    private final ApplicationContext applicationContext;

    /**
     * 接收远程派发请求并在本地执行。
     *
     * <p>Leader 节点将分片任务通过 HTTP 派发到本节点，本方法接收后：
     * <ol>
     *   <li>从请求中恢复 traceId 到 MDC（保证全链路追踪）</li>
     *   <li>调用 {@link TaskDispatcher#executeLocally} 在本地执行</li>
     *   <li>返回执行日志 ID（data 字段）</li>
     * </ol>
     *
     * @param request 远程派发请求（job + triggerType + shardIndex + shardTotal + traceId）
     * @return 统一响应结果，data 为执行日志 ID（锁被持有或执行失败时为 null）
     */
    @Operation(summary = "接收远程派发请求并本地执行")
    @PostMapping("/execute")
    public Result<String> execute(@RequestBody RemoteTaskRequest request) {
        if (request == null || request.getJob() == null) {
            log.warn("[InternalJob] 远程派发请求参数为空");
            return Result.failed(400, "请求参数为空");
        }
        if (request.getJob().getJobKey() == null) {
            log.warn("[InternalJob] 远程派发请求 jobKey 为空");
            return Result.failed(400, "jobKey 不能为空");
        }
        // P1-4: 从请求中恢复 traceId 到 MDC，保证全链路追踪
        String traceId = request.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            TraceIdUtil.set(traceId);
        } else {
            TraceIdUtil.getOrCreate();
        }
        try {
            log.info("[InternalJob] 接收远程派发: key={} triggerType={} shard={}/{} traceId={}",
                    request.getJob().getJobKey(), request.getTriggerType(),
                    request.getShardIndex(), request.getShardTotal(), TraceIdUtil.get());
            String logId = taskDispatcher.executeLocally(
                    request.getJob(), request.getTriggerType(),
                    request.getShardIndex(), request.getShardTotal());
            return Result.ok(logId);
        } catch (Exception e) {
            log.error("[InternalJob] 远程派发执行异常: key={} reason={}",
                    request.getJob().getJobKey(), e.getMessage(), e);
            // 执行异常时返回 null（执行器端已记录 FAILED 日志，或锁被持有）
            return Result.ok(null);
        } finally {
            TraceIdUtil.clear();
        }
    }

    /**
     * P0-1: 接收 MapReduce 子任务远程派发请求并在本地执行。
     *
     * <p>Leader 节点将 MapReduce 子任务通过 HTTP 派发到本节点，本方法接收后：
     * <ol>
     *   <li>从请求中恢复 traceId 到 MDC</li>
     *   <li>从 ApplicationContext 获取 MapProcessor Bean</li>
     *   <li>构造子任务 MapContext，调用 processor.process()</li>
     *   <li>返回执行结果（含 success/result/errorMessage）</li>
     * </ol>
     *
     * @param request 子任务派发请求
     * @return 统一响应结果，data 为子任务执行结果对象
     */
    @Operation(summary = "接收 MapReduce 子任务远程派发并本地执行")
    @PostMapping("/execute-sub-task")
    public Result<ProcessResult> executeSubTask(@RequestBody RemoteSubTaskRequest request) {
        if (request == null || request.getJobKey() == null || request.getHandler() == null) {
            log.warn("[InternalJob] 子任务请求参数为空");
            return Result.failed(400, "请求参数为空");
        }
        String traceId = request.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            TraceIdUtil.set(traceId);
        } else {
            TraceIdUtil.getOrCreate();
        }
        try {
            log.info("[InternalJob] 接收子任务派发: key={} taskName={} handler={} traceId={}",
                    request.getJobKey(), request.getTaskName(), request.getHandler(), TraceIdUtil.get());
            // 获取 MapProcessor Bean
            MapProcessor processor;
            try {
                processor = applicationContext.getBean(request.getHandler(), MapProcessor.class);
            } catch (Exception e) {
                log.error("[InternalJob] 获取 MapProcessor Bean 失败: handler={} reason={}",
                        request.getHandler(), e.getMessage());
                return Result.ok(ProcessResult.failed("获取 MapProcessor Bean 失败: " + e.getMessage()));
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
            return Result.ok(result);
        } finally {
            TraceIdUtil.clear();
        }
    }
}
