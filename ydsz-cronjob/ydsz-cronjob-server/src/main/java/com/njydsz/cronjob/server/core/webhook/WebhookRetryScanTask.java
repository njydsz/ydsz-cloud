package com.njydsz.cronjob.server.core.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.cronjob.domain.repository.WebhookRetryRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookRetryVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.WebhookRetryConfig;
import com.njydsz.cronjob.server.core.maintenance.ScanTask;

/**
 * P1-3: Webhook 重试补偿扫描任务。
 *
 * <p>周期性扫描 {@code ydsz_job_webhook_retry} 补偿表，对 {@code retry_status=PENDING} 的记录
 * 尝试重新推送。指数退避策略：每次重试间隔翻倍，超过 {@code maxRetries} 后标记为 DEAD。
 *
 * <h3>运作流程</h3>
 *
 * <ol>
 *   <li>扫描到期重试记录（{@code next_retry_time <= NOW()}）
 *   <li>解析 payloadJson 构建 HTTP 请求
 *   <li>发送后按响应状态更新记录：成功 → SUCCESS；失败且未达上限 → 更新 retryCount/nextRetryTime；
 *       达上限 → DEAD
 * </ol>
 *
 * <h3>云顶编码规范 §24 配置管理规范</h3>
 *
 * <p>扫描间隔与批量大小均通过 {@link CronjobProperties.WebhookRetryConfig} 配置化，无硬编码。
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebhookRetryScanTask implements ScanTask {

  /** HTTP 成功状态码下限 */
  private static final int HTTP_OK_MIN = 200;

  /** HTTP 成功状态码上限（不含） */
  private static final int HTTP_OK_MAX_EXCLUSIVE = 300;

  /** HTTP 请求超时：10 秒 */
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  /** HMAC 算法 */
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  /** 签名头名称 */
  private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

  /** 最大退避时间：60 秒 */
  private static final long MAX_BACKOFF_MS = 60_000L;

  /** 退避基数：1000 ms */
  private static final long BACKOFF_BASE_MS = 1_000L;

  private final WebhookRetryRepository webhookRetryRepository;
  private final CronjobProperties cronjobProperties;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Override
  public String name() {
    return "webhook-retry";
  }

  @Override
  public void scan() {
    WebhookRetryConfig config = cronjobProperties.getWebhookRetry();
    int batchSize = config.getBatchSize();

    List<JobWebhookRetryVO> pendingRetries =
        webhookRetryRepository.findPendingRetries(LocalDateTime.now(), batchSize);
    if (pendingRetries.isEmpty()) {
      return;
    }
    log.info("[WebhookRetry] 本次扫描到 {} 条待重试记录", pendingRetries.size());

    int successCount = 0;
    int deadCount = 0;
    for (JobWebhookRetryVO retry : pendingRetries) {
      boolean success;
      try {
        success = doSend(retry);
      } catch (Exception e) {
        log.warn(
            "[WebhookRetry] 重试推送异常: retryId={} webhookId={} reason={}",
            retry.getId(),
            retry.getWebhookId(),
            e.getMessage());
        success = false;
      }
      if (success) {
        webhookRetryRepository.markSuccess(retry.getId(), LocalDateTime.now());
        successCount++;
      } else {
        int newCount = retry.getRetryCount() + 1;
        if (newCount >= retry.getMaxRetries()) {
          webhookRetryRepository.markDead(retry.getId(), LocalDateTime.now(), "重试次数耗尽");
          deadCount++;
        } else {
          long backoffMs = calculateBackoffMs(newCount);
          LocalDateTime nextRetry = LocalDateTime.now().plusNanos(backoffMs * 1_000_000L);
          webhookRetryRepository.updateForRetry(retry.getId(), newCount, nextRetry, null);
        }
      }
    }
    if (successCount > 0 || deadCount > 0) {
      log.info(
          "[WebhookRetry] 本轮处理完成: successCount={} deadCount={} totalProcessed={}",
          successCount,
          deadCount,
          pendingRetries.size());
    }
  }

  @Override
  public long intervalMs() {
    long configuredInterval = cronjobProperties.getWebhookRetry().getScanIntervalMs();
    return configuredInterval > 0 ? configuredInterval : 30_000L;
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:webhook-retry";
  }

  /**
   * 执行单次 HTTP 推送。
   *
   * <p>复用 {@link WebhookEventDispatcher} 类似的发送逻辑，包含签名与超时控制。
   *
   * @param retry 重试记录
   * @return true 推送成功（2xx）；false 非 2xx 响应
   * @throws Exception IO 或签名异常
   */
  private boolean doSend(JobWebhookRetryVO retry) throws Exception {
    ObjectNode body = YdszJson.parseObject(retry.getPayloadJson());
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(retry.getCallbackUrl()))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json; charset=UTF-8");

    // 如有 secret，计算签名
    String secret = retry.getWebhookSecret();
    if (secret != null && !secret.isBlank()) {
      String signature = computeSignature(YdszJson.toJson(body), secret);
      builder.header(SIGNATURE_HEADER, signature);
    }

    String method = retry.getHttpMethod() != null ? retry.getHttpMethod() : "POST";
    HttpRequest request =
        builder.method(method, HttpRequest.BodyPublishers.ofString(YdszJson.toJson(body))).build();
    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < HTTP_OK_MIN || response.statusCode() >= HTTP_OK_MAX_EXCLUSIVE) {
      log.warn(
          "[WebhookRetry] 非 2xx 响应: retryId={} url={} status={}",
          retry.getId(),
          retry.getCallbackUrl(),
          response.statusCode());
    }
    return response.statusCode() >= HTTP_OK_MIN
        && response.statusCode() < HTTP_OK_MAX_EXCLUSIVE;
  }

  /**
   * 计算指数退避毫秒数：2^retryCount * 1000ms，上限 60s。
   *
   * @param retryCount 当前重试次数（从 1 开始）
   * @return 退避毫秒数
   */
  private long calculateBackoffMs(int retryCount) {
    long backoff = (long) Math.pow(2, retryCount) * BACKOFF_BASE_MS;
    return Math.min(backoff, MAX_BACKOFF_MS);
  }

  /**
   * HMAC-SHA256 签名。
   *
   * @param payload 请求体 JSON
   * @param secret Webhook 密钥
   * @return 十六进制签名
   * @throws Exception 算法初始化异常
   */
  private String computeSignature(String payload, String secret) throws Exception {
    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
    byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
