package com.njydsz.pmis.cronjob.web.controller.log;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.pmis.cronjob.domain.entity.log.JobLogContentDO;
import com.njydsz.pmis.cronjob.server.core.logger.LogStreamManager;
import com.njydsz.pmis.cronjob.server.service.log.JobLogContentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0-2: SSE 实时日志推送接口。
 *
 * <p>前端通过 EventSource 建立 SSE 连接，实时接收任务执行日志：
 * <pre>
 * const evtSource = new EventSource('/cronjob/log/stream/log123');
 * evtSource.addEventListener('log', (e) => {
 *     const line = JSON.parse(e.data);
 *     console.log(line.lineNo, line.logLevel, line.content);
 * });
 * evtSource.addEventListener('complete', (e) => {
 *     const result = JSON.parse(e.data);
 *     console.log('Task finished:', result.success);
 *     evtSource.close();
 * });
 * </pre>
 *
 * <h3>事件类型</h3>
 * <ul>
 *   <li>{@code log}: 日志行（含 lineNo/logLevel/content/createdAt）</li>
 *   <li>{@code complete}: 任务完成事件（含 success 字段）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@RestController
@RequestMapping("/cronjob/log")
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
     *   <li>从 DB 查询已有日志行并推送（历史日志）</li>
     *   <li>等待 {@link LogStreamManager} 推送实时日志行</li>
     *   <li>任务完成后推送 complete 事件并关闭连接</li>
     * </ol>
     *
     * @param logId 执行日志 ID
     * @return SseEmitter
     */
    @GetMapping("/stream/{logId}")
    public SseEmitter stream(@PathVariable String logId) {
        log.info("[SseLog] SSE 连接建立: logId={}", logId);
        SseEmitter emitter = logStreamManager.subscribe(logId);

        // 推送历史日志（连接建立前的日志）
        try {
            List<JobLogContentDO> history = jobLogContentService.listAfterLine(logId, 0);
            if (history != null && !history.isEmpty()) {
                logStreamManager.pushHistory(logId, history);
                log.debug("[SseLog] 推送历史日志: logId={} lines={}", logId, history.size());
            }
        } catch (Exception e) {
            log.warn("[SseLog] 推送历史日志失败: logId={} reason={}", logId, e.getMessage());
        }

        return emitter;
    }

    /**
     * 查询指定 logId 的当前 SSE 订阅者数量（监控用）。
     *
     * @param logId 执行日志 ID
     * @return 订阅者数量
     */
    @GetMapping("/stream/{logId}/subscribers")
    public int getSubscriberCount(@PathVariable String logId) {
        return logStreamManager.getSubscriberCount(logId);
    }

    /**
     * 查询当前活跃的 SSE 流数量（监控用）。
     *
     * @return 活跃流数量
     */
    @GetMapping("/stream/activeCount")
    public int getActiveStreamCount() {
        return logStreamManager.getActiveStreamCount();
    }
}
