package com.njydsz.common.queue.resilience;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 熔断器发布者装饰器
 *
 * <p>包装 {@link IMessagePublisher}，在发布消息前检查熔断器状态。
 * 当熔断器处于 OPEN 状态时，快速拒绝消息发送并记录告警日志，
 * 避免持续向不可用的 MQ 引擎发送请求导致线程阻塞和级联故障。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class CircuitBreakerPublisher implements IMessagePublisher {

    private final IMessagePublisher delegate;
    private final QueueCircuitBreaker circuitBreaker;

    public CircuitBreakerPublisher(IMessagePublisher delegate, QueueCircuitBreaker circuitBreaker) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate publisher 不能为空");
        }
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("circuitBreaker 不能为空");
        }
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void publish(String message) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[CircuitBreaker] 熔断中，拒绝消息发布，channel={}, state={}",
                    delegate.getChannel(), circuitBreaker.getState());
            return;
        }
        try {
            delegate.publish(message);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public void publish(QueueMessage message) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[CircuitBreaker] 熔断中，拒绝消息发布，channel={}, state={}",
                    delegate.getChannel(), circuitBreaker.getState());
            return;
        }
        try {
            delegate.publish(message);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public void publishDelayed(QueueMessage message, long delayMillis) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[CircuitBreaker] 熔断中，拒绝延迟消息发布，channel={}, state={}",
                    delegate.getChannel(), circuitBreaker.getState());
            return;
        }
        try {
            delegate.publishDelayed(message, delayMillis);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public void publishSequential(QueueMessage message) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[CircuitBreaker] 熔断中，拒绝顺序消息发布，channel={}, state={}",
                    delegate.getChannel(), circuitBreaker.getState());
            return;
        }
        try {
            delegate.publishSequential(message);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public void publishBatch(List<QueueMessage> messages) {
        if (!circuitBreaker.allowRequest()) {
            log.warn("[CircuitBreaker] 熔断中，拒绝批量消息发布，channel={}, state={}",
                    delegate.getChannel(), circuitBreaker.getState());
            return;
        }
        try {
            delegate.publishBatch(messages);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            throw e;
        }
    }

    @Override
    public String getChannel() {
        return delegate.getChannel();
    }

    @Override
    public boolean isActive() {
        return delegate.isActive() && circuitBreaker.getState() != QueueCircuitBreaker.State.OPEN;
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 获取被装饰的原始发布者
     */
    public IMessagePublisher getDelegate() {
        return delegate;
    }

    /**
     * 获取熔断器实例
     */
    public QueueCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
