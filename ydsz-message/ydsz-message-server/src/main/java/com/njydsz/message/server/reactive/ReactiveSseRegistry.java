package com.njydsz.message.server.reactive;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 响应式 SSE 事件注册表。
 *
 * <p>持有 {@link Sinks.Many} 多播事件管道，为 Controller 层和 Service 层提供统一的事件发布 / 订阅入口，
 * 避免 Controller 依赖 Service、Service 又依赖 Controller 的循环依赖问题。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>使用 {@link Sinks#many()}.multicast() 实现多播，所有订阅者共享同一事件流</li>
 *   <li>背压策略：BUFFER（缓冲最多 256 条未消费事件，避免生产者阻塞）</li>
 *   <li>终端操作 {@link Sinks.Many#tryEmitNext} 返回 {@link Sinks.EmitResult} 指示成功 / 失败原因</li>
 * </ul>
 *
 * <p><b>注意：</b>该类为有状态单例 Bean，生命周期随 Spring 容器管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReactiveEvent
 * @see com.njydsz.message.web.controller.ReactiveNotificationController
 * @see ReactiveEventPublisher
 */
@Slf4j
@Component
public class ReactiveSseRegistry {

    /**
     * 事件缓冲区大小。
     *
     * <p>当订阅者消费速度落后于事件生产速度时，最多缓冲 224 条事件。超出后 {@link #publish} 将返回
     * {@link Sinks.EmitResult#FAIL_OVERFLOW}。
     */
    private static final int BUFFER_SIZE = 256;

    /** 多播事件管道 */
    private final Sinks.Many<ReactiveEvent> eventSink =
            Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);

    /**
     * 获取原始事件管道（用于订阅）。
     *
     * @return 事件 Flux（惰性，首次订阅时激活）
     */
    public Flux<ReactiveEvent> getEventStream() {
        return eventSink.asFlux();
    }

    /**
     * 获取事件 Sink（用于发布）。
     *
     * @return 多播 Sink 实例
     */
    public Sinks.Many<ReactiveEvent> getEventSink() {
        return eventSink;
    }

    /**
     * 向事件管道推送一条事件。
     *
     * @param event 待推送的事件（不可为 null）
     * @return 发布结果（{@link Sinks.EmitResult#SUCCESS} 表示成功）
     */
    public Sinks.EmitResult publish(ReactiveEvent event) {
        Sinks.EmitResult result = eventSink.tryEmitNext(event);
        if (!result.isSuccess()) {
            log.warn("[ReactiveSSE] 事件推送失败: eventId={} result={}", event.getEventId(), result);
        }
        return result;
    }

    /**
     * Bean 销毁时完成事件管道，释放订阅资源。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[ReactiveSSE] 注册表关闭，完成事件管道");
        eventSink.tryEmitComplete();
    }
}
