package com.njydsz.pmis.message.channel.push;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GetuiPushProvider 单元测试：mock RestTemplate 验证鉴权、推送、降级、异常分支。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class GetuiPushProviderTest {

    private MessageProperties.GetuiPushConfig config;
    private RestTemplate restTemplate;
    private GetuiPushProvider provider;

    @BeforeEach
    void setUp() {
        config = new MessageProperties.GetuiPushConfig();
        config.setAppId("test-app-id");
        config.setAppKey("test-app-key");
        config.setMasterSecret("test-master-secret");
        config.setBaseUrl("https://restapi.getui.com");
        restTemplate = mock(RestTemplate.class);
        provider = new GetuiPushProvider(config, restTemplate);
    }

    @Test
    void providerType_isGetui() {
        assertEquals("getui", provider.providerType());
    }

    @Test
    void send_returnsFailWhenClientIdBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("  ");
        MessageResult result = provider.send(request, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("clientId"));
    }

    @Test
    void send_returnsFailWhenCredentialMissing() {
        config.setAppKey("");
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        MessageResult result = provider.send(request, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("凭证"));
    }

    @Test
    void send_returnsOkWhenGetuiReturnsSuccess() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setSubject("通知标题");
        request.setContent("推送正文");
        // 鉴权响应
        String authResp = "{\"code\":\"10000\",\"data\":{\"token\":\"auth-token-001\"}}";
        // 推送响应
        String pushResp = "{\"code\":\"10000\",\"data\":\"task-id-001\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(authResp, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(pushResp, HttpStatus.OK));

        MessageResult result = provider.send(request, null);

        assertTrue(result.isSuccess());
        assertEquals("PUSH", result.getChannel());
        assertEquals("GETUI-task-id-001", result.getProviderTraceId());
        verify(restTemplate, atLeastOnce()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void send_returnsFailWhenGetuiReturnsErrorCode() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("内容");
        String authResp = "{\"code\":\"10000\",\"data\":{\"token\":\"auth-token-002\"}}";
        String pushResp = "{\"code\":\"10005\",\"msg\":\"目标 cid 无效\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(authResp, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(pushResp, HttpStatus.OK));

        MessageResult result = provider.send(request, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("10005"));
        assertTrue(result.getErrorMessage().contains("目标 cid 无效"));
    }

    @Test
    void send_returnsFailOnAuthError() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("内容");
        // 鉴权失败
        String authResp = "{\"code\":\"10001\",\"msg\":\"签名错误\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(authResp, HttpStatus.OK));

        MessageResult result = provider.send(request, null);

        assertFalse(result.isSuccess());
        // 鉴权失败会抛 IllegalStateException 被 catch 转为 fail
        assertTrue(result.getErrorMessage().contains("IllegalStateException"));
    }

    @Test
    void send_returnsFailOnRestException() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("内容");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("连接超时"));

        MessageResult result = provider.send(request, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("连接超时"));
    }

    @Test
    void send_prioritizesDeviceTokenFromChannelMeta() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("fallback-cid");
        request.setContent("内容");
        Map<String, String> meta = new HashMap<>();
        meta.put("deviceToken", "device-token-from-meta");
        request.setChannelMeta(meta);
        String authResp = "{\"code\":\"10000\",\"data\":{\"token\":\"token-3\"}}";
        String pushResp = "{\"code\":\"10000\",\"data\":\"task-3\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(authResp, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(pushResp, HttpStatus.OK));

        MessageResult result = provider.send(request, null);

        assertTrue(result.isSuccess());
        // 验证请求 body 中使用了 deviceToken 而非 fallback-cid
        org.mockito.ArgumentCaptor<Object> bodyCaptor =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restTemplate, atLeastOnce()).postForEntity(anyString(), bodyCaptor.capture(), eq(String.class));
        // 第二次调用（推送）应包含 deviceToken
        Object pushCall = bodyCaptor.getAllValues().get(1);
        String json = com.alibaba.fastjson2.JSON.toJSONString(pushCall);
        assertTrue(json.contains("device-token-from-meta"), "应使用 channelMeta.deviceToken");
        assertFalse(json.contains("fallback-cid"), "不应回退到 receiver");
    }

    @Test
    void send_usesDefaultTitleWhenSubjectBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("正文");
        // subject 留空
        String authResp = "{\"code\":\"10000\",\"data\":{\"token\":\"token-4\"}}";
        String pushResp = "{\"code\":\"10000\",\"data\":\"task-4\"}";
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(authResp, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(pushResp, HttpStatus.OK));

        MessageResult result = provider.send(request, null);

        assertTrue(result.isSuccess());
        // 验证推送 body 中标题回退为"通知"
        org.mockito.ArgumentCaptor<Object> bodyCaptor =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restTemplate, atLeastOnce()).postForEntity(anyString(), bodyCaptor.capture(), eq(String.class));
        String json = com.alibaba.fastjson2.JSON.toJSONString(bodyCaptor.getAllValues().get(1));
        assertTrue(json.contains("通知"), "subject 为空时标题应回退为'通知'");
    }

    @Test
    void send_passesTemplateButNotUsedForPush() {
        // 推送通道不使用模板（PushChannel 传 null），这里验证 template 参数不影响发送
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("推送内容");
        MsgTemplateDO template = new MsgTemplateDO();
        template.setSubject("模板标题-不应使用");

        ResponseEntity<String> authResp = new ResponseEntity<>(
                "{\"code\":\"10000\",\"data\":{\"token\":\"t\"}}", HttpStatus.OK);
        ResponseEntity<String> pushResp = new ResponseEntity<>(
                "{\"code\":\"10000\",\"data\":\"t-5\"}", HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(authResp)
                .thenReturn(pushResp);

        MessageResult result = provider.send(request, template);

        assertTrue(result.isSuccess());
    }
}
