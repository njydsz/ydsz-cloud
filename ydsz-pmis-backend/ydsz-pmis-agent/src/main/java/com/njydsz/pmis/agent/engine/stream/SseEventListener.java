package com.njydsz.pmis.agent.engine.stream;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * 构造 SSE 监听器。
     *
     * @param emitter Spring MVC SseEmitter
     */
    public SseEventListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onStepStart(int stepIndex) {
        send(StreamEvent.of(StreamEvent.Type.STEP_START, stepIndex));
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
        if (result == null) {
            send(StreamEvent.error(0, "result is null"));
        } else {
            send(StreamEvent.done(result.getTotalSteps(), result.isSuccess()));
        }
        completeEmitter();
    }

    @Override
    public void onError(int stepIndex, Throwable error) {
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
        } catch (Exception e) {
            log.warn("[SSE] 推送异常: {}", e.getMessage());
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
