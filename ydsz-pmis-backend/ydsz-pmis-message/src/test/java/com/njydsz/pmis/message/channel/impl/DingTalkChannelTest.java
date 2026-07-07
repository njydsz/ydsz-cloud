package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.ChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DingTalkChannel 单元测试：验证加签逻辑（HMAC-SHA256）与 errcode 判断。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class DingTalkChannelTest {

    private static final String WEBHOOK_PREFIX =
            "https://oapi.dingtalk.com/robot/send?access_token=";

    private ChannelProperties channelProperties;
    private DingTalkChannel channel;

    @BeforeEach
    void setUp() {
        channelProperties = new ChannelProperties();
        channelProperties.getChannel().getDingtalk().setDefaultToken("DEFAULT_TOKEN");
        channel = new DingTalkChannel(channelProperties);
    }

    @Test
    void resolveUrl_receiverTokenAppendedToPrefix() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("RECEIVER_TOKEN");

        assertEquals(WEBHOOK_PREFIX + "RECEIVER_TOKEN", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_receiverHttpUsedAsFullUrl() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("https://custom.example.com/robot/send?access_token=abc");

        assertEquals("https://custom.example.com/robot/send?access_token=abc",
                channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_paramsTokenHasHighestPriority() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("RECEIVER_TOKEN");
        request.setParams(Map.of("dingtalkToken", "PARAM_TOKEN"));

        assertEquals(WEBHOOK_PREFIX + "PARAM_TOKEN", channel.resolveUrl(request));
    }

    @Test
    void resolveUrl_defaultTokenWhenReceiverMissing() {
        MessageRequest request = new MessageRequest();

        assertEquals(WEBHOOK_PREFIX + "DEFAULT_TOKEN", channel.resolveUrl(request));
    }

    @Test
    void appendSign_appendsTimestampAndSignUsingHmacSha256() throws Exception {
        String url = WEBHOOK_PREFIX + "TOKEN";
        String secret = "SECSECRET";

        String signed = channel.appendSign(url, secret);

        assertTrue(signed.startsWith(url));
        assertTrue(signed.contains("&timestamp="));
        assertTrue(signed.contains("&sign="));

        // 重新计算签名并比对，验证 HMAC-SHA256 逻辑
        int tsIdx = signed.indexOf("&timestamp=");
        int signIdx = signed.indexOf("&sign=");
        String timestamp = signed.substring(tsIdx + "&timestamp=".length(), signIdx);
        String signEncoded = signed.substring(signIdx + "&sign=".length());

        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = URLEncoder.encode(
                Base64.getEncoder().encodeToString(
                        mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8))),
                StandardCharsets.UTF_8);
        assertEquals(expected, signEncoded);
    }

    @Test
    void send_errcodeZeroReturnsSuccess() {
        MockRestServiceServer server = bindMockServer();
        server.expect(requestTo(WEBHOOK_PREFIX + "TOKEN"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        MessageRequest request = new MessageRequest();
        request.setReceiver("TOKEN");
        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        server.verify();
    }

    @Test
    void send_errcodeNonZeroReturnsFail() {
        MockRestServiceServer server = bindMockServer();
        server.expect(requestTo(WEBHOOK_PREFIX + "TOKEN"))
                .andRespond(withSuccess("{\"errcode\":310000,\"errmsg\":\"ip not in whitelist\"}",
                        MediaType.APPLICATION_JSON));

        MessageRequest request = new MessageRequest();
        request.setReceiver("TOKEN");
        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("310000"));
        server.verify();
    }

    @Test
    void send_returnsFailWhenTokenMissing() {
        channelProperties.getChannel().getDingtalk().setDefaultToken("");
        MessageRequest request = new MessageRequest();

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("access_token"));
    }

    @Test
    void buildPayload_textByDefault() {
        MessageRequest request = new MessageRequest();
        request.setContent("hello");
        request.setSubject("title");

        Map<String, Object> payload = channel.buildPayload(request);

        assertEquals("text", payload.get("msgtype"));
        assertEquals("hello", ((Map<?, ?>) payload.get("text")).get("content"));
    }

    @Test
    void buildPayload_markdownWhenMsgTypeMarkdown() {
        MessageRequest request = new MessageRequest();
        request.setContent("hello");
        request.setSubject("title");
        request.setParams(Map.of("msgType", "markdown"));

        Map<String, Object> payload = channel.buildPayload(request);

        assertEquals("markdown", payload.get("msgtype"));
        assertEquals("title", ((Map<?, ?>) payload.get("markdown")).get("title"));
    }

    /**
     * 将 channel 的 restClient 绑定到 MockRestServiceServer，返回 server。
     */
    private MockRestServiceServer bindMockServer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        channel.restClient = builder.build();
        return server;
    }
}
