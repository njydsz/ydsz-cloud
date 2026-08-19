package com.njydsz.cronjob.server.core.dispatch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;

/**
 * WebHook 事件分发器（P3-13 WebHook 事件订阅）。
 *
 * <p>监听任务生命周期事件，匹配已配置的 WebHook 订阅并推送通知。
 *
 * <h3>支持的事件类型</h3>
 *
 * <ul>
 *   <li>TASK_STARTED: 任务开始执行
 *   <li>TASK_SUCCESS: 任务执行成功
 *   <li>TASK_FAILED: 任务执行失败
 *   <li>TASK_TIMEOUT: 任务执行超时
 *   <li>DAG_COMPLETED: DAG 工作流执行完成
 * </ul>
 *
 * <h3>推送格式</h3>
 *
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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventDispatcher {

  private final JobWebhookMapper webhookMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /**
   * 推送 WebHook 事件。
   *
   * @param eventType 事件类型
   * @param jobKey 任务 KEY
   * @param payload 事件数据
   */
  @Async
  public void dispatchEvent(String eventType, String jobKey, Map<String, Object> payload) {
    try {
      List<JobWebhook> webhooks = webhookMapper.selectActiveByEventAndJob(eventType, jobKey);
      if (webhooks.isEmpty()) {
        return;
      }
      ObjectNode eventBody = new ObjectNode();
      eventBody.put("eventType", eventType);
      eventBody.put("jobKey", jobKey);
      eventBody.put("timestamp", LocalDateTime.now().toString());
      eventBody.put("data", payload);

      for (JobWebhook webhook : webhooks) {
        sendWebhookWithRetry(webhook, eventBody);
      }
    } catch (Exception e) {
      log.error(
          "[Webhook] 事件分发异常: eventType={} jobKey={} reason={}",
          eventType,
          jobKey,
          e.getMessage(),
          e);
    }
  }

  /**
   * P0-F3: 发送测试事件到指定 WebHook。
   *
   * <p>供 {@code JobWebhookController.testWebhook} 调用，主动发送一个 {@code TEST_WEBHOOK} 合成事件
   * 验证 WebHook 配置正确性（URL/签名/请求头）。同步执行并返回推送结果。
   *
   * @param webhook WebHook 订阅配置
   * @return true=推送成功；false=重试耗尽仍失败
   */
  public boolean sendTest(JobWebhook webhook) {
    ObjectNode body = new ObjectNode();
    body.put("eventType", "TEST_WEBHOOK");
    body.put("jobKey", webhook.getJobKey() != null ? webhook.getJobKey() : "");
    body.put("timestamp", LocalDateTime.now().toString());
    ObjectNode data = new ObjectNode();
    data.put("message", "This is a test event from ydsz-cronjob");
    data.put("webhookId", webhook.getId());
    data.put("webhookName", webhook.getName() != null ? webhook.getName() : "");
    body.put("data", data);
    return sendWebhookWithRetry(webhook, body);
  }

  /** 重试退避间隔（毫秒）：第 1 次失败后等 1s，第 2 次失败后等 5s，最多 3 次尝试。 */
  private static final long[] RETRY_BACKOFF_MS = {1_000L, 5_000L};

  /**
   * P0-F5: 发送 WebHook 通知（带指数退避重试）。
   *
   * <p>原实现失败仅 log 丢弃，网络抖动/接收方瞬时不可用时事件丢失。现引入最多 3 次尝试
   * （1s / 5s 退避），全部失败才放弃并记录 ERROR，供运维排查。
   *
   * @return true 推送成功；false 重试耗尽仍失败
   */
  private boolean sendWebhookWithRetry(JobWebhook webhook, ObjectNode body) {
    Throwable lastError = null;
    for (int attempt = 1; attempt <= RETRY_BACKOFF_MS.length + 1; attempt++) {
      try {
        if (doSend(webhook, body)) {
          log.debug(
              "[Webhook] 推送成功: webhook={} url={} attempt={}",
              webhook.getName(),
              webhook.getCallbackUrl(),
              attempt);
          return true;
        }
        log.warn(
            "[Webhook] 推送失败(第 {} 次): webhook={} url={}",
            attempt,
            webhook.getName(),
            webhook.getCallbackUrl());
      } catch (Exception e) {
        lastError = e;
        log.warn(
            "[Webhook] 推送异常(第 {} 次): webhook={} url={} reason={}",
            attempt,
            webhook.getName(),
            webhook.getCallbackUrl(),
            e.getMessage());
      }
      if (attempt <= RETRY_BACKOFF_MS.length) {
        try {
          Thread.sleep(RETRY_BACKOFF_MS[attempt - 1]);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    log.error(
        "[Webhook] 推送失败(重试耗尽): webhook={} url={} lastError={}",
        webhook.getName(),
        webhook.getCallbackUrl(),
        lastError != null ? lastError.getMessage() : "非 2xx 响应");
    return false;
  }

  /** 单次 HTTP 推送（IOException/InterruptedException 由调用方 sendWebhookWithRetry 捕获）。 */
  private boolean doSend(JobWebhook webhook, ObjectNode body) throws java.io.IOException, InterruptedException {
    String method = webhook.getHttpMethod() != null ? webhook.getHttpMethod() : "POST";
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(webhook.getCallbackUrl()))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json; charset=UTF-8");

    // 添加自定义请求头
    if (webhook.getHeaders() != null && !webhook.getHeaders().isBlank()) {
      ObjectNode headers = YdszJson.parseObject(webhook.getHeaders());
      for (String key : headers.keySet()) {
        builder.header(key, headers.getString(key));
      }
    }

    // 添加签名头（如有密钥）
    if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
      String signature = computeSignature(YdszJson.toJson(body), webhook.getSecret());
      builder.header("X-Webhook-Signature", signature);
    }

    HttpRequest request =
        builder
            .method(method, HttpRequest.BodyPublishers.ofString(YdszJson.toJson(body)))
            .build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return true;
    }
    log.warn(
        "[Webhook] 非 2xx 响应: webhook={} url={} status={} body={}",
        webhook.getName(),
        webhook.getCallbackUrl(),
        response.statusCode(),
        response.body() == null
            ? ""
            : response.body().substring(0, Math.min(200, response.body().length())));
    return false;
  }

  /** 计算 HMAC-SHA256 签名。 */
  private String computeSignature(String body, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
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
