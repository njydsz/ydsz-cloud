package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.config.AlertProperties;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SmsAlertNotifier} 单元测试（P1-5 告警渠道扩展）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>发送成功</li>
 *   <li>发送失败不抛异常</li>
 *   <li>enabled=false 时不发送</li>
 *   <li>手机号为空时不发送</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SmsAlertNotifier 短信通知器测试")
class SmsAlertNotifierTest {

    private AlertProperties alertProperties;
    private RestTemplate restTemplate;
    private SmsAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        alertProperties = new AlertProperties();
        restTemplate = mock(RestTemplate.class);
        notifier = new SmsAlertNotifier(alertProperties, restTemplate);
    }

    @Test
    @DisplayName("supportedChannel: 返回 SMS")
    void supportedChannel_returnsSms() {
        assertEquals(AlertChannel.SMS, notifier.supportedChannel());
    }

    @Test
    @DisplayName("notify: 使用配置手机号发送成功")
    void notify_success_callsRestTemplate() throws Exception {
        AlertProperties.Sms sms = alertProperties.getSms();
        sms.setEnabled(true);
        sms.setWebhookUrl("https://message-service/api/sms/send");
        sms.setPhoneNumbers("13800000000,13900000000");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":\"OK\"}", HttpStatus.OK));

        notifier.notify(buildContext(), buildRule(), List.of());

        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("notify: 使用 receivers 中的手机号发送")
    void notify_usesReceiversAsPhoneNumbers() throws Exception {
        AlertProperties.Sms sms = alertProperties.getSms();
        sms.setEnabled(true);
        sms.setWebhookUrl("https://message-service/api/sms/send");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":\"OK\"}", HttpStatus.OK));

        notifier.notify(buildContext(), buildRule(), List.of("13800000000", "13900000000"));

        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("notify: 发送失败不抛出异常（仅记录 WARN）")
    void notify_sendError_doesNotThrow() throws Exception {
        AlertProperties.Sms sms = alertProperties.getSms();
        sms.setEnabled(true);
        sms.setWebhookUrl("https://message-service/api/sms/send");
        sms.setPhoneNumbers("13800000000");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("连接超时"));

        // 不应抛出异常
        assertDoesNotThrow(() -> notifier.notify(buildContext(), buildRule(), List.of()));
    }

    @Test
    @DisplayName("notify: enabled=false 时不发送")
    void notify_disabled_doesNotSend() throws Exception {
        // 默认 enabled=false
        notifier.notify(buildContext(), buildRule(), List.of("13800000000"));

        verify(restTemplate, never())
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("notify: 手机号列表为空时不发送")
    void notify_emptyPhones_doesNotSend() throws Exception {
        AlertProperties.Sms sms = alertProperties.getSms();
        sms.setEnabled(true);
        sms.setWebhookUrl("https://message-service/api/sms/send");
        sms.setPhoneNumbers("");

        notifier.notify(buildContext(), buildRule(), List.of());

        verify(restTemplate, never())
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    private AlertContext buildContext() {
        return AlertContext.of(
                AlertType.FAIL, "job-1", "key-1", "name-1",
                "log-1", "5000", "NPE", "trace-1", "tenant-1");
    }

    private JobAlertRuleDO buildRule() {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId("rule-1");
        rule.setRuleName("测试规则");
        rule.setAlertType("FAIL");
        rule.setAlertLevel("ERROR");
        return rule;
    }
}
