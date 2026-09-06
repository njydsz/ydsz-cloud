package com.njydsz.message.server.reactive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

/**
 * 响应式事件发布器。
 *
 * <p>为 Service 层（如 {@code NotificationService}）提供向响应式 SSE 流推送事件的便捷方法，
 * 通过 {@link ReactiveSseRegistry} 间接发布，避免与 Web 层形成循环依赖。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>站内通知发送后，通过响应式流推送给在线用户（与 WebSocket 通道并列）</li>
 *   <li>系统级事件广播（维护公告、告警推送）</li>
 *   <li>工作流节点变更实时通知</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReactiveSseRegistry
 * @see ReactiveEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveEventPublisher {

    /** 响应式事件注册表 */
    private final ReactiveSseRegistry reactiveSseRegistry;

    /**
     * 将站内通知 VO 推送到响应式流。
     *
     * <p>自动构建 {@link ReactiveEvent}，将通知的 title/content/receiverId 映射到事件结构。
     * 如果推送失败（如缓冲区满），仅记录警告日志，不影响主业务逻辑。
     *
     * @param notification 站内通知 VO（需含 title、content、receiverId）
     * @return 推送结果（success 表示成功）
     */
    public YdszResponse<String> publishNotification(MsgNotificationVO notification) {
        ReactiveEvent event = ReactiveEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("notification")
                .targetUserId(notification.getReceiverId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .level(notification.getLevel())
                .timestamp(Instant.now())
                .build();

        Sinks.EmitResult result = reactiveSseRegistry.publish(event);
        if (result.isSuccess()) {
            log.debug("[ReactiveSSE] 通知已发布到响应式流: eventId={} userId={}",
                    event.getEventId(), notification.getReceiverId());
            return YdszResponse.success("推送成功");
        } else {
            log.warn("[ReactiveSSE] 通知推送失败: eventId={} result={}", event.getEventId(), result);
            return YdszResponse.error("推送失败: " + result);
        }
    }

    /**
     * 推送自定义业务事件到响应式流。
     *
     * @param eventType 事件类型标识
     * @param targetUserId 目标用户 ID（null 表示广播）
     * @param title 事件标题
     * @param content 事件正文
     * @param data 扩展业务数据
     * @return 推送结果
     */
    public YdszResponse<String> publishCustomEvent(
            String eventType,
            String targetUserId,
            String title,
            String content,
            Map<String, Object> data) {

        ReactiveEvent event = ReactiveEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .targetUserId(targetUserId)
                .title(title)
                .content(content)
                .data(data)
                .timestamp(Instant.now())
                .build();

        Sinks.EmitResult result = reactiveSseRegistry.publish(event);
        return result.isSuccess()
                ? YdszResponse.success("推送成功")
                : YdszResponse.error("推送失败: " + result);
    }

    /**
     * 广播系统级事件给所有在线订阅者。
     *
     * @param title 事件标题
     * @param content 事件正文
     * @param level 事件级别
     * @return 推送结果
     */
    public YdszResponse<String> broadcastSystemEvent(String title, String content, String level) {
        ReactiveEvent event = ReactiveEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("system")
                .targetUserId(null)
                .title(title)
                .content(content)
                .level(level)
                .timestamp(Instant.now())
                .build();

        Sinks.EmitResult result = reactiveSseRegistry.publish(event);
        if (result.isSuccess()) {
            log.info("[ReactiveSSE] 系统事件已广播: eventId={} title={}", event.getEventId(), title);
            return YdszResponse.success("广播成功");
        } else {
            return YdszResponse.error("广播失败: " + result);
        }
    }
}
