package com.njydsz.cronjob.web.controller.log;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.server.core.logger.LogStreamManager;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SSE 实时日志推送 Controller（P0-2）。
 *
 * <p>提供基于 Server-Sent Events（SSE）的实时日志推送能力，对标 XXL-Job / PowerJob 的在线日志白屏化体验。
 * 前端通过 {@code EventSource} 建立 SSE 连接，实时接收任务执行日志，无需手动刷新。
 *
 * <h3>前端使用示例</h3>
 * <pre>
 * const evtSource = new EventSource('/api/v1/cronjob/log/stream/log123');
 * evtSource.addEventListener('log', (e) =&gt; {
 *     const line = JSON.parse(e.data);
 *     console.log(line.lineNo, line.logLevel, line.content);
 * });
 * evtSource.addEventListener('complete', (e) =&gt; {
 *     const result = JSON.parse(e.data);
 *     console.log('Task finished:', result.success);
 *     evtSource.close();
 * });
 * </pre>
 *
 * <h3>SSE 事件类型</h3>
 * <ul>
 *   <li>{@code log} - 日志行事件（含 lineNo/logLevel/content/createdAt）</li>
 *   <li>{@code complete} - 任务完成事件（含 success 字段）</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #stream} - 建立 SSE 连接，订阅指定 logId 的实时日志</li>
 *   <li>{@link #getSubscriberCount} - 查询指定 logId 的订阅者数量</li>
 *   <li>{@link #getActiveStreamCount} - 查询全局活跃 SSE 流数量</li>
 * </ul>
 *
 * <h3>安全</h3>
 * 接口加 {@link AuthApiPermission} 权限控制（{@link PermissionCodes#CRONJOB_LOG_VIEW}）；
 * SSE 连接本身是单向推送（服务端 → 客户端），不写操作无需幂等/限流/审计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "SSE 实时日志推送", description = "基于 Server-Sent Events 的任务执行日志实时推送")
@RestController
@RequestMapping("/api/v1/cronjob/log")
@RequiredArgsConstructor
public class SseLogController {

    /** SSE 日志流管理器（管理实时推送通道） */
    private final LogStreamManager logStreamManager;
    /** 任务日志内容服务（查询历史日志行） */
    private final JobLogContentService jobLogContentService;

    /**
     * 建立 SSE 连接，订阅指定 logId 的实时日志推送。
     *
     * <p>连接建立后：
     * <ol>
     *   <li>从 DB 查询已有日志行（{@code lineNo > 0}）并推送给客户端（历史日志回放）</li>
     *   <li>由 {@link LogStreamManager} 维护实时通道，后续新增日志行通过 {@code log} 事件推送</li>
     *   <li>任务完成后推送 {@code complete} 事件并关闭连接</li>
     * </ol>
     *
     * <p>异常处理：历史日志推送失败不阻塞 SSE 连接建立（仅记录 warn 日志），
     * 保证前端仍可接收后续实时日志。
     *
     * @param logId 执行日志 ID（{@code ydsz_job_log.id}）
     * @return SseEmitter（Spring MVC 的 SSE 句柄）
     */
    @Operation(summary = "建立 SSE 连接订阅实时日志")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_LOG_VIEW)
    @GetMapping("/stream/{logId}")
    public SseEmitter stream(@PathVariable String logId) {
        log.info("[SseLog] SSE 连接建立: logId={}", logId);
        // 1. 向 LogStreamManager 注册订阅（获取 SseEmitter 句柄）
        SseEmitter emitter = logStreamManager.subscribe(logId);

        // 2. 推送历史日志（连接建立前的日志行，从 lineNo=0 开始）
        try {
            List<JobLogContent> history = jobLogContentService.listAfterLine(logId, 0);
            if (history != null && !history.isEmpty()) {
                logStreamManager.pushHistory(logId, history);
                log.debug("[SseLog] 推送历史日志: logId={} lines={}", logId, history.size());
            }
        } catch (Exception e) {
            // 历史日志推送失败不阻塞 SSE 连接（仅记录 warn）
            log.warn("[SseLog] 推送历史日志失败: logId={} reason={}", logId, e.getMessage());
        }

        return emitter;
    }

    /**
     * 查询指定 logId 的当前 SSE 订阅者数量（监控用）。
     *
     * <p>典型场景：监控面板展示"当前有 X 个客户端正在订阅任务 Y 的实时日志"。
     *
     * @param logId 执行日志 ID
     * @return 订阅者数量（0 表示无活跃订阅）
     */
    @Operation(summary = "查询指定日志的 SSE 订阅者数量")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_LOG_VIEW)
    @GetMapping("/stream/{logId}/subscribers")
    public int getSubscriberCount(@PathVariable String logId) {
        return logStreamManager.getSubscriberCount(logId);
    }

    /**
     * 查询当前活跃的 SSE 流数量（监控用）。
     *
     * <p>典型场景：全局 SSE 连接池监控，识别异常增长的连接数（可能是客户端未正确关闭导致泄漏）。
     *
     * @return 活跃流数量
     */
    @Operation(summary = "查询全局活跃 SSE 流数量")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_LOG_VIEW)
    @GetMapping("/stream/activeCount")
    public int getActiveStreamCount() {
        return logStreamManager.getActiveStreamCount();
    }
}
