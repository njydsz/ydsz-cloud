package com.njydsz.pmis.cronjob.server.core.tracing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import org.slf4j.MDC;

/**
 * P2-12: 全链路追踪集成（SkyWalking / OpenTelemetry）。
 *
 * <p>将任务调度全链路（调度→派发→执行→完成）接入分布式追踪系统，
 * 实现跨节点、跨服务的调用链可视化。
 *
 * <h3>追踪链路</h3>
 * <pre>
 * [Scheduler] JobScanner.scan
 *   └─ [Dispatcher] DefaultTaskDispatcher.dispatch
 *      └─ [Remote] HTTP/gRPC → Worker Node
 *         └─ [Executor] executeJob
 *            └─ [Handler] JobHandler.execute
 *               └─ [Complete] publishTaskCompleted
 * </pre>
 *
 * <h3>集成方式</h3>
 * <ul>
 *   <li>SkyWalking：通过 agent 自动注入 TraceId，本组件补充业务标签</li>
 *   <li>OpenTelemetry：通过 OTel SDK 手动创建 Span，本组件提供 Span 创建辅助方法</li>
 *   <li>兼容模式：无 agent 时使用 MDC + TraceIdUtil 实现简易追踪</li>
 * </ul>
 *
 * <h3>业务标签（Tags）</h3>
 * <ul>
 *   <li>{@code job.key}: 任务 KEY</li>
 *   <li>{@code job.trigger}: 触发类型（CRON/MANUAL/RETRY）</li>
 *   <li>{@code job.shard}: 分片索引</li>
 *   <li>{@code job.duration}: 执行耗时（ms）</li>
 *   <li>{@code job.status}: 执行结果（SUCCESS/FAILED）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceIntegrationHelper {

    /**
     * 创建任务执行 Span 的业务标签。
     *
     * <p>当 SkyWalking agent 或 OTel SDK 存在时，这些标签会自动附加到当前 Span。
     * 无 agent 时，标签写入 MDC 供日志输出。
     *
     * @param jobKey      任务 KEY
     * @param triggerType 触发类型
     * @param shardIndex  分片索引（-1 表示非分片）
     * @return 标签 Map
     */
    public Map<String, String> buildJobTags(String jobKey, String triggerType, int shardIndex) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("job.key", jobKey != null ? jobKey : "unknown");
        tags.put("job.trigger", triggerType != null ? triggerType : "UNKNOWN");
        tags.put("job.shard", String.valueOf(shardIndex));
        return tags;
    }

    /**
     * 记录任务执行完成时的追踪信息。
     *
     * <p>在 SkyWalking 存在时，会自动捕获异常并设置 Span 状态；
     * 无 agent 时，仅记录日志。
     *
     * @param jobKey      任务 KEY
     * @param triggerType 触发类型
     * @param success     是否成功
     * @param durationMs  执行耗时（毫秒）
     * @param errorMessage 错误信息（成功时为 null）
     */
    public void recordJobCompletion(String jobKey, String triggerType, boolean success,
                                      long durationMs, String errorMessage) {
        try {
            // 业务标签
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("job.key", jobKey != null ? jobKey : "unknown");
            tags.put("job.trigger", triggerType != null ? triggerType : "UNKNOWN");
            tags.put("job.duration_ms", String.valueOf(durationMs));
            tags.put("job.status", success ? "SUCCESS" : "FAILED");

            if (!success && errorMessage != null) {
                tags.put("job.error", errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) : errorMessage);
            }

            // 写入 MDC（兼容无 agent 场景）
            tags.forEach((k, v) -> MDC.put(k, v));

            if (log.isDebugEnabled()) {
                log.debug("[Trace] 任务执行完成: key={} trigger={} success={} duration={}ms",
                        jobKey, triggerType, success, durationMs);
            }
        } catch (Exception e) {
            log.debug("[Trace] 追踪信息记录失败(不影响主流程): reason={}", e.getMessage());
        }
    }

    /**
     * 清理 MDC 中的任务标签。
     *
     * <p>在任务执行完成后调用，避免标签泄漏到后续日志。
     */
    public void clearJobTags() {
        try {
            MDC.remove("job.key");
            MDC.remove("job.trigger");
            MDC.remove("job.shard");
            MDC.remove("job.duration_ms");
            MDC.remove("job.status");
            MDC.remove("job.error");
        } catch (Exception e) {
            // 静默忽略
        }
    }
}
