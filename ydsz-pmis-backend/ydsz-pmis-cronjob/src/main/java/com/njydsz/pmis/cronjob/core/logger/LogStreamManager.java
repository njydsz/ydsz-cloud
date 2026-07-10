package com.njydsz.pmis.cronjob.core.logger;

import com.njydsz.pmis.cronjob.entity.log.JobLogContentDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * P0-2: 日志流推送管理器（SSE 实时推送）。
 *
 * <p>管理按 logId 分组的 SSE 连接，当任务执行过程中产生新日志行时，
 * 通过 {@link #pushLogLine(String, JobLogContentDO)} 实时推送到所有订阅该 logId 的客户端。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>前端通过 {@code GET /cronjob/log/stream/{logId}} 建立 SSE 连接</li>
 *   <li>连接建立后，先推送历史日志（从 DB 查询）</li>
 *   <li>任务执行中，{@link JobLoggerImpl} 每写一行日志即调用 {@link #pushLogLine} 推送</li>
 *   <li>任务完成后，推送结束事件并关闭 SSE 连接</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 存储 logId → emitters 映射</li>
 *   <li>每个 logId 的 emitters 使用 {@link CopyOnWriteArrayList} 保证并发安全</li>
 *   <li>SSE 发送失败时自动移除失效连接</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class LogStreamManager {

    /** SSE 超时时间（毫秒，默认 30 分钟，覆盖长任务执行场景） */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /** logId → SSE emitters 映射 */
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    /**
     * 注册 SSE 连接，订阅指定 logId 的日志推送。
     *
     * @param logId 执行日志 ID
     * @return 创建的 SseEmitter
     */
    public SseEmitter subscribe(String logId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emittersMap.computeIfAbsent(logId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 设置回调：超时/完成/错误时自动清理
        emitter.onCompletion(() -> removeEmitter(logId, emitter));
        emitter.onTimeout(() -> {
            log.debug("[LogStream] SSE 连接超时: logId={}", logId);
            emitter.complete();
            removeEmitter(logId, emitter);
        });
        emitter.onError(e -> {
            log.debug("[LogStream] SSE 连接错误: logId={} reason={}", logId, e.getMessage());
            removeEmitter(logId, emitter);
        });

        log.debug("[LogStream] SSE 订阅: logId={} totalSubscribers={}",
                logId, emittersMap.getOrDefault(logId, new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    /**
     * 推送单条日志行到所有订阅该 logId 的客户端。
     *
     * <p>发送失败的连接自动移除。无订阅者时静默跳过。
     *
     * @param logId 执行日志 ID
     * @param line  日志行
     */
    public void pushLogLine(String logId, JobLogContentDO line) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(logId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(line));
            } catch (IOException | IllegalStateException e) {
                log.debug("[LogStream] SSE 推送失败, 移除连接: logId={} reason={}", logId, e.getMessage());
                removeEmitter(logId, emitter);
            }
        }
    }

    /**
     * 批量推送日志行（用于连接建立后推送历史日志）。
     *
     * @param logId 执行日志 ID
     * @param lines 日志行列表
     */
    public void pushHistory(String logId, List<JobLogContentDO> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(logId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (JobLogContentDO line : lines) {
            pushLogLine(logId, line);
        }
    }

    /**
     * 推送任务完成事件，关闭该 logId 的所有 SSE 连接。
     *
     * @param logId   执行日志 ID
     * @param success 任务是否成功
     */
    public void pushComplete(String logId, boolean success) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(logId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("{\"success\":" + success + "}"));
                emitter.complete();
            } catch (IOException | IllegalStateException e) {
                log.debug("[LogStream] SSE 完成事件推送失败: logId={} reason={}", logId, e.getMessage());
            }
        }
        emittersMap.remove(logId);
        log.debug("[LogStream] SSE 连接已关闭: logId={} subscribers={}", logId, emitters.size());
    }

    /**
     * 移除指定 logId 下的一个 SSE 连接。
     *
     * @param logId   执行日志 ID
     * @param emitter 要移除的 emitter
     */
    private void removeEmitter(String logId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(logId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersMap.remove(logId, emitters);
            }
        }
    }

    /**
     * 获取指定 logId 的当前订阅者数量（供监控使用）。
     *
     * @param logId 执行日志 ID
     * @return 订阅者数量
     */
    public int getSubscriberCount(String logId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersMap.get(logId);
        return emitters != null ? emitters.size() : 0;
    }

    /**
     * 获取所有活跃的 logId 数量（供监控使用）。
     *
     * @return 活跃 logId 数量
     */
    public int getActiveStreamCount() {
        return emittersMap.size();
    }
}
