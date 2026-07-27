package com.njydsz.cronjob.web.controller.job;

import org.springframework.context.ApplicationContext;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.job.MapContext;
import com.njydsz.common.core.job.MapProcessor;
import com.njydsz.common.core.job.ProcessResult;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.server.core.dispatch.RemoteSubTaskRequest;
import com.njydsz.cronjob.server.core.dispatch.RemoteTaskRequest;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.ProcessResultVO;

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
 *   <li>不走权限校验（@AuthApiPermission），因为是节点间内部通信</li>
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "内部任务执行（远程派发接收端）")
@RestController
@RequestMapping("/api/v1/cronjob/internal")
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
    @IdempotentExempt("定时触发接口，无需幂等")
    @RateLimit(resource = "cronjob.internaljob.execute", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:InternalJobController:execute:lock", ttlSeconds = 5)
    @PostMapping("/execute")
    public BaseResponse<String> execute(@RequestBody RemoteTaskRequest request) {
        if (request == null || request.getJob() == null) {
            log.warn("[InternalJob] 远程派发请求参数为空");
            return BaseResponse.error("400", "请求参数为空");
        }
        if (request.getJob().getJobKey() == null) {
            log.warn("[InternalJob] 远程派发请求 jobKey 为空");
            return BaseResponse.error("400", "jobKey 不能为空");
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
    @IdempotentExempt("定时触发接口，无需幂等")
    @RateLimit(resource = "cronjob.internaljob.executeSubTask", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:InternalJobController:executeSubTask:lock", ttlSeconds = 5)
    @PostMapping("/executeSubTask")
    public BaseResponse<ProcessResultVO> executeSubTask(@RequestBody RemoteSubTaskRequest request) {
        if (request == null || request.getJobKey() == null || request.getHandler() == null) {
            log.warn("[InternalJob] 子任务请求参数为空");
            return BaseResponse.error("400", "请求参数为空");
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
                return BaseResponse.success(ProcessResult.failed("获取 MapProcessor Bean 失败: " + e.getMessage()));
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
            return BaseResponse.success(result);
        } finally {
            TracerUtils.clear();
        }
    }
}
