package com.njydsz.common.queue.trace;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessageHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息消费端轨迹拦截器
 *
 * <p>包装 {@link IMessageHandler}，在消息消费前后自动记录轨迹信息。
 * 消费开始时记录 DELIVERED 状态，消费成功后记录 CONSUMED 状态，消费失败时记录 FAILED 状态及异常信息。
 *
 * <p><b>轨迹状态流转：</b>
 * <pre>{@code
 * DELIVERED -> CONSUMED  (消费成功)
 *           \-> FAILED    (消费失败)
 * }</pre>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * IMessageHandler originalHandler = message -> processMessage(message);
 * IMessageHandler tracedHandler = MessageTraceInterceptor.wrap(
 *     originalHandler, traceRecorder, consumerId);
 * subscriber.subscribeAsync(tracedHandler);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MessageTraceInterceptor implements IMessageHandler {

    private final IMessageHandler delegate;
    private final MessageTraceRecorder traceRecorder;
    private final String consumerId;

    public MessageTraceInterceptor(IMessageHandler delegate,
                                   MessageTraceRecorder traceRecorder,
                                   String consumerId) {
        if (delegate == null) {
            throw new IllegalArgumentException("消息处理器不能为空");
        }
        if (traceRecorder == null) {
            throw new IllegalArgumentException("轨迹记录器不能为空");
        }
        this.delegate = delegate;
        this.traceRecorder = traceRecorder;
        this.consumerId = consumerId != null ? consumerId : "unknown";
    }

    /**
     * 包装消息处理器，添加轨迹记录能力
     *
     * @param delegate      原始消息处理器
     * @param traceRecorder 轨迹记录器
     * @param consumerId    消费者ID
     * @return 包装后的消息处理器
     */
    public static MessageTraceInterceptor wrap(IMessageHandler delegate,
                                               MessageTraceRecorder traceRecorder,
                                               String consumerId) {
        return new MessageTraceInterceptor(delegate, traceRecorder, consumerId);
    }

    @Override
    public void onMessage(QueueMessage message) throws Exception {
        if (message == null) {
            delegate.onMessage(null);
            return;
        }

        String traceId = message.getTraceId();
        String messageId = buildMessageId(message, traceId);
        String topic = resolveTopic();

        // 注入 traceId 到 MDC，保证消费过程中的日志可追踪
        if (traceId != null && !traceId.isEmpty()) {
            MessageTracer.injectTraceId(traceId);
        }

        // 记录 DELIVERED 状态（消息已投递到消费者）
        MessageTrace deliveredTrace = MessageTrace.builder()
                .messageId(messageId)
                .topic(topic)
                .consumerId(consumerId)
                .status(MessageTrace.TraceStatus.DELIVERED)
                .traceId(traceId)
                .retryCount(message.getRetryCount() != null ? message.getRetryCount() : 0)
                .build();
        deliveredTrace.addTimestamp("delivered");
        recordTraceSafely(deliveredTrace);

        // 记录消费开始
        MessageTrace consumeTrace = MessageTrace.builder()
                .messageId(messageId)
                .topic(topic)
                .consumerId(consumerId)
                .status(MessageTrace.TraceStatus.CONSUMED)
                .traceId(traceId)
                .retryCount(message.getRetryCount() != null ? message.getRetryCount() : 0)
                .build();

        try {
            // 执行实际的消息处理
            delegate.onMessage(message);

            // 记录消费成功轨迹
            consumeTrace.addTimestamp("consumed");
            recordTraceSafely(consumeTrace);

            log.debug("[MessageTrace] 消息消费成功，messageId={}, traceId={}, consumerId={}",
                    messageId, traceId, consumerId);
        } catch (Exception e) {
            // 记录消费失败轨迹
            consumeTrace.setStatus(MessageTrace.TraceStatus.FAILED);
            consumeTrace.addTimestamp("failed");
            consumeTrace.setErrorMessage(e.getMessage());
            recordTraceSafely(consumeTrace);

            log.error("[MessageTrace] 消息消费失败，messageId={}, traceId={}, consumerId={}, error={}",
                    messageId, traceId, consumerId, e.getMessage());

            throw e;
        } finally {
            // 清理 MDC 中的 traceId
            MessageTracer.clearTraceId();
        }
    }

    /**
     * 安全地记录轨迹，避免轨迹记录失败影响消息消费
     *
     * @param trace 轨迹记录
     */
    private void recordTraceSafely(MessageTrace trace) {
        try {
            traceRecorder.record(trace);
        } catch (Exception e) {
            log.warn("[MessageTrace] 轨迹记录失败，messageId={}, status={}, error={}",
                    trace.getMessageId(), trace.getStatus(), e.getMessage());
        }
    }

    private String buildMessageId(QueueMessage message, String traceId) {
        String prefix = traceId != null && !traceId.isEmpty()
                ? traceId.substring(0, Math.min(traceId.length(), 8))
                : "unknown";
        return "msg-" + prefix + "-" + System.currentTimeMillis();
    }

    private String resolveTopic() {
        return "unknown";
    }
}
