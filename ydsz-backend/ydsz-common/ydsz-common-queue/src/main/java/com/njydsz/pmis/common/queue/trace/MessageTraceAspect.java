package com.njydsz.common.queue.trace;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.util.id.TracerUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息轨迹 AOP 切面
 *
 * <p>拦截 {@link IMessagePublisher#publish(QueueMessage)} 方法，
 * 自动注入 traceId 到消息头，记录消息发送时间戳和轨迹。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class MessageTraceAspect {

    private final MessageTraceRecorder traceRecorder;

    public MessageTraceAspect(MessageTraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    /**
     * 拦截 IMessagePublisher.publish(QueueMessage) 方法
     *
     * @param joinPoint 切点
     * @return 方法返回值
     */
    @Around("execution(void com.njydsz.common.queue.service.IMessagePublisher.publish(com.njydsz.common.queue.domain.QueueMessage)) && args(message)")
    public Object aroundPublish(ProceedingJoinPoint joinPoint, QueueMessage message) throws Throwable {
        if (message == null) {
            return joinPoint.proceed();
        }

        String traceId = resolveTraceId(message);
        String messageId = resolveMessageId(message, traceId);

        // 构建轨迹记录
        MessageTrace trace = MessageTrace.builder()
                .messageId(messageId)
                .topic(resolveTopic(joinPoint))
                .producerId(resolveProducerId(joinPoint))
                .status(MessageTrace.TraceStatus.SENT)
                .traceId(traceId)
                .retryCount(message.getRetryCount() != null ? message.getRetryCount() : 0)
                .build();
        trace.addTimestamp("sent");

        // 记录轨迹
        try {
            traceRecorder.record(trace);
        } catch (Exception e) {
            log.warn("[MessageTrace] 轨迹记录失败，messageId={}, traceId={}", messageId, traceId, e);
        }

        log.debug("[MessageTrace] 消息发送轨迹，messageId={}, traceId={}, topic={}",
                messageId, traceId, trace.getTopic());

        return joinPoint.proceed();
    }

    /**
     * 解析 traceId：优先使用消息已有的 traceId，否则从 MDC 获取，最后自动生成
     */
    private String resolveTraceId(QueueMessage message) {
        if (message.getTraceId() != null && !message.getTraceId().isEmpty()) {
            return message.getTraceId();
        }
        String mdcTraceId = MessageTracer.extractTraceId();
        if (mdcTraceId != null && !mdcTraceId.isEmpty()) {
            return mdcTraceId;
        }
        return TracerUtils.generateTraceId();
    }

    /**
     * 解析消息ID：使用 traceId + timestamp 组合
     */
    private String resolveMessageId(QueueMessage message, String traceId) {
        return "msg-" + traceId.substring(0, Math.min(traceId.length(), 8)) + "-" + System.currentTimeMillis();
    }

    /**
     * 解析主题/通道名称
     */
    private String resolveTopic(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target instanceof IMessagePublisher) {
            String channel = ((IMessagePublisher) target).getChannel();
            return channel != null ? channel : "unknown";
        }
        return "unknown";
    }

    /**
     * 解析生产者ID
     */
    private String resolveProducerId(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName();
    }
}
