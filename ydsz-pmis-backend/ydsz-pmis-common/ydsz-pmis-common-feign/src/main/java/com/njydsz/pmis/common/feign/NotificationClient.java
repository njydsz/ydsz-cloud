package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Unified Notification Feign Client.
 *
 * <p>All modules communicate with {@code ydsz-pmis-message} through this interface
 * for notification sending, realtime push, and broadcast.
 * {@link NotificationClientFallback} ensures caller's main flow is not affected
 * when the message module is unavailable.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@link #send} - Send notification (in-app / email / SMS), persists to notification table</li>
 *   <li>{@link #pushRealtime} - Push realtime WebSocket message to a specific user</li>
 *   <li>{@link #broadcast} - Broadcast message to all online users (alerts / announcements)</li>
 * </ul>
 *
 * <p>P0-2 refactor: Merged the former {@code NotificationPushClient}'s pushToUser / broadcast
 * capabilities into this single client, eliminating redundant Feign clients targeting the same service.
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@FeignClient(name = FeignClientConstants.MESSAGE, fallbackFactory = NotificationClientFallback.class)
public interface NotificationClient {

    /**
     * Send notification (single or batch).
     *
     * @param payload notification parameters (aligned with NotificationSendDTO)
     * @return actual persisted record count
     */
    @PostMapping("/notifications/send")
    BaseResponse<Integer> send(@RequestBody NotificationFeignDTO payload);

    /**
     * Push realtime message to a specific user (WebSocket).
     *
     * <p>The workflow engine uses this to push realtime messages to assignees
     * on task creation/completion, including todo count updates and notifications.
     * Frontend subscribes to /user/{userId}/queue/notifications to receive.
     *
     * @param userId  target user ID
     * @param type    message type (TASK_ASSIGNED/TASK_COMPLETED/TODO_COUNT/NOTIFICATION)
     * @param payload message content
     * @return push result
     */
    @PostMapping("/notifications/push")
    BaseResponse<Map<String, Object>> pushRealtime(@RequestParam("userId") String userId,
                                                @RequestParam("type") String type,
                                                 @RequestBody RealtimePushDTO payload);

    /**
     * Broadcast message to all online users.
     *
     * <p>Suitable for alerts / announcements targeting all users.
     * Push failures are handled by FallbackFactory, does not affect caller's main flow.
     *
     * @param type    message type (NOTIFICATION/ALERT/DASHBOARD)
     * @param payload message content
     * @return push result
     */
    @PostMapping("/notifications/broadcast")
    BaseResponse<Map<String, Object>> broadcast(@RequestParam("type") String type,
                                                 @RequestBody RealtimePushDTO payload);
}
