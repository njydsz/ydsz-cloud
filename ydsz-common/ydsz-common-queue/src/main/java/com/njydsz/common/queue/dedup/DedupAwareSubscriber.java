package com.njydsz.common.queue.dedup;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessageHandler;
import com.njydsz.common.queue.service.IMessageSubscriber;

import lombok.extern.slf4j.Slf4j;

/**
 * 幂等去重订阅者装饰器
 *
 * <p>包装 {@link IMessageSubscriber}，在处理消息前自动检查消息是否已被消费过。
 * 基于 {@link MessageDeduplicator} 实现幂等性保证，重复消息会被自动跳过。
 *
 * <p>使用 traceId 作为去重键，在去重窗口内的重复消息将被丢弃。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DedupAwareSubscriber implements IMessageSubscriber {

    private final IMessageSubscriber delegate;
    private final MessageDeduplicator deduplicator;

    public DedupAwareSubscriber(IMessageSubscriber delegate, MessageDeduplicator deduplicator) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate subscriber 不能为空");
        }
        if (deduplicator == null) {
            throw new IllegalArgumentException("deduplicator 不能为空");
        }
        this.delegate = delegate;
        this.deduplicator = deduplicator;
    }

    @Override
    public String subscribe() {
        String payload = delegate.subscribe();
        if (payload == null) {
            return null;
        }
        QueueMessage message = QueueMessage.fromPayload(payload);
        if (message == null) {
            return payload;
        }
        if (deduplicator.checkAndMark(message.getTraceId())) {
            log.debug("[DedupSubscriber] 重复消息已跳过，traceId={}", message.getTraceId());
            return null;
        }
        return payload;
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        return delegate.subscribeAsync(wrapHandler(handler));
    }

    /**
     * 包装 handler，在处理前检查去重
     */
    private IMessageHandler wrapHandler(IMessageHandler handler) {
        if (handler == null) {
            return null;
        }
        return message -> {
            if (deduplicator.checkAndMark(message.getTraceId())) {
                log.debug("[DedupSubscriber] 重复消息已跳过，traceId={}", message.getTraceId());
                return;
            }
            handler.onMessage(message);
        };
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /**
     * 获取被装饰的原始订阅者
     */
    public IMessageSubscriber getDelegate() {
        return delegate;
    }

    /**
     * 获取去重器实例
     */
    public MessageDeduplicator getDeduplicator() {
        return deduplicator;
    }
}
