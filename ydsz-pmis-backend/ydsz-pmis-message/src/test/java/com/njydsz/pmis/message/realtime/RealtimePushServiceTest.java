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

/**
 * RealtimePushService 单元测试：验证 pushToUser / broadcast / pushToTopic
 * 调用 SimpMessagingTemplate，以及异常降级（不抛出）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class RealtimePushServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private RealtimePushService service;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        service = new RealtimePushService(messagingTemplate);
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
}
