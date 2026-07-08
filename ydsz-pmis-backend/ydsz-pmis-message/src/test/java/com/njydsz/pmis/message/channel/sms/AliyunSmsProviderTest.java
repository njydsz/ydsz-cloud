package com.njydsz.pmis.message.channel.sms;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AliyunSmsProvider 单元测试：mock RestTemplate 验证各分支。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class AliyunSmsProviderTest {

    private MessageProperties.AliyunSmsConfig config;
    private RestTemplate restTemplate;
    private AliyunSmsProvider provider;

    @BeforeEach
    void setUp() {
        config = new MessageProperties.AliyunSmsConfig();
        config.setAccessKeyId("test-ak");
        config.setAccessKeySecret("test-sk");
        config.setSignName("测试签名");
        restTemplate = mock(RestTemplate.class);
        provider = new AliyunSmsProvider(config, restTemplate);
    }

    @Test
    void providerType_isAliyun() {
        assertEquals("aliyun", provider.providerType());
    }

    @Test
    void send_returnsFailWhenPhoneBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("");
        MessageResult result = provider.send(request, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("手机号"));
    }

    @Test
    void send_returnsFailWhenCredentialMissing() {
        config.setAccessKeyId("");
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MessageResult result = provider.send(request, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("凭证"));
    }

    @Test
    void send_returnsFailWhenTemplateCodeMissing() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MessageResult result = provider.send(request, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("签名或模板"));
    }

    @Test
    void send_returnsOkWhenAliyunReturnsOK() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        request.setParams(Map.of("code", "1234"));
        MsgTemplateDO template = new MsgTemplateDO();
        template.setSignName("签名");
        template.setProviderKey("SMS_123456");
        String respBody = "{\"Code\":\"OK\",\"BizId\":\"1234567890\",\"Message\":\"OK\"}";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respBody, HttpStatus.OK));

        MessageResult result = provider.send(request, template);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("ALIYUN-"));
        verify(restTemplate).getForEntity(anyString(), eq(String.class));
    }

    @Test
    void send_returnsFailWhenAliyunReturnsError() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MsgTemplateDO template = new MsgTemplateDO();
        template.setSignName("签名");
        template.setProviderKey("SMS_123456");
        String respBody = "{\"Code\":\"isv.BUSINESS_LIMIT_CONTROL\",\"Message\":\"业务限流\"}";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respBody, HttpStatus.OK));

        MessageResult result = provider.send(request, template);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("isv.BUSINESS_LIMIT_CONTROL"));
    }

    @Test
    void send_returnsFailOnRestException() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MsgTemplateDO template = new MsgTemplateDO();
        template.setSignName("签名");
        template.setProviderKey("SMS_123456");
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("连接超时"));

        MessageResult result = provider.send(request, template);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("连接超时"));
    }

    @Test
    void send_usesConfigSignNameWhenTemplateSignNameBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        MsgTemplateDO template = new MsgTemplateDO();
        template.setProviderKey("SMS_123456");
        // template.signName 为空,应回退 config.signName
        String respBody = "{\"Code\":\"OK\",\"BizId\":\"BIZ-1\",\"Message\":\"OK\"}";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respBody, HttpStatus.OK));

        MessageResult result = provider.send(request, template);

        assertTrue(result.isSuccess());
    }
}
