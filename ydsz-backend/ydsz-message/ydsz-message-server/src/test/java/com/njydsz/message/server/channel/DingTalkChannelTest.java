package com.njydsz.message.server.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.server.config.ChannelProperties;

/**
 * DingTalkChannel 钉钉通道单元测试。
 *
 * <p>P1-1: 验证 appendSign 失败快速失败 + resolveUrl 优先级。
 *
 * <p>P3-1: 从 channel.impl 包对齐到 channel 包（与被测类同包）。
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("DingTalkChannel 钉钉通道测试")
@ExtendWith(MockitoExtension.class)
class DingTalkChannelTest {

    @Mock
    private ChannelProperties channelProperties;

    @InjectMocks
    private DingTalkChannel channel;

    @BeforeEach
    void setUp() {
        ChannelProperties.ChannelGroup ch = new ChannelProperties.ChannelGroup();
        ChannelProperties.DingTalkConfig cfg = new ChannelProperties.DingTalkConfig();
        ch.setDingtalk(cfg);
        when(channelProperties.getChannel()).thenReturn(ch);
        channel.init();
    }

    @Nested
    @DisplayName("appendSign() 加签")
    class AppendSignTest {

        @Test
        @DisplayName("正常加签: 返回带 timestamp/sign 的 URL")
        void shouldAppendTimestampAndSign() {
            String url = "https://oapi.dingtalk.com/robot/send?access_token=abc123";
            String secret = "SECtest123";

            String signed = channel.appendSign(url, secret);

            assertThat(signed).isNotNull();
            assertThat(signed).contains("timestamp=");
            assertThat(signed).contains("sign=");
            assertThat(signed).startsWith(url + "&");
        }

        @Test
        @DisplayName("空 secret 加签: 仍返回带签名的 URL(空密钥不抛异常)")
        void shouldHandleEmptySecret() {
            String url = "https://oapi.dingtalk.com/robot/send?access_token=abc123";

            String signed = channel.appendSign(url, "");

            assertThat(signed).isNotNull();
            assertThat(signed).contains("timestamp=");
        }
    }

    @Nested
    @DisplayName("resolveUrl() URL 解析优先级")
    class ResolveUrlTest {

        @Test
        @DisplayName("params.dingtalkToken 优先级最高")
        void paramsTokenHighestPriority() {
            MessageRequest req = new MessageRequest();
            Map<String, Object> params = new HashMap<>();
            params.put("dingtalkToken", "explicit-token");
            req.setParams(params);

            String url = channel.resolveUrl(req);

            assertThat(url).isEqualTo(
                    "https://oapi.dingtalk.com/robot/send?access_token=explicit-token");
        }

        @Test
        @DisplayName("receiver 为 http 开头时视为完整 URL")
        void receiverAsFullUrl() {
            MessageRequest req = new MessageRequest();
            req.setReceiver("https://custom.webhook.com/send");

            String url = channel.resolveUrl(req);

            assertThat(url).isEqualTo("https://custom.webhook.com/send");
        }

        @Test
        @DisplayName("receiver 为 token 时拼接默认前缀")
        void receiverAsToken() {
            MessageRequest req = new MessageRequest();
            req.setReceiver("my-access-token");

            String url = channel.resolveUrl(req);

            assertThat(url).isEqualTo(
                    "https://oapi.dingtalk.com/robot/send?access_token=my-access-token");
        }

        @Test
        @DisplayName("无 receiver 时回退 default-token 配置")
        void fallbackToDefaultToken() {
            when(channelProperties.getChannel().getDingtalk().getDefaultToken())
                    .thenReturn("default-tkn");
            MessageRequest req = new MessageRequest();

            String url = channel.resolveUrl(req);

            assertThat(url).isEqualTo(
                    "https://oapi.dingtalk.com/robot/send?access_token=default-tkn");
        }

        @Test
        @DisplayName("无任何配置时返回 null")
        void returnsNullWhenNoConfig() {
            MessageRequest req = new MessageRequest();

            String url = channel.resolveUrl(req);

            assertThat(url).isNull();
        }
    }

    @Nested
    @DisplayName("buildPayload() 请求体构造")
    class BuildPayloadTest {

        @Test
        @DisplayName("默认 text 类型")
        void defaultTextType() {
            MessageRequest req = new MessageRequest();
            req.setContent("hello");

            Map<String, Object> payload = channel.buildPayload(req);

            assertThat(payload.get("msgtype")).isEqualTo("text");
            assertThat(payload.get("text")).isNotNull();
        }

        @Test
        @DisplayName("markdown 类型")
        void markdownType() {
            MessageRequest req = new MessageRequest();
            req.setContent("# 标题");
            req.setSubject("主题");
            Map<String, Object> params = new HashMap<>();
            params.put("msgType", "markdown");
            req.setParams(params);

            Map<String, Object> payload = channel.buildPayload(req);

            assertThat(payload.get("msgtype")).isEqualTo("markdown");
            assertThat(payload.get("markdown")).isNotNull();
        }
    }
}
