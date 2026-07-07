package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.ChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * WechatWorkChannel 烟雾测试：验证 errcode=0 返回 ok、URL 解析与失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class WechatWorkChannelTest {

    private static final String WEBHOOK_PREFIX =
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

    private ChannelProperties channelProperties;
    private WechatWorkChannel channel;

    @BeforeEach
    void setUp() {
        channelProperties = new ChannelProperties();
        channelProperties.getChannel().getWechatWork().setDefaultKey("DEFAULT_KEY");
        channel = new WechatWorkChannel(channelProperties);
    }

    @Test
    void channelType_isWecom() {
        assertEquals("WECOM", channel.channelType());
    }

    @Test
    void resolveUrl_defaultKeyWhenReceiverMissing() {
        MessageRequest request = new MessageRequest();

        assertEquals(WEBHOOK_PREFIX + "DEFAULT_KEY", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_receiverKeyAppendedToPrefix() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("RECEIVER_KEY");

        assertEquals(WEBHOOK_PREFIX + "RECEIVER_KEY", channel.resolveUrl(request));
    }

    @Test
    void send_errcodeZeroReturnsSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        channel.restClient = builder.build();
        server.expect(requestTo(WEBHOOK_PREFIX + "KEY"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        MessageRequest request = new MessageRequest();
        request.setReceiver("KEY");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        server.verify();
    }

    @Test
    void send_errcodeNonZeroReturnsFail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        channel.restClient = builder.build();
        server.expect(requestTo(WEBHOOK_PREFIX + "KEY"))
                .andRespond(withSuccess("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}",
                        MediaType.APPLICATION_JSON));

        MessageRequest request = new MessageRequest();
        request.setReceiver("KEY");
        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("93000"));
        server.verify();
    }

    @Test
    void send_returnsFailWhenKeyMissing() {
        channelProperties.getChannel().getWechatWork().setDefaultKey("");
        MessageRequest request = new MessageRequest();

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("key"));
    }
}
