package com.njydsz.gateway.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;

/**
 * P3-7: 网关层告警聚合与分级服务
 *
 * <p>在网关过滤器与 SentryObservation 之间增加一层本地告警聚合逻辑：
 *
 * <ul>
 *   <li>分级路由：P0/P1 立即发送；P2 延迟聚合后发送；P3 仅记录日志
 *   <li>本地滑动窗口聚合：相同 {@code dedupKey} 的告警在窗口期内仅发送一次， 后续触发以计数器累加，减少告警风暴
 *   <li>自动flush：定时任务将窗口期内的聚合告警发送出去
 * </ul>
 *
 * <h3>聚合策略</h3>
 *
 * <ul>
 *   <li>窗口时长：60 秒
 *   <li>去重维度：{@link AlertEvent#dedupKey()}
 *   <li>聚合后消息格式："告警名称 在 60s 内触发 N 次"
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <p>网关过滤器统一通过 {@link #alert(AlertEvent)} 而非直接调用 SentryObservation。
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
@Service
@ConditionalOnClass(SentryObservation.class)
public class GatewayAlertService {

  /** 聚合窗口时长（秒） */
  private static final long AGGREGATION_WINDOW_SECONDS = 60;

  /** P2 延迟发送前的最小触发次数（达到阈值才发送，过滤瞬时抖动） */
  private static final int P2_MIN_TRIGGER_COUNT = 3;

  /** 告警聚合记录 */
  private final ConcurrentHashMap<String, AggregatedAlert> aggregatedAlerts =
      new ConcurrentHashMap<>();

  /** 定时 flush 线程池 */
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "gateway-alert-flush");
            t.setDaemon(true);
            return t;
          });

  /**
   * 构造网关告警服务
   *
   * <p>启动定时 flush 任务，每 30 秒检查并发送窗口期内的聚合告警。
   */
  public GatewayAlertService() {
    scheduler.scheduleAtFixedRate(this::flushAggregatedAlerts, 30, 30, TimeUnit.SECONDS);
    log.info("[GatewayAlert] 告警聚合服务初始化完成，聚合窗口={}s", AGGREGATION_WINDOW_SECONDS);
  }

  /**
   * P3-7: 发布网关告警（经分级和聚合处理后发送）
   *
   * @param event 告警事件
   */
  public void alert(AlertEvent event) {
    if (event == null) {
      return;
    }

    try {
      AlertSeverity severity = event.getSeverity();
      if (severity == null) {
        severity = AlertSeverity.P3;
      }

      switch (severity) {
        case P0, P1 -> {
          // P0/P1：立即发送（经 Sentry 自身收敛）
          doAlert(event);
        }
        case P2 -> {
          // P2：本地窗口聚合（达到阈值才发送，过滤瞬时抖动）
          aggregateAndMaybeSend(event);
        }
        case P3 -> {
          // P3：仅记录，不通知
          log.debug(
              "[GatewayAlert] P3 告警仅记录: name={} summary={}", event.getName(), event.getSummary());
        }
      }
    } catch (Exception e) {
      // 告警处理异常不应影响主流程
      log.debug("[GatewayAlert] 告警处理异常: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 聚合 P2 告警并判断是否发送
   *
   * @param event 告警事件
   */
  private void aggregateAndMaybeSend(AlertEvent event) {
    String dedupKey = event.dedupKey();
    Instant now = Instant.now();

    AggregatedAlert aggregated =
        aggregatedAlerts.computeIfAbsent(
            dedupKey,
            k -> {
              AggregatedAlert newAlert = new AggregatedAlert();
              newAlert.event = event;
              newAlert.firstTriggeredAt = now;
              newAlert.lastTriggeredAt = now;
              return newAlert;
            });

    synchronized (aggregated) {
      aggregated.count.incrementAndGet();
      aggregated.lastTriggeredAt = now;

      // 达到阈值立即发送
      if (aggregated.count.get() >= P2_MIN_TRIGGER_COUNT && !aggregated.sent) {
        aggregated.sent = true;
        doAlert(buildAggregatedEvent(aggregated));
      }
    }
  }

  /**
   * P3-7: 构建聚合后的告警事件
   *
   * @param aggregated 聚合记录
   * @return 聚合后的告警事件
   */
  private AlertEvent buildAggregatedEvent(AggregatedAlert aggregated) {
    AlertEvent original = aggregated.event;
    long secondsBetween =
        Duration.between(aggregated.firstTriggeredAt, aggregated.lastTriggeredAt).getSeconds();

    return AlertEvent.builder()
        .name(original.getName())
        .severity(original.getSeverity())
        .summary(
            original.getSummary() + " (" + aggregated.count.get() + " 次/" + secondsBetween + "s)")
        .description(original.getDescription())
        .category(original.getCategory())
        .labels(original.getLabels())
        .value(original.getValue())
        .runbookUrl(original.getRunbookUrl())
        .build();
  }

  /** P3-7: 定时 flush 聚合告警（将窗口期内未达到 P2 阈值的告警发送出去） */
  private void flushAggregatedAlerts() {
    try {
      Instant now = Instant.now();
      Instant windowStart = now.minusSeconds(AGGREGATION_WINDOW_SECONDS);

      aggregatedAlerts
          .entrySet()
          .removeIf(
              entry -> {
                AggregatedAlert aggregated = entry.getValue();
                // 移除超出窗口期的记录
                if (aggregated.firstTriggeredAt.isBefore(windowStart)) {
                  // 如果未曾发送且有触发次数，发送一次汇总
                  if (!aggregated.sent && aggregated.count.get() > 0) {
                    doAlert(buildAggregatedEvent(aggregated));
                  }
                  return true;
                }
                return false;
              });
    } catch (Exception e) {
      log.debug("[GatewayAlert] flush 聚合告警异常: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 经 SentryObservation 发送告警
   *
   * @param event 告警事件
   */
  private void doAlert(AlertEvent event) {
    try {
      SentryObservation.alert(event);
      log.debug("[GatewayAlert] 告警已发送: name={} severity={}", event.getName(), event.getSeverity());
    } catch (Exception e) {
      log.warn("[GatewayAlert] 告警发送失败: {}", e.getMessage());
    }
  }

  /** P3-7: 关闭时 flush 剩余告警 */
  @PreDestroy
  public void destroy() {
    try {
      flushAggregatedAlerts();
      scheduler.shutdownNow();
    } catch (Exception e) {
      log.debug("[GatewayAlert] 关闭异常: {}", e.getMessage());
    }
  }

  /** 聚合告警记录 */
  private static class AggregatedAlert {
    /** 原始告警事件 */
    AlertEvent event;

    /** 首次触发时间 */
    Instant firstTriggeredAt;

    /** 最近触发时间 */
    Instant lastTriggeredAt;

    /** 触发次数 */
    final AtomicInteger count = new AtomicInteger(0);

    /** 是否已发送 */
    volatile boolean sent;
  }
}
