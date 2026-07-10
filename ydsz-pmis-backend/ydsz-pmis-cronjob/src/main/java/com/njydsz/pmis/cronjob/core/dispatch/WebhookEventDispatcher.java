package com.njydsz.pmis.cronjob.core.dispatch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.entity.job.JobWebhookDO;
import com.njydsz.pmis.cronjob.mapper.job.JobWebhookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WebHook 事件分发器（P3-13 WebHook 事件订阅）。
 *
 * <p>监听任务生命周期事件，匹配已配置的 WebHook 订阅并推送通知。
 *
 * <h3>支持的事件类型</h3>
 * <ul>
 *   <li>TASK_STARTED: 任务开始执行</li>
 *   <li>TASK_SUCCESS: 任务执行成功</li>
 *   <li>TASK_FAILED: 任务执行失败</li>
 *   <li>TASK_TIMEOUT: 任务执行超时</li>
 *   <li>DAG_COMPLETED: DAG 工作流执行完成</li>
 * </ul>
 *
 * <h3>推送格式</h3>
 * <pre>{@code
 * {
 *   "eventType": "TASK_SUCCESS",
 *   "jobKey": "data-sync-job",
 *   "jobName": "数据同步任务",
 *   "logId": "1234567890",
 *   "status": "SUCCESS",
 *   "duration": 1500,
 *   "timestamp": "2026-07-08T12:00:00",
 *   "data": { ... }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventDispatcher {

    private final JobWebhookMapper webhookMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 推送 WebHook 事件。
     *
     * @param eventType 事件类型
     * @param jobKey    任务 KEY
     * @param payload   事件数据
     */
    @Async
    public void dispatchEvent(String eventType, String jobKey, Map<String, Object> payload) {
        try {
            List<JobWebhookDO> webhooks = webhookMapper.selectActiveByEventAndJob(eventType, jobKey);
            if (webhooks.isEmpty()) {
                return;
            }
            JSONObject eventBody = new JSONObject();
            eventBody.put("eventType", eventType);
            eventBody.put("jobKey", jobKey);
            eventBody.put("timestamp", LocalDateTime.now().toString());
            eventBody.put("data", payload);

            for (JobWebhookDO webhook : webhooks) {
                sendWebhook(webhook, eventBody);
            }
        } catch (Exception e) {
            log.error("[Webhook] 事件分发异常: eventType={} jobKey={} reason={}",
                    eventType, jobKey, e.getMessage(), e);
        }
    }

    /**
     * 发送 WebHook 通知。
     */
    private void sendWebhook(JobWebhookDO webhook, JSONObject body) {
        try {
            String method = webhook.getHttpMethod() != null ? webhook.getHttpMethod() : "POST";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getCallbackUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8");

            // 添加自定义请求头
            if (webhook.getHeaders() != null && !webhook.getHeaders().isBlank()) {
                JSONObject headers = JSON.parseObject(webhook.getHeaders());
                for (String key : headers.keySet()) {
                    builder.header(key, headers.getString(key));
                }
            }

            // 添加签名头（如有密钥）
            if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
                String signature = computeSignature(body.toJSONString(), webhook.getSecret());
                builder.header("X-Webhook-Signature", signature);
            }

            HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofString(body.toJSONString())).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("[Webhook] 推送成功: webhook={} url={} status={}",
                        webhook.getName(), webhook.getCallbackUrl(), response.statusCode());
            } else {
                log.warn("[Webhook] 推送失败: webhook={} url={} status={} body={}",
                        webhook.getName(), webhook.getCallbackUrl(), response.statusCode(),
                        response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            }
        } catch (Exception e) {
            log.error("[Webhook] 推送异常: webhook={} url={} reason={}",
                    webhook.getName(), webhook.getCallbackUrl(), e.getMessage());
        }
    }

    /**
     * 计算 HMAC-SHA256 签名。
     */
    private String computeSignature(String body, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[Webhook] 签名计算失败: reason={}", e.getMessage());
            return "";
        }
    }
}
