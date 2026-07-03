package com.njydsz.pmis.system.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WebhookChannel 单元测试
 *
 * <p>覆盖 Webhook 通道核心分支：
 * <ul>
 *   <li>channelType 返回 WEBHOOK</li>
 *   <li>正常发送（receiver 为 URL，2xx 响应）→ 成功，含追踪 ID</li>
 *   <li>URL 缺失（无 params / receiver 非 http / 无默认配置）→ 失败，不调用 RestTemplate</li>
 *   <li>HTTP 异常（连接拒绝等）→ 失败，错误信息含异常原因</li>
 *   <li>非 2xx 响应 → 失败，错误信息含状态码</li>
 *   <li>params.webhookUrl 优先级高于 receiver</li>
 *   <li>receiver 非 URL 时回退到默认配置 default-url</li>
 * </ul>
 *
 * <p>WebhookChannel 通过构造器注入 RestTemplate（可选），便于直接 mock，
 * 规避被测类内部 {@code new RestTemplate()} 不可 mock 的问题。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class WebhookChannelTest {

    @Mock
    private RestTemplate restTemplate;

    private WebhookChannel webhookChannel;

    @BeforeEach
    void setUp() {
        // 注入 mock RestTemplate，@Value 字段保持默认（defaultUrl=null），由各用例按需注入
        webhookChannel = new WebhookChannel(restTemplate);
    }

    @Test
    @DisplayName("channelType 返回 WEBHOOK")
    void channelType_shouldReturnWebhook() {
        assertThat(webhookChannel.channelType()).isEqualTo("WEBHOOK");
    }

    @Test
    @DisplayName("receiver 为 http URL 且响应 2xx → 发送成功，含追踪 ID")
    void send_shouldReturnSuccess_whenReceiverIsUrlAndResponse2xx() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx";
        MessageRequest req = newMessageRequest(url, "CPU 预警", "CPU 使用率超过 90%");

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0}", HttpStatus.OK));

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("WEBHOOK");
        assertThat(result.getProviderTraceId()).startsWith("WEBHOOK-");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("URL 缺失（receiver 非 http 且无默认配置）→ 失败，不调用 RestTemplate")
    void send_shouldReturnFail_whenUrlMissing() {
        MessageRequest req = newMessageRequest("ops-team", "标题", "内容");

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Webhook URL");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("HTTP 调用抛异常 → 失败，错误信息含异常原因")
    void send_shouldReturnFail_whenHttpThrows() {
        MessageRequest req = newMessageRequest("https://example.com/hook", "标题", "内容");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Connection refused");
    }

    @Test
    @DisplayName("非 2xx 响应 → 失败，错误信息含 HTTP 状态码")
    void send_shouldReturnFail_whenResponseNon2xx() {
        MessageRequest req = newMessageRequest("https://example.com/hook", "标题", "内容");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("bad request", HttpStatus.BAD_REQUEST));

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("400");
    }

    @Test
    @DisplayName("params.webhookUrl 优先级高于 receiver，使用 params 指定的 URL")
    void send_shouldUseParamsWebhookUrl_whenPresent() {
        String paramUrl = "https://param.example.com/hook";
        Map<String, Object> params = new HashMap<>();
        params.put("webhookUrl", paramUrl);
        MessageRequest req = newMessageRequest("https://receiver.example.com/hook", "标题", "内容");
        req.setParams(params);

        when(restTemplate.postForEntity(eq(paramUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("receiver 非 URL 时回退到默认配置 default-url")
    void send_shouldFallbackToDefaultUrl_whenReceiverNotUrl() {
        String defaultUrl = "https://default.example.com/hook";
        ReflectionTestUtils.setField(webhookChannel, "defaultUrl", defaultUrl);
        MessageRequest req = newMessageRequest("team-a", "标题", "内容");

        when(restTemplate.postForEntity(eq(defaultUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        MessageResult result = webhookChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * 构造一个最小化的消息请求。
     *
     * @param receiver 接收人 / Webhook URL
     * @param subject  消息标题
     * @param content  消息内容
     * @return 消息请求
     */
    private MessageRequest newMessageRequest(String receiver, String subject, String content) {
        MessageRequest req = new MessageRequest();
        req.setChannel("WEBHOOK");
        req.setReceiver(receiver);
        req.setSubject(subject);
        req.setContent(content);
        return req;
    }
}
