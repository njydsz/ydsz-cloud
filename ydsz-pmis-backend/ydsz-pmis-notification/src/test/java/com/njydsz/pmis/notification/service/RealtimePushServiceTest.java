package com.njydsz.pmis.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * RealtimePushService 单元测试。
 *
 * <p>验证推送目标地址正确，且消息代理异常时降级不抛出（不影响主业务）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("RealtimePushService 实时推送测试")
@ExtendWith(MockitoExtension.class)
class RealtimePushServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RealtimePushService realtimePushService;

    @Test
    @DisplayName("pushToUser 应发送到用户私有队列")
    void pushToUser_shouldSendToUserQueue() {
        realtimePushService.pushToUser(1L, "NOTIFICATION", "test-payload");
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/notifications"), any());
    }

    @Test
    @DisplayName("pushToUser 消息代理异常时应降级不抛出")
    void pushToUser_shouldNotThrowWhenMessagingFails() {
        doThrow(new RuntimeException("connection closed"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
        // 不抛出异常即视为通过
        realtimePushService.pushToUser(1L, "NOTIFICATION", "test");
    }

    @Test
    @DisplayName("broadcast 应发送到广播主题")
    void broadcast_shouldSendToTopic() {
        realtimePushService.broadcast("ALERT", "alert-data");
        verify(messagingTemplate).convertAndSend(eq("/topic/broadcast"), any());
    }

    @Test
    @DisplayName("pushToTopic 应发送到指定主题")
    void pushToTopic_shouldSendToSpecifiedTopic() {
        realtimePushService.pushToTopic("dashboard-refresh", "data");
        verify(messagingTemplate).convertAndSend(eq("/topic/dashboard-refresh"), any());
    }

    @Test
    @DisplayName("broadcast 消息代理异常时应降级不抛出")
    void broadcast_shouldNotThrowWhenMessagingFails() {
        doThrow(new RuntimeException("broker unavailable"))
                .when(messagingTemplate).convertAndSend(anyString(), any());
        // 不抛出异常即视为通过
        realtimePushService.broadcast("ALERT", "test");
    }
}
