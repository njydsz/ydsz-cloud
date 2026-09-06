package com.njydsz.message.server.reactive;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应式事件数据。
 *
 * <p>作为 WebFlux SSE 推送的统一事件结构，{@link ReactiveSseRegistry} 通过 {@code Sinks.Many<ReactiveEvent>} 管理事件流，
 * {@link com.njydsz.message.web.controller.ReactiveNotificationController} 负责将事件推送到订阅者。
 *
 * <p><b>字段说明：</b>
 *
 * <ul>
 *   <li>{@code eventId} — 事件唯一标识，用于 SSE 的 {@code id} 字段和客户端去重</li>
 *   <li>{@code eventType} — 事件类型（如 notification / heartbeat / system），用于 SSE 的 {@code event} 字段</li>
 *   <li>{@code targetUserId} — 目标用户 ID（null 表示广播）</li>
 *   <li>{@code title} / {@code content} — 事件标题与正文</li>
 *   <li>{@code level} — 事件级别（INFO / WARN / ERROR / CRITICAL）</li>
 *   <li>{@code data} — 扩展数据 Map，携带业务自定义 KV</li>
 *   <li>{@code timestamp} — 事件生成时间戳（UTC 瞬时时间）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReactiveSseRegistry
 * @see com.njydsz.message.web.controller.ReactiveNotificationController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactiveEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件唯一标识 */
    private String eventId;

    /** 事件类型（notification / heartbeat / system 等） */
    private String eventType;

    /**
     * 目标用户 ID。
     *
     * <p>{@code null} 表示广播事件，所有订阅者均可接收。
     */
    private String targetUserId;

    /** 事件标题 */
    private String title;

    /** 事件正文 */
    private String content;

    /** 事件级别（INFO / WARN / ERROR / CRITICAL） */
    private String level;

    /** 扩展业务数据 */
    private Map<String, Object> data;

    /** 事件生成时间戳 */
    private Instant timestamp;
}
