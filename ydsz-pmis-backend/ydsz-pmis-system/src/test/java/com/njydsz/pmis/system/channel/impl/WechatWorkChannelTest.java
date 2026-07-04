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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WechatWorkChannel 单元测试
 *
 * <p>覆盖企业微信群机器人通道核心分支：
 * <ul>
 *   <li>channelType 返回 WECHAT_WORK</li>
 *   <li>receiver 为完整 URL 且 errcode=0 → 成功</li>
 *   <li>receiver 为 key → 拼接默认 URL 前缀</li>
 *   <li>params.wechatWorkKey 优先级最高</li>
 *   <li>URL 缺失 → 失败，不调用 RestTemplate</li>
 *   <li>errcode != 0 → 失败，含 errmsg</li>
 *   <li>markdown 消息类型正常发送</li>
 *   <li>HTTP 异常 → 失败</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class WechatWorkChannelTest {

    @Mock
    private RestTemplate restTemplate;

    private WechatWorkChannel wechatWorkChannel;

    @BeforeEach
    void setUp() {
        wechatWorkChannel = new WechatWorkChannel(restTemplate);
    }

    @Test
    @DisplayName("channelType 返回 WECHAT_WORK")
    void channelType_shouldReturnWechatWork() {
        assertThat(wechatWorkChannel.channelType()).isEqualTo("WECHAT_WORK");
    }

    @Test
    @DisplayName("receiver 为完整 URL 且 errcode=0 → 发送成功")
    void send_shouldReturnSuccess_whenReceiverIsUrlAndErrcodeZero() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx";
        MessageRequest req = newRequest(url, "CPU 预警", "CPU 使用率超过 90%");

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0,\"errmsg\":\"ok\"}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getChannel()).isEqualTo("WECHAT_WORK");
        assertThat(result.getProviderTraceId()).startsWith("WECHAT_WORK-");
    }

    @Test
    @DisplayName("receiver 为 key → 拼接默认 URL 前缀")
    void send_shouldConcatPrefix_whenReceiverIsKey() {
        String key = "abc123";
        MessageRequest req = newRequest(key, "标题", "内容");

        when(restTemplate.postForEntity(contains("key=" + key), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("params.wechatWorkKey 优先级高于 receiver")
    void send_shouldUseParamsKey_whenPresent() {
        String key = "param-key";
        Map<String, Object> params = new HashMap<>();
        params.put("wechatWorkKey", key);
        MessageRequest req = newRequest("receiver-key", "标题", "内容");
        req.setParams(params);

        when(restTemplate.postForEntity(contains("key=" + key), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("URL 缺失（receiver 空 + 无默认配置）→ 失败，不调用 RestTemplate")
    void send_shouldReturnFail_whenUrlMissing() {
        MessageRequest req = newRequest(null, "标题", "内容");

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("key");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("errcode != 0 → 失败，含 errmsg")
    void send_shouldReturnFail_whenErrcodeNonZero() {
        MessageRequest req = newRequest("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx", "标题", "内容");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":93000,\"errmsg\":\"invalid webhook url\"}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("93000");
        assertThat(result.getErrorMessage()).contains("invalid");
    }

    @Test
    @DisplayName("params.msgType=markdown → 发送 markdown 消息")
    void send_shouldSendMarkdown_whenMsgTypeIsMarkdown() {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx";
        Map<String, Object> params = new HashMap<>();
        params.put("msgType", "markdown");
        MessageRequest req = newRequest(url, "预警标题", "## 预警详情\nCPU 90%");
        req.setParams(params);

        when(restTemplate.postForEntity(eq(url), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("HTTP 调用抛异常 → 失败，错误信息含异常原因")
    void send_shouldReturnFail_whenHttpThrows() {
        MessageRequest req = newRequest("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx", "标题", "内容");

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Connection refused");
    }

    @Test
    @DisplayName("receiver 为空时回退到默认配置 default-key")
    void send_shouldFallbackToDefaultKey_whenReceiverEmpty() {
        ReflectionTestUtils.setField(wechatWorkChannel, "defaultKey", "default-key-xxx");
        MessageRequest req = newRequest(null, "标题", "内容");

        when(restTemplate.postForEntity(contains("key=default-key-xxx"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"errcode\":0}", HttpStatus.OK));

        MessageResult result = wechatWorkChannel.send(req);

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * 构造一个最小化的消息请求。
     *
     * @param receiver 接收人 / key / Webhook URL
     * @param subject  消息标题
     * @param content  消息内容
     * @return 消息请求
     */
    private MessageRequest newRequest(String receiver, String subject, String content) {
        MessageRequest req = new MessageRequest();
        req.setChannel("WECHAT_WORK");
        req.setReceiver(receiver);
        req.setSubject(subject);
        req.setContent(content);
        return req;
    }
}
