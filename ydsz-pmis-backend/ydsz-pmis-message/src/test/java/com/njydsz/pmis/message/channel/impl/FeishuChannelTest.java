package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.ChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * FeishuChannel 烟雾测试：验证 code=0 返回 ok、加签字段、URL 解析与失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class FeishuChannelTest {

    private static final String WEBHOOK_PREFIX =
            "https://open.feishu.cn/open-apis/bot/v2/hook/";

    private ChannelProperties channelProperties;
    private FeishuChannel channel;

    @BeforeEach
    void setUp() {
        channelProperties = new ChannelProperties();
        channelProperties.getChannel().getFeishu().setDefaultHook("DEFAULT_HOOK");
        channel = new FeishuChannel(channelProperties);
    }

    @Test
    void channelType_isFeishu() {
        assertEquals("FEISHU", channel.channelType());
    }

    @Test
    void resolveUrl_hookIdAppendedToPrefix() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("HOOK_ID");

        assertEquals(WEBHOOK_PREFIX + "HOOK_ID", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_receiverHttpUsedAsFullUrl() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("https://custom.example.com/bot/v2/hook/abc");

        assertEquals("https://custom.example.com/bot/v2/hook/abc",
                channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_defaultHookWhenReceiverMissing() {
        MessageRequest request = new MessageRequest();

        assertEquals(WEBHOOK_PREFIX + "DEFAULT_HOOK", channel.resolveUrl(request));
    }

    @Test
    void send_codeZeroReturnsSuccess() {
        bindMockServer(WEBHOOK_PREFIX + "HOOK", "{\"code\":0,\"msg\":\"success\"}");

        MessageRequest request = new MessageRequest();
        request.setReceiver("HOOK");
        request.setContent("hello");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
    }

    @Test
    void send_codeNonZeroReturnsFail() {
        bindMockServer(WEBHOOK_PREFIX + "HOOK", "{\"code\":19021,\"msg\":\"sign match fail\"}");

        MessageRequest request = new MessageRequest();
        request.setReceiver("HOOK");
        request.setContent("hello");
        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("19021"));
    }

    @Test
    void send_returnsFailWhenHookMissing() {
        channelProperties.getChannel().getFeishu().setDefaultHook("");
        MessageRequest request = new MessageRequest();

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("hook"));
    }

    @Test
    void buildPayload_includesSignWhenSecretConfigured() {
        channelProperties.getChannel().getFeishu().setSecret("FEISHU_SECRET");

        MessageRequest request = new MessageRequest();
        request.setContent("hello");
        request.setSubject("title");

        Map<String, Object> payload = channel.buildPayload(request);

        assertNotNull(payload.get("timestamp"));
        assertNotNull(payload.get("sign"));
    }

    @Test
    void appendSign_producesTimestampAndSign() {
        channelProperties.getChannel().getFeishu().setSecret("FEISHU_SECRET");

        Map<String, String> sign = channel.appendSign("FEISHU_SECRET");

        assertNotNull(sign.get("timestamp"));
        assertNotNull(sign.get("sign"));
        assertFalse(sign.get("sign").isBlank());
    }

    /**
     * 绑定 MockRestServiceServer 到 channel.restClient 并设置成功响应。
     */
    private void bindMockServer(String expectedUrl, String responseBody) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        channel.restClient = builder.build();
        server.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }
}
