package com.njydsz.message.web.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.message.server.reactive.ReactiveEvent;
import com.njydsz.message.server.reactive.ReactiveSseRegistry;

/**
 * WebFlux 响应式推送试点 — 使用 Flux 实现服务端事件流（SSE）。
 *
 * <h3>响应式特性</h3>
 * <ul>
 *   <li>基于 {@link ReactiveSseRegistry} 内的 Reactor {@link Sinks.Many} 实现多播事件推送</li>
 *   <li>背压策略：BUFFER（缓冲最多 256 条未消费事件）</li>
 *   <li>心跳：每 15 秒发送 comment 事件防代理超时</li>
 * </ul>
 *
 * <p><b>注意：</b>当前模块仍以 Spring MVC 为主容器运行，WebFlux 依赖声明为 {@code provided} scope，
 * 响应式 SSE 端点仅在引入 WebFlux 自动配置时完整生效（需独立 WebFlux 容器或 Reactive WebServer）。
 * 试点阶段主要用作对比参考，验证响应式编程模型的集成可行性。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReactiveSseRegistry
 * @see ReactiveEvent
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/message/reactive")
@RequiredArgsConstructor
public class ReactiveNotificationController {

    /** 心跳间隔（秒），用于防止代理超时。 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    /** 响应式事件注册表 */
    private final ReactiveSseRegistry reactiveSseRegistry;

    /**
     * 订阅实时响应式事件流。
     *
     * <p>URL: GET /api/message/reactive/stream</p>
     * <p>返回 Content-Type: text/event-stream</p>
     *
     * <p>客户端示例（EventSource）：
     * <pre>{@code
     *   const es = new EventSource('/api/message/reactive/stream');
     *   es.addEventListener('notification', e => console.log(e.data));
     * }</pre>
     *
     * @param userId 用户 ID（从 header X-User-Id 获取，可选）
     * @return SSE 事件流（心跳 events + 业务事件）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ReactiveEvent>> streamEvents(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("[ReactiveSSE] 用户 {} 建立响应式 SSE 连接", userId);

        // 心跳流：每 15 秒发送 comment 事件防代理超时
        Flux<ServerSentEvent<ReactiveEvent>> heartbeat = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
                .map(i -> ServerSentEvent.<ReactiveEvent>builder()
                        .comment("heartbeat-" + i)
                        .build());

        // 事件流：从注册表获取的多播事件，过滤当前用户相关事件
        Flux<ServerSentEvent<ReactiveEvent>> events = reactiveSseRegistry.getEventStream()
                .filter(event -> event.getTargetUserId() == null
                        || event.getTargetUserId().equals(userId))
                .map(event -> ServerSentEvent.<ReactiveEvent>builder()
                        .id(event.getEventId())
                        .event(event.getEventType())
                        .data(event)
                        .build());

        // 合并心跳流与事件流，客户端断开时记录日志
        return Flux.merge(heartbeat, events)
                .doOnCancel(() -> log.info("[ReactiveSSE] 用户 {} 断开连接", userId));
    }

    /**
     * 推送事件到响应式流。
     *
     * <p>URL: POST /api/message/reactive/publish</p>
     *
     * <p>请求体为 {@link ReactiveEvent} JSON，示例：
     * <pre>{@code
     *   POST /api/message/reactive/publish
     *   {
     *     "eventType": "notification",
     *     "targetUserId": "user-001",
     *     "title": "系统通知",
     *     "content": "您有一条新消息",
     *     "level": "INFO"
     *   }
     * }</pre>
     *
     * @param event 事件数据（eventId 和 timestamp 将由服务端补充）
     * @return 推送结果
     */
    @PostMapping("/publish")
    public YdszResponse<String> publishEvent(@RequestBody ReactiveEvent event) {
        // 补充必要字段
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            event.setEventType("notification");
        }

        Sinks.EmitResult result = reactiveSseRegistry.publish(event);
        if (result.isSuccess()) {
            return YdszResponse.success("推送成功");
        } else {
            return YdszResponse.error("推送失败: " + result);
        }
    }

    /**
     * 健康检测端点（试点状态查询）。
     *
     * <p>URL: GET /api/message/reactive/health</p>
     *
     * @return 注册表运行状态
     */
    @GetMapping("/health")
    public YdszResponse<Map<String, String>> health() {
        return YdszResponse.success(Map.of(
                "status", "UP",
                "mode", "reactive-pilot",
                "bufferSize", "256"
        ));
    }
}
