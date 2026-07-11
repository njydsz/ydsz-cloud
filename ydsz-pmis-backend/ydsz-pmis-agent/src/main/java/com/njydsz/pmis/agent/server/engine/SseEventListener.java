package com.njydsz.pmis.agent.server.engine.stream;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.server.engine.react.ReActDecision;
import com.njydsz.pmis.agent.server.engine.react.ReActResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 把 ReAct 事件转换为 SSE 推送的监听器（P2-1 落地）
 *
 * <p>实现 {@link ReActEventListener}，把 {@link StreamEvent} 序列化为 SSE event/data 推送给客户端。
 * 对标 Coze / Dify 的 Chat Stream API。
 *
 * <p>SSE 事件格式：
 * <pre>
 * event: STEP_START
 * data: {"stepIndex":1,"timestamp":1700000000000}
 *
 * event: THOUGHT
 * data: {"stepIndex":1,"thought":"...","timestamp":...}
 *
 * event: ACTION
 * data: {"stepIndex":1,"action":"bpmn_validate","parameters":{...},"timestamp":...}
 *
 * event: OBSERVATION
 * data: {"stepIndex":1,"observation":"校验通过","timestamp":...}
 *
 * event: FINAL_ANSWER
 * data: {"stepIndex":2,"finalAnswer":"<bpmn:definitions>...","timestamp":...}
 *
 * event: DONE
 * data: {"success":true,"totalSteps":2,"timestamp":...}
 *
 * event: ERROR
 * data: {"stepIndex":0,"failureReason":"...","timestamp":...}
 * </pre>
 *
 * <p><b>线程安全</b>：{@link SseEmitter#send} 是线程安全的，本监听器可在异步线程调用。
 *
 * <p><b>异常处理</b>：所有 SSE 推送异常被捕获并记录日志，不向 ReActLoop 抛出（避免影响主流程）。
 * 客户端断开会话时，后续 send 会失败，日志会记录但 ReAct 仍会继续完成。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
@Slf4j
public class SseEventListener implements ReActEventListener {

    /** SSE 推送目标 */
    private final SseEmitter emitter;
    /** 客户端是否已断开（一旦检测到 IOException，后续推送跳过） */
    private volatile boolean clientDisconnected = false;

    /**
     * 心跳调度器（P2-5）。
     *
     * <p>每个 SseEventListener 实例独占一个单线程调度器，避免多 SSE 连接共享调度器
     * 导致一个连接的心跳任务异常影响其他连接。心跳任务本身非常轻量（仅推送时间戳），
     * 独占调度器的开销可忽略。
     */
    private final ScheduledExecutorService heartbeatScheduler;

    /** 心跳定时任务句柄（用于取消，P2-5） */
    private ScheduledFuture<?> heartbeatFuture;

    /** 心跳间隔（秒，P2-5） */
    private final long heartbeatIntervalSeconds;

    /**
     * 构造 SSE 监听器（使用默认心跳间隔 15s，P2-5）。
     *
     * @param emitter Spring MVC SseEmitter
     */
    public SseEventListener(SseEmitter emitter) {
        this(emitter, 15L);
    }

    /**
     * 构造 SSE 监听器（指定心跳间隔，P2-5）。
     *
     * <p>心跳保活机制：LLM 调用耗时较长时（如 10s+），客户端或中间代理（nginx /
     * ELB）可能因超时断开 SSE 连接。心跳事件定期推送，告知连接仍在活跃，
     * 防止超时断连。心跳在 {@link #onComplete} / {@link #onError} 时自动停止。
     *
     * @param emitter                   Spring MVC SseEmitter
     * @param heartbeatIntervalSeconds  心跳间隔（秒），<= 0 表示禁用心跳
     */
    public SseEventListener(SseEmitter emitter, long heartbeatIntervalSeconds) {
        this.emitter = emitter;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.heartbeatScheduler = heartbeatIntervalSeconds > 0
                ? Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "sse-heartbeat");
                    t.setDaemon(true);
                    return t;
                })
                : null;
    }

    @Override
    public void onStepStart(int stepIndex) {
        // P2-5：首个步骤开始时启动心跳（仅启动一次）
        startHeartbeat();
        send(StreamEvent.of(StreamEvent.Type.STEP_START, stepIndex));
    }

    /**
     * LLM 流式 token 增量推送（P4-1 落地）。
     *
     * <p>每收到一个 token 片段即推送 LLM_DELTA 事件，
     * 前端可基于此实现打字机效果。
     */
    @Override
    public void onToken(int stepIndex, String tokenDelta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", tokenDelta == null ? "" : tokenDelta);
        send(StreamEvent.of(StreamEvent.Type.LLM_DELTA, stepIndex, payload));
    }

    @Override
    public void onThought(int stepIndex, String thought) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("thought", thought == null ? "" : thought);
        send(StreamEvent.of(StreamEvent.Type.THOUGHT, stepIndex, payload));
    }

    @Override
    public void onAction(int stepIndex, ReActDecision decision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", decision.getAction());
        payload.put("parameters", decision.getParameters());
        send(StreamEvent.of(StreamEvent.Type.ACTION, stepIndex, payload));
    }

    @Override
    public void onObservation(int stepIndex, String observation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("observation", observation == null ? "" : observation);
        send(StreamEvent.of(StreamEvent.Type.OBSERVATION, stepIndex, payload));
    }

    @Override
    public void onFinalAnswer(int stepIndex, String finalAnswer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finalAnswer", finalAnswer == null ? "" : finalAnswer);
        send(StreamEvent.of(StreamEvent.Type.FINAL_ANSWER, stepIndex, payload));
    }

    @Override
    public void onStepEnd(int stepIndex) {
        send(StreamEvent.of(StreamEvent.Type.STEP_END, stepIndex));
    }

    @Override
    public void onComplete(ReActResult result) {
        // P2-5：完成时停止心跳
        stopHeartbeat();
        if (result == null) {
            send(StreamEvent.error(0, "result is null"));
        } else {
            send(StreamEvent.done(result.getTotalSteps(), result.isSuccess()));
        }
        completeEmitter();
    }

    @Override
    public void onError(int stepIndex, Throwable error) {
        // P2-5：异常时停止心跳（onComplete 会再次调用 stopHeartbeat，但内部有幂等保护）
        stopHeartbeat();
        String reason = error == null ? "unknown" : error.getMessage();
        send(StreamEvent.error(stepIndex, reason));
        // 不在这里 complete，让 onComplete 处理
    }

    /**
     * 安全推送 SSE 事件（捕获 IOException 标记客户端断开）。
     *
     * @param event 流式事件
     */
    private void send(StreamEvent event) {
        if (clientDisconnected || emitter == null) {
            return;
        }
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.getType().name())
                    .data(JSON.toJSONString(event));
            emitter.send(builder);
        } catch (IOException | IllegalStateException e) {
            log.warn("[SSE] 客户端断开或推送失败: {}", e.getMessage());
            clientDisconnected = true;
            // P2-5：客户端断开时停止心跳，避免无效推送
            stopHeartbeat();
        } catch (Exception e) {
            log.warn("[SSE] 推送异常: {}", e.getMessage());
        }
    }

    /**
     * 启动心跳定时任务（P2-5）。
     *
     * <p>仅在心跳调度器存在且心跳任务未启动时生效（幂等）。
     * 心跳任务定期推送 {@link StreamEvent.Type#HEARTBEAT} 事件，
     * 推送失败（客户端断开）时自动停止。
     */
    private void startHeartbeat() {
        if (heartbeatScheduler == null || heartbeatFuture != null) {
            return;
        }
        heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (clientDisconnected) {
                stopHeartbeat();
                return;
            }
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .name(StreamEvent.Type.HEARTBEAT.name())
                        .data(JSON.toJSONString(StreamEvent.heartbeat()));
                emitter.send(builder);
            } catch (IOException | IllegalStateException e) {
                log.debug("[SSE] 心跳推送失败，客户端可能已断开: {}", e.getMessage());
                clientDisconnected = true;
                stopHeartbeat();
            } catch (Exception e) {
                log.debug("[SSE] 心跳推送异常: {}", e.getMessage());
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
        log.debug("[SSE] 心跳已启动, 间隔 {}s", heartbeatIntervalSeconds);
    }

    /**
     * 停止心跳定时任务并关闭调度器（P2-5）。
     *
     * <p>幂等：多次调用安全。停止后不再推送心跳事件。
     */
    private void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
            log.debug("[SSE] 心跳调度器已关闭");
        }
    }

    /**
     * 完成 SseEmitter（通知 Spring MVC 关闭流）。
     */
    private void completeEmitter() {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("[SSE] complete 异常: {}", e.getMessage());
        }
    }

    /** 客户端是否已断开（用于外部判断是否需要继续推送） */
    public boolean isClientDisconnected() {
        return clientDisconnected;
    }
}
