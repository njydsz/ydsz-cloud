package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.config.ChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WebhookChannel 单元测试：验证 URL 解析优先级
 * （params.webhookUrl &gt; receiver(http) &gt; default-url）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class WebhookChannelTest {

    private ChannelProperties channelProperties;
    private WebhookChannel channel;

    @BeforeEach
    void setUp() {
        channelProperties = new ChannelProperties();
        channelProperties.getWebhook().setDefaultUrl("https://default.example.com/hook");
        channel = new WebhookChannel(channelProperties);
    }

    @Test
    void resolveUrl_paramsWebhookUrlHasHighestPriority() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("https://receiver.example.com");
        Map<String, Object> params = new HashMap<>();
        params.put("webhookUrl", "https://params.example.com/hook");
        request.setParams(params);

        assertEquals("https://params.example.com/hook", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_receiverHttpUsedWhenNoParams() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("https://receiver.example.com/hook");

        assertEquals("https://receiver.example.com/hook", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_defaultUrlUsedWhenReceiverNotHttp() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("someone@example.com");

        assertEquals("https://default.example.com/hook", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_returnsNullWhenNothingConfigured() {
        channelProperties.getWebhook().setDefaultUrl("");
        MessageRequest request = new MessageRequest();

        assertNull(channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_paramsBlankFallsBackToReceiver() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("https://receiver.example.com/hook");
        Map<String, Object> params = new HashMap<>();
        params.put("webhookUrl", "  ");
        request.setParams(params);

        assertEquals("https://receiver.example.com/hook", channel.resolveUrl(request));
    }
}
