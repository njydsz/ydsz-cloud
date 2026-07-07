package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.channel.push.MockPushProvider;
import com.njydsz.pmis.message.channel.push.PushProvider;
import com.njydsz.pmis.message.config.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PushChannel 门面单元测试：验证 provider 选择、降级、空接收人、异常分支。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class PushChannelTest {

    private MessageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MessageProperties();
    }

    @Test
    void channelType_isPush() {
        PushChannel channel = new PushChannel(List.of(new MockPushProvider()), properties);
        assertEquals("PUSH", channel.channelType());
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        PushChannel channel = new PushChannel(List.of(new MockPushProvider()), properties);
        MessageRequest request = new MessageRequest();
        request.setReceiver("");
        MessageResult result = channel.send(request);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("推送目标"));
    }

    @Test
    void send_usesMockProviderWhenConfigMock() {
        properties.getPush().setProvider("mock");
        PushProvider mockProvider = mock(PushProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("PUSH", "MOCK-1"));
        PushChannel channel = new PushChannel(List.of(mockProvider), properties);

        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        verify(mockProvider).send(any(), any());
    }

    @Test
    void send_fallsBackToMockWhenGetuiConfiguredButNotRegistered() {
        properties.getPush().setProvider("getui");
        PushProvider mockProvider = mock(PushProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("PUSH", "MOCK-FALLBACK"));
        PushChannel channel = new PushChannel(List.of(mockProvider), properties);

        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("MOCK-FALLBACK", result.getProviderTraceId());
        verify(mockProvider).send(any(), any());
    }

    @Test
    void send_selectsGetuiWhenConfigGetuiAndRegistered() {
        properties.getPush().setProvider("getui");
        PushProvider getuiProvider = mock(PushProvider.class);
        when(getuiProvider.providerType()).thenReturn("getui");
        when(getuiProvider.send(any(), any())).thenReturn(MessageResult.ok("PUSH", "GETUI-1"));
        PushProvider mockProvider = mock(PushProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        PushChannel channel = new PushChannel(List.of(getuiProvider, mockProvider), properties);

        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("GETUI-1", result.getProviderTraceId());
        verify(getuiProvider).send(any(), any());
    }

    @Test
    void send_throwsWhenNoProviderAvailable() {
        properties.getPush().setProvider("getui");
        PushChannel channel = new PushChannel(List.of(), properties);

        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        assertThrows(IllegalStateException.class, () -> channel.send(request));
    }

    @Test
    void send_defaultsToMockWhenProviderConfigBlank() {
        // provider 配置为空时，默认走 mock
        properties.getPush().setProvider("");
        PushProvider mockProvider = mock(PushProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("PUSH", "MOCK-DEFAULT"));
        PushChannel channel = new PushChannel(List.of(mockProvider), properties);

        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("MOCK-DEFAULT", result.getProviderTraceId());
    }
}
