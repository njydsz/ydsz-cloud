package com.njydsz.common.sentry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.domain.AlertCategory;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;

/**
 * Sentry 统一一站式门面。
 *
 * <p>封装常见"计时 + SLA + trace tag + 异常告警"组合操作，降低业务方使用 sentry 的认知负担。 业务方只需要调用一个方法即可完成原来需要分别调用：
 *
 * <ul>
 *   <li>{@link SentryService#time} — 耗时统计
 *   <li>{@link com.njydsz.common.sentry.spi.SlaCollector#recordTotal} — SLA 记录
 *   <li>{@link com.njydsz.common.sentry.spi.TraceContext#tag} — Span 标签注入
 *   <li>{@link com.njydsz.common.sentry.spi.AlertPublisher#publish} — 异常告警
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class OrderService {
 *     private final SentryFacade sentryFacade;
 *
 *     public OrderVO create(OrderDTO dto) {
 *         return sentryFacade.monitor("order.create",
 *             Map.of("channel", dto.getChannel()),
 *             5000,  // SLA 阈值 ms
 *             () -> {
 *                 // 业务逻辑
 *                 return doCreate(dto);
 *             });
 *     }
 * }
 * }</pre>
 *
 * <p>26.09.20 新增。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
@Component
public class SentryFacade {

  private final SentryService sentryService;

  /**
   * 构造统一门面。
   *
   * @param sentryService 可观测性服务
   */
  public SentryFacade(SentryService sentryService) {
    this.sentryService = sentryService;
  }

  /**
   * 一站式监控：自动完成耗时统计 + SLA 记录 + trace tag。
   *
   * <p>行为：
   *
   * <ul>
   *   <li>若 {@code slaThresholdMillis > 0}，记录 SLA 整体执行结果</li>
   *   <li>注入 trace tag：{@code (operationName, "completed")} 或 {@code (operationName, "failed")}</li>
   *   <li>操作抛出异常时自动注入 tag 并上抛</li>
   * </ul>
   *
   * @param operationName 操作名称（作为指标名、SLA 名、trace tag key 前缀）
   * @param tags 指标 / SLA 标签
   * @param slaThresholdMillis SLA 阈值（毫秒），≤0 时跳过 SLA 记录
   * @param operation 要执行的业务操作
   * @param <T> 返回值类型
   * @return 操作结果
   * @throws Throwable 操作执行中的异常
   */
  public <T> T monitor(
      String operationName,
      Map<String, String> tags,
      long slaThresholdMillis,
      SentryService.CheckedSupplier<T> operation)
      throws Throwable {
    long start = System.currentTimeMillis();
    boolean isSuccess = false;
    try {
      T result = sentryService.time(operationName, operationName + " operation", tags, operation);
      isSuccess = true;
      return result;
    } finally {
      long tookMillis = System.currentTimeMillis() - start;
      if (slaThresholdMillis > 0) {
        sentryService.recordSla(operationName + ".total", null, tookMillis, isSuccess);
      }
      String tagKey = operationName.replace('.', '_') + "_status";
      sentryService.tag(tagKey, isSuccess ? "completed" : "failed");
    }
  }

  /**
   * 一站式监控（无返回值版）。
   *
   * @param operationName 操作名称
   * @param tags 指标 / SLA 标签
   * @param slaThresholdMillis SLA 阈值（毫秒），≤0 时跳过 SLA 记录
   * @param operation 要执行的业务操作
   */
  public void monitor(
      String operationName,
      Map<String, String> tags,
      long slaThresholdMillis,
      Runnable operation) {
    long start = System.currentTimeMillis();
    boolean isSuccess = false;
    try {
      sentryService.time(operationName, operationName + " operation", tags, operation);
      isSuccess = true;
    } finally {
      long tookMillis = System.currentTimeMillis() - start;
      if (slaThresholdMillis > 0) {
        sentryService.recordSla(operationName + ".total", null, tookMillis, isSuccess);
      }
      String tagKey = operationName.replace('.', '_') + "_status";
      sentryService.tag(tagKey, isSuccess ? "completed" : "failed");
    }
  }

  /**
   * 一站式监控 + 异常自动告警。
   *
   * <p>在 {@link #monitor} 基础上，若操作抛出业务异常，自动发布 {@link AlertSeverity#P2} 告警事件。
   *
   * @param operationName 操作名称
   * @param tags 指标 / SLA / 告警标签
   * @param slaThresholdMillis SLA 阈值（毫秒）
   * @param alertOnException 异常时是否自动发布告警
   * @param operation 要执行的业务操作
   * @param <T> 返回值类型
   * @return 操作结果
   * @throws Throwable 操作执行中的异常
   */
  public <T> T monitorWithAlert(
      String operationName,
      Map<String, String> tags,
      long slaThresholdMillis,
      boolean alertOnException,
      SentryService.CheckedSupplier<T> operation)
      throws Throwable {
    try {
      return monitor(operationName, tags, slaThresholdMillis, operation);
    } catch (Throwable e) {
      if (alertOnException) {
        alertOnFailure(operationName, tags, e);
      }
      throw e;
    }
  }

  /**
   * 发布业务异常告警（P2 级别）。
   *
   * @param operationName 操作名称
   * @param tags 告警标签
   * @param throwable 异常
   */
  private void alertOnFailure(String operationName, Map<String, String> tags, Throwable throwable) {
    try {
      Map<String, String> alertLabels = new java.util.HashMap<>(tags);
      alertLabels.put("exception", throwable.getClass().getSimpleName());
      alertLabels.put("message", throwable.getMessage() != null ? throwable.getMessage() : "");

      AlertEvent event =
          AlertEvent.builder()
              .name(operationName + ".failure")
              .severity(AlertSeverity.P2)
              .summary(operationName + " 执行失败：" + throwable.getClass().getSimpleName())
              .description(throwable.getMessage())
              .category(AlertCategory.AVAILABILITY)
              .labels(alertLabels)
              .build();

      boolean published = sentryService.alert(event);
      if (!published) {
        log.debug("[Sentry] 告警被收敛器抑制: operation={}", operationName);
      }
    } catch (Exception e) {
      log.warn("[Sentry] 告警发布异常（不影响主流程）: operation={}, err={}", operationName, e.getMessage());
    }
  }
}
