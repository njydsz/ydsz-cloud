package com.njydsz.common.notify.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通知模块指标埋点（P1-4：Micrometer 指标监控 + P1-2：维度增强）
 *
 * <p>为邮件及其他通知渠道提供统一的指标埋点能力，暴露以下指标：
 *
 * <ul>
 *   <li>{@code notify_email_sent_total{channel,result,template}} — 发送总数（含成功/失败/模板标签）
 *   <li>{@code notify_email_duration_seconds{channel,template}} — 发送耗时分布
 *   <li>{@code notify_email_failed_total{channel,reason,error_type}} — 失败总数（含失败原因和错误类型标签）
 *   <li>{@code notify_channel_sent_total{channel,result,template}} — 各渠道发送总数
 *   <li>{@code notify_retry_total{channel,result}} — 重试总数
 *   <li>{@code notify_circuit_breaker_state{channel,state}} — 熔断器状态
 * </ul>
 *
 * <p>当 micrometer-core 依赖不存在时，自动降级为 no-op（不影响功能）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(NotifyMetrics.class);

  private static final String METRIC_EMAIL_SENT = "notify_email_sent_total";
  private static final String METRIC_EMAIL_DURATION = "notify_email_duration_seconds";
  private static final String METRIC_EMAIL_FAILED = "notify_email_failed_total";
  private static final String METRIC_CHANNEL_SENT = "notify_channel_sent_total";
  private static final String METRIC_RETRY = "notify_retry_total";
  private static final String METRIC_CIRCUIT_BREAKER = "notify_circuit_breaker_state";

  private static final boolean MICROMETER_AVAILABLE;

  static {
    boolean available;
    try {
      Class.forName("io.micrometer.core.instrument.MeterRegistry");
      available = true;
    } catch (ClassNotFoundException e) {
      available = false;
    }
    MICROMETER_AVAILABLE = available;
  }

  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

  /**
   * 构造通知指标收集器
   *
   * @param meterRegistry Micrometer MeterRegistry（可为 null，降级为 SimpleMeterRegistry）
   */
  public NotifyMetrics(MeterRegistry meterRegistry) {
    if (!MICROMETER_AVAILABLE) {
      this.meterRegistry = null;
      LOG.info("[NotifyMetrics] micrometer-core 依赖不存在，指标收集降级为 no-op");
    } else {
      this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
      LOG.info(
          "[NotifyMetrics] NotifyMetrics 初始化完成, registry={}",
          this.meterRegistry.getClass().getSimpleName());
    }
  }

  /**
   * 记录邮件发送结果（P1-2：增加模板维度）
   *
   * @param channel 渠道名称
   * @param success 是否成功
   * @param duration 发送耗时
   * @param templateCode 模板编码（可为 null）
   */
  public void recordEmailSend(
      String channel, boolean success, Duration duration, String templateCode) {
    if (meterRegistry == null) {
      return;
    }
    String result = success ? "success" : "failure";
    String template = templateCode != null ? templateCode : "default";
    Counter counter =
        counterCache.computeIfAbsent(
            METRIC_EMAIL_SENT + "_" + channel + "_" + result + "_" + template,
            k ->
                Counter.builder(METRIC_EMAIL_SENT)
                    .tag("channel", channel)
                    .tag("result", result)
                    .tag("template", template)
                    .register(meterRegistry));
    counter.increment();

    Timer timer =
        timerCache.computeIfAbsent(
            METRIC_EMAIL_DURATION + "_" + channel + "_" + template,
            k ->
                Timer.builder(METRIC_EMAIL_DURATION)
                    .tag("channel", channel)
                    .tag("template", template)
                    .register(meterRegistry));
    timer.record(duration);
  }

  /**
   * 记录邮件发送失败（P1-2：增加 error_type 维度）
   *
   * @param channel 渠道名称
   * @param reason 失败原因
   * @param errorType 错误类型（异常类名，可为 null）
   */
  public void recordEmailFailure(String channel, String reason, String errorType) {
    if (meterRegistry == null) {
      return;
    }
    Counter counter =
        counterCache.computeIfAbsent(
            METRIC_EMAIL_FAILED
                + "_"
                + channel
                + "_"
                + reason
                + "_"
                + (errorType != null ? errorType : "unknown"),
            k ->
                Counter.builder(METRIC_EMAIL_FAILED)
                    .tag("channel", channel)
                    .tag("reason", reason != null ? reason : "unknown")
                    .tag("error_type", errorType != null ? errorType : "unknown")
                    .register(meterRegistry));
    counter.increment();
  }

  /**
   * 记录各渠道发送结果（P1-2：增加模板维度）
   *
   * @param channel 渠道名称
   * @param success 是否成功
   * @param templateCode 模板编码（可为 null）
   */
  public void recordChannelSend(String channel, boolean success, String templateCode) {
    if (meterRegistry == null) {
      return;
    }
    String result = success ? "success" : "failure";
    String template = templateCode != null ? templateCode : "default";
    Counter counter =
        counterCache.computeIfAbsent(
            METRIC_CHANNEL_SENT + "_" + channel + "_" + result + "_" + template,
            k ->
                Counter.builder(METRIC_CHANNEL_SENT)
                    .tag("channel", channel)
                    .tag("result", result)
                    .tag("template", template)
                    .register(meterRegistry));
    counter.increment();
  }

  /**
   * 记录重试结果（P1-2）
   *
   * @param channel 渠道名称
   * @param success 重试是否成功
   */
  public void recordRetry(String channel, boolean success) {
    if (meterRegistry == null) {
      return;
    }
    String result = success ? "success" : "failure";
    Counter counter =
        counterCache.computeIfAbsent(
            METRIC_RETRY + "_" + channel + "_" + result,
            k ->
                Counter.builder(METRIC_RETRY)
                    .tag("channel", channel)
                    .tag("result", result)
                    .register(meterRegistry));
    counter.increment();
  }

  /**
   * 记录熔断器状态变更（P1-2）
   *
   * @param channel 渠道名称
   * @param state 熔断器状态
   */
  public void recordCircuitBreakerState(String channel, String state) {
    if (meterRegistry == null) {
      return;
    }
    counterCache.computeIfAbsent(
        METRIC_CIRCUIT_BREAKER + "_" + channel + "_" + state,
        k ->
            Counter.builder(METRIC_CIRCUIT_BREAKER)
                .tag("channel", channel)
                .tag("state", state)
                .register(meterRegistry));
  }

  /**
   * 判断 Micrometer 是否可用
   *
   * @return true 表示指标收集功能正常
   */
  public static boolean isMicrometerAvailable() {
    return MICROMETER_AVAILABLE;
  }
}
