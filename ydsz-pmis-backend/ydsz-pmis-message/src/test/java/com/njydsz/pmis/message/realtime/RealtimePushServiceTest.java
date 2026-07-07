package com.njydsz.pmis.message.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RealtimePushService 单元测试：验证 pushToUser / broadcast / pushToTopic
 * 调用 SimpMessagingTemplate，以及异常降级（不抛出）。
 *
 * <p>P0-4 增强：新增 {@link #pushToUserWithOffline_*} 系列用例，验证在线 / 离线分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class RealtimePushServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private OnlineUserService onlineUserService;
    private OfflineMessageService offlineMessageService;
    private RealtimePushService service;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        onlineUserService = mock(OnlineUserService.class);
        offlineMessageService = mock(OfflineMessageService.class);
        service = new RealtimePushService(messagingTemplate, onlineUserService, offlineMessageService);
    }

    @Test
    void pushToUser_routesToUserDestination() {
        service.pushToUser("u1", "NOTIFICATION", "hello");

        verify(messagingTemplate).convertAndSend(eq("/topic/user/u1/notifications"), eq("hello"));
    }

    @Test
    void broadcast_routesToBroadcastDestination() {
        service.broadcast("world");

        verify(messagingTemplate).convertAndSend(eq("/topic/broadcast"), eq("world"));
    }

    @Test
    void pushToTopic_routesToTopicDestination() {
        service.pushToTopic("dashboard", "data");

        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard"), eq("data"));
    }

    @Test
    void pushToUser_swallowsExceptionAndDoesNotThrow() {
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(eq("/topic/user/u2/notifications"), any(Object.class));

        assertDoesNotThrow(() -> service.pushToUser("u2", "ALERT", "x"));
    }

    @Test
    void broadcast_swallowsExceptionAndDoesNotThrow() {
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(eq("/topic/broadcast"), any(Object.class));

        assertDoesNotThrow(() -> service.broadcast("x"));
    }

    @Test
    void pushToTopic_swallowsExceptionAndDoesNotThrow() {
        doThrow(new RuntimeException("ws down"))
                .when(messagingTemplate).convertAndSend(eq("/topic/dashboard"), any(Object.class));

        assertDoesNotThrow(() -> service.pushToTopic("dashboard", "x"));
    }

    // ========== P0-4: pushToUserWithOffline 在线 / 离线分支 ==========

    @Test
    void pushToUserWithOffline_pushesDirectlyWhenOnline() {
        when(onlineUserService.isOnline("u1")).thenReturn(true);

        service.pushToUserWithOffline("u1", "NOTIFICATION", "hello");

        verify(messagingTemplate).convertAndSend(eq("/topic/user/u1/notifications"), eq("hello"));
        // 不应缓存
        verify(offlineMessageService, org.mockito.Mockito.never())
                .cacheOffline(any(), any(), any());
    }

    @Test
    void pushToUserWithOffline_cachesWhenOffline() {
        when(onlineUserService.isOnline("u2")).thenReturn(false);

        service.pushToUserWithOffline("u2", "NOTIFICATION", "hello");

        verify(offlineMessageService).cacheOffline(eq("u2"), eq("NOTIFICATION"), eq("hello"));
        // 不应直接推送
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void pushToUserWithOffline_fallsBackToDirectPushOnRedisError() {
        when(onlineUserService.isOnline("u3")).thenThrow(new RuntimeException("redis down"));

        service.pushToUserWithOffline("u3", "ALERT", "x");

        // 在线检查异常时降级为直接推送
        verify(messagingTemplate).convertAndSend(eq("/topic/user/u3/notifications"), eq("x"));
    }

    @Test
    void pushToUserWithOffline_skipsWhenUserIdNull() {
        service.pushToUserWithOffline(null, "NOTIFICATION", "x");

        verify(onlineUserService, org.mockito.Mockito.never()).isOnline(any());
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(any(String.class), any(Object.class));
    }
}
