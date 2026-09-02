package com.njydsz.cronjob.server.core.dispatch;

import java.io.IOException;
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
import com.njydsz.cronjob.domain.repository.JobWebhookRepository;
import com.njydsz.cronjob.domain.repository.WebhookRetryRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookRetryVO;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;

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
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventDispatcher {
  /** HTTP 成功状态码下限 */
  private static final int HTTP_OK_MIN = 200;

  /** HTTP 成功状态码上限（不含） */
  private static final int HTTP_OK_MAX_EXCLUSIVE = 300;

  /** 响应体日志截断长度 */
  private static final int BODY_LOG_MAX_LENGTH = 200;

  /** 连接超时：5 秒 */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  /** Webhook 推送超时时间（秒） */
  private static final long WEBHOOK_REQUEST_TIMEOUT_SECONDS = 10;

  /** HMAC 算法名称 */
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /** 重试补偿记录默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRIES = 5;

  /** 重试补偿记录首次重试延迟（秒） */
  private static final long INITIAL_RETRY_DELAY_SECONDS = 30;


  private final JobWebhookRepository jobWebhookRepository;
  private final WebhookRetryRepository webhookRetryRepository;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

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
      List<JobWebhookVO> webhooks = jobWebhookRepository.findActiveByEventAndJob(eventType, jobKey);
      if (webhooks.isEmpty()) {
        return;
      }
      ObjectNode eventBody = new ObjectNode();
      eventBody.put("eventType", eventType);
      eventBody.put("jobKey", jobKey);
      eventBody.put("timestamp", LocalDateTime.now().toString());
      eventBody.put("data", payload);

      for (JobWebhookVO webhook : webhooks) {
        boolean success = sendWebhookWithRetry(webhook, eventBody);
        if (!success) {
          // P1-3: 在线重试耗尽后写入补偿表，由 WebhookRetryScanTask 异步重试
          createRetryRecord(webhook, eventBody);
        }
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
  public boolean sendTest(JobWebhookVO webhook) {
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
  private boolean sendWebhookWithRetry(JobWebhookVO webhook, ObjectNode body) {
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
  private boolean doSend(JobWebhookVO webhook, ObjectNode body) throws IOException, InterruptedException {
    String method = webhook.getHttpMethod() != null ? webhook.getHttpMethod() : "POST";
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(webhook.getCallbackUrl()))
            .timeout(Duration.ofSeconds(WEBHOOK_REQUEST_TIMEOUT_SECONDS))
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

    if (response.statusCode() >= HTTP_OK_MIN && response.statusCode() < HTTP_OK_MAX_EXCLUSIVE) {
      return true;
    }
    log.warn(
        "[Webhook] 非 2xx 响应: webhook={} url={} status={} body={}",
        webhook.getName(),
        webhook.getCallbackUrl(),
        response.statusCode(),
        response.body() == null
            ? ""
            : response.body().substring(0, Math.min(BODY_LOG_MAX_LENGTH, response.body().length())));
    return false;
  }

  /**
   * P1-3: 创建重试补偿记录。
   *
   * <p>当在线重试（3 次）全部失败时，将本次推送写入 {@code ydsz_job_webhook_retry} 补偿表，
   * 由 {@code WebhookRetryScanTask} 周期性扫描并指数退避重试。
   *
   * @param webhook Webhook 配置
   * @param body 请求体
   */
  private void createRetryRecord(JobWebhookVO webhook, ObjectNode body) {
    try {
      JobWebhookRetryVO retryVO = new JobWebhookRetryVO();
      retryVO.setWebhookId(webhook.getId());
      retryVO.setEventType(body.getString("eventType"));
      retryVO.setJobKey(body.getString("jobKey"));
      retryVO.setCallbackUrl(webhook.getCallbackUrl());
      retryVO.setHttpMethod(webhook.getHttpMethod());
      retryVO.setHeaders(webhook.getHeaders());
      retryVO.setWebhookSecret(webhook.getSecret());
      retryVO.setPayloadJson(YdszJson.toJson(body));
      retryVO.setRetryCount(0);
      retryVO.setMaxRetries(DEFAULT_MAX_RETRIES);
      retryVO.setNextRetryTime(LocalDateTime.now().plusSeconds(INITIAL_RETRY_DELAY_SECONDS));
      retryVO.setRetryStatus("PENDING");
      retryVO.setCreatedAt(LocalDateTime.now());
      webhookRetryRepository.create(retryVO);
      log.info(
          "[Webhook] 已写入补偿记录: webhook={} eventType={} url={}",
          webhook.getName(),
          body.getString("eventType"),
          webhook.getCallbackUrl());
    } catch (Exception e) {
      log.error(
          "[Webhook] 写入补偿记录失败: webhook={} reason={}",
          webhook.getName(),
          e.getMessage(),
          e);
    }
  }

  /** 计算 HMAC-SHA256 签名。 */
  private String computeSignature(String body, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(), HMAC_ALGORITHM));
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
