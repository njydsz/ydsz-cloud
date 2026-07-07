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
 * {@link FeishuAlertNotifier} 单元测试（P1-5 告警渠道扩展）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>发送成功</li>
 *   <li>发送失败（HTTP 错误）不抛异常</li>
 *   <li>enabled=false 时不发送</li>
 *   <li>Webhook URL 未配置时不发送</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FeishuAlertNotifier 飞书通知器测试")
class FeishuAlertNotifierTest {

    private AlertProperties alertProperties;
    private RestTemplate restTemplate;
    private FeishuAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        alertProperties = new AlertProperties();
        restTemplate = mock(RestTemplate.class);
        notifier = new FeishuAlertNotifier(alertProperties, restTemplate);
    }

    @Test
    @DisplayName("supportedChannel: 返回 FEISHU")
    void supportedChannel_returnsFeishu() {
        assertEquals(AlertChannel.FEISHU, notifier.supportedChannel());
    }

    @Test
    @DisplayName("notify: 发送成功时调用 RestTemplate")
    void notify_success_callsRestTemplate() throws Exception {
        AlertProperties.Feishu feishu = alertProperties.getFeishu();
        feishu.setEnabled(true);
        feishu.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test-token");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":0}", HttpStatus.OK));

        notifier.notify(buildContext(), buildRule(), List.of());

        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("notify: HTTP 错误时不抛出异常（仅记录 WARN）")
    void notify_httpError_doesNotThrow() throws Exception {
        AlertProperties.Feishu feishu = alertProperties.getFeishu();
        feishu.setEnabled(true);
        feishu.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test-token");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("HTTP 500"));

        // 不应抛出异常
        assertDoesNotThrow(() -> notifier.notify(buildContext(), buildRule(), List.of()));
    }

    @Test
    @DisplayName("notify: enabled=false 时不发送")
    void notify_disabled_doesNotSend() throws Exception {
        // 默认 enabled=false
        notifier.notify(buildContext(), buildRule(), List.of());

        verify(restTemplate, never())
                .postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("notify: Webhook URL 未配置时不发送")
    void notify_webhookUrlBlank_doesNotSend() throws Exception {
        AlertProperties.Feishu feishu = alertProperties.getFeishu();
        feishu.setEnabled(true);
        feishu.setWebhookUrl("");

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
