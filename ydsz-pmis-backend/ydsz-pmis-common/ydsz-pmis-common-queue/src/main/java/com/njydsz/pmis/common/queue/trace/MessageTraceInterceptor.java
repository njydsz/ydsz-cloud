package com.njydsz.pmis.common.queue.trace;

import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息消费端轨迹拦截器
 *
 * <p>包装 {@link IMessageHandler}，在消息消费前后自动记录轨迹信息。
 * 消费成功时记录 CONSUMED 状态，消费失败时记录 FAILED 状态及异常信息。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * IMessageHandler originalHandler = message -> processMessage(message);
 * IMessageHandler tracedHandler = MessageTraceInterceptor.wrap(
 *     originalHandler, traceRecorder, consumerId);
 * subscriber.subscribeAsync(tracedHandler);
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
     * @param delegate     原始消息处理器
     * @param traceRecorder 轨迹记录器
     * @param consumerId   消费者ID
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

        // 记录消费开始
        MessageTrace trace = MessageTrace.builder()
                .messageId(messageId)
                .topic(topic)
                .consumerId(consumerId)
                .status(MessageTrace.TraceStatus.CONSUMED)
                .traceId(traceId)
                .retryCount(message.getRetryCount() != null ? message.getRetryCount() : 0)
                .build();
        trace.addTimestamp("consumed");

        try {
            // 注入 traceId 到 MDC
            if (traceId != null) {
                MessageTracer.injectTraceId(traceId);
            }

            // 执行实际的消息处理
            delegate.onMessage(message);

            // 记录消费成功轨迹
            try {
                traceRecorder.record(trace);
            } catch (Exception e) {
                log.warn("[MessageTrace] 消费成功轨迹记录失败，messageId={}, traceId={}", messageId, traceId, e);
            }

            log.debug("[MessageTrace] 消息消费成功，messageId={}, traceId={}, consumerId={}",
                    messageId, traceId, consumerId);
        } catch (Exception e) {
            // 记录消费失败轨迹
            trace.setStatus(MessageTrace.TraceStatus.FAILED);
            trace.addTimestamp("failed");
            trace.setErrorMessage(e.getMessage());

            try {
                traceRecorder.record(trace);
            } catch (Exception ex) {
                log.warn("[MessageTrace] 消费失败轨迹记录失败，messageId={}, traceId={}", messageId, traceId, ex);
            }

            log.error("[MessageTrace] 消息消费失败，messageId={}, traceId={}, consumerId={}, error={}",
                    messageId, traceId, consumerId, e.getMessage());

            throw e;
        } finally {
            // 清理 MDC
            MessageTracer.clearTraceId();
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
