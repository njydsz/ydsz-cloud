package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.workflow.service.impl.FlowNotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowNotificationServiceImpl 单元测试
 *
 * <p>覆盖 GAP-V2-03: IN_APP / EMAIL / WEBHOOK 三个通知通道的投递逻辑，
 * 以及 Feign 异常降级行为。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("FlowNotificationServiceImpl 单元测试")
class FlowNotificationServiceImplTest {

    private NotificationClient notificationClient;
    private FlowNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationClient = mock(NotificationClient.class);
        service = new FlowNotificationServiceImpl(notificationClient);
    }

    @Test
    @DisplayName("send IN_APP 通道：通过 NotificationClient 投递站内信")
    void testSendInApp() {
        service.send("IN_APP", 100L, "您有新的审批待办", "请尽快处理", Map.of("bizType", "TEST"));

        verify(notificationClient).send(any());
    }

    @Test
    @DisplayName("send EMAIL 通道：通过 NotificationClient 投递邮件")
    void testSendEmail() {
        service.send("EMAIL", 100L, "审批超时提醒", "您的任务已超时", Map.of("bizType", "TEST"));

        verify(notificationClient).send(any());
    }

    @Test
    @DisplayName("send WEBHOOK 通道：未配置 webhookUrl 时跳过，不抛异常")
    void testSendWebhook() {
        // 未配置 webhookUrl → sendWebhook 内部直接 return，不调用 RestTemplate
        assertThatCode(() -> service.send("WEBHOOK", 100L, "标题", "内容", Map.of("bizType", "TEST")))
                .doesNotThrowAnyException();
        // WEBHOOK 通道不经过 NotificationClient
        verify(notificationClient, never()).send(any());
    }

    @Test
    @DisplayName("send IN_APP 降级：Feign 异常时降级为日志，不抛异常")
    void testSendInAppFallback() {
        when(notificationClient.send(any())).thenThrow(new RuntimeException("Feign timeout"));

        // 异常被内部 try-catch 吞掉，不应传播到调用方
        assertThatCode(() -> service.send("IN_APP", 100L, "标题", "内容", Map.of("bizType", "TEST")))
                .doesNotThrowAnyException();

        // 仍验证 Feign 调用被触发
        verify(notificationClient).send(any());
    }
}
