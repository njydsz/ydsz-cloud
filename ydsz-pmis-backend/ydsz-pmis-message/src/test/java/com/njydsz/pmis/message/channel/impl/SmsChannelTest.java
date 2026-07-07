package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.channel.sms.MockSmsProvider;
import com.njydsz.pmis.message.channel.sms.SmsProvider;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SmsChannel 门面单元测试：验证 provider 选择、降级、空接收人、模板解析。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class SmsChannelTest {

    private MessageProperties properties;
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        properties = new MessageProperties();
        templateService = mock(TemplateService.class);
    }

    @Test
    void channelType_isSms() {
        SmsChannel channel = new SmsChannel(List.of(new MockSmsProvider()), properties, templateService);
        assertEquals("SMS", channel.channelType());
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        SmsChannel channel = new SmsChannel(List.of(new MockSmsProvider()), properties, templateService);
        MessageRequest request = new MessageRequest();
        request.setReceiver("");
        MessageResult result = channel.send(request);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("手机号"));
    }

    @Test
    void send_usesMockProviderWhenConfigMock() {
        properties.getSms().setProvider("mock");
        SmsProvider mockProvider = mock(SmsProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("SMS", "MOCK-1"));
        SmsChannel channel = new SmsChannel(List.of(mockProvider), properties, templateService);

        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        verify(mockProvider).send(any(), any());
    }

    @Test
    void send_fallsBackToMockWhenAliyunConfiguredButNotRegistered() {
        properties.getSms().setProvider("aliyun");
        SmsProvider mockProvider = mock(SmsProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("SMS", "MOCK-FALLBACK"));
        SmsChannel channel = new SmsChannel(List.of(mockProvider), properties, templateService);

        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("MOCK-FALLBACK", result.getProviderTraceId());
        verify(mockProvider).send(any(), any());
    }

    @Test
    void send_throwsWhenNoProviderAvailable() {
        properties.getSms().setProvider("aliyun");
        SmsChannel channel = new SmsChannel(List.of(), properties, templateService);

        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        assertThrows(IllegalStateException.class, () -> channel.send(request));
    }

    @Test
    void send_resolvesTemplateFromChannelMeta() {
        properties.getSms().setProvider("mock");
        SmsProvider mockProvider = mock(SmsProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("SMS", "MOCK-META"));
        SmsChannel channel = new SmsChannel(List.of(mockProvider), properties, templateService);

        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        request.setTemplateCode("SMS_VERIFY");
        java.util.Map<String, String> meta = new java.util.HashMap<>();
        meta.put("signName", "元数据签名");
        meta.put("providerKey", "SMS_META_123");
        request.setChannelMeta(meta);

        channel.send(request);

        // 验证传给 provider 的 template 含 channelMeta 的值
        org.mockito.ArgumentCaptor<MsgTemplateDO> captor =
                org.mockito.ArgumentCaptor.forClass(MsgTemplateDO.class);
        verify(mockProvider).send(any(), captor.capture());
        assertEquals("元数据签名", captor.getValue().getSignName());
        assertEquals("SMS_META_123", captor.getValue().getProviderKey());
    }

    @Test
    void send_fallsBackToTemplateServiceWhenNoMeta() {
        properties.getSms().setProvider("mock");
        SmsProvider mockProvider = mock(SmsProvider.class);
        when(mockProvider.providerType()).thenReturn("mock");
        when(mockProvider.send(any(), any())).thenReturn(MessageResult.ok("SMS", "MOCK-TS"));
        MsgTemplateDO dbTemplate = new MsgTemplateDO();
        dbTemplate.setSignName("DB签名");
        dbTemplate.setProviderKey("SMS_DB_456");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), any()))
                .thenReturn(dbTemplate);
        SmsChannel channel = new SmsChannel(List.of(mockProvider), properties, templateService);

        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        request.setTemplateCode("SMS_VERIFY");

        channel.send(request);

        org.mockito.ArgumentCaptor<MsgTemplateDO> captor =
                org.mockito.ArgumentCaptor.forClass(MsgTemplateDO.class);
        verify(mockProvider).send(any(), captor.capture());
        assertEquals("DB签名", captor.getValue().getSignName());
        assertEquals("SMS_DB_456", captor.getValue().getProviderKey());
    }
}
