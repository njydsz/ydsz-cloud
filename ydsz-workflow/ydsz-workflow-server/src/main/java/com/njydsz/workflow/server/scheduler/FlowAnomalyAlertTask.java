package com.njydsz.workflow.server.scheduler;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.context.TenantContext;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.workflow.domain.vo.FlowAnomalyVO;
import com.njydsz.workflow.server.config.FlowAlertProperties;
import com.njydsz.workflow.server.service.FlowAnalyticsService;

/**
 * 流程异常告警定时任务。
 *
 * <p>每小时执行一次异常检测，发现 RED 级别告警时记录日志并通过事件发布通知。
 * 实际通知投递（站内信/邮件/短信）由 ydsz-message 引擎或前端订阅实现。
 *
 * <p><b>降级策略：</b>告警检测本身不影响主流程；异常时仅记录 warn 日志并跳过本次执行。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.flow.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FlowAnomalyAlertTask {

  private final FlowAnalyticsService analyticsService;
  private final FlowAlertProperties alertProperties;

  /**
   * 定时异常检测（每小时执行）。
   *
   * <p>检测 RED 级别告警并记录，供后续接入 ydzs-message 引擎做通知投递。
   */
  @Scheduled(cron = "${ydsz.flow.alert.cron-expression:0 0 * * * ?}")
  public void detectAndAlert() {
    try {
      String defaultTenant = "0";
      TenantContext context = TenantContext.builder(defaultTenant).build();
      TenantContextHolder.runWithContext(context, () -> doDetect(defaultTenant));
    } catch (Exception e) {
      log.warn("[FlowAnomalyAlert] 异常检测执行失败: {}", e.getMessage());
    }
  }

  /**
   * 执行实际检测逻辑（已绑定租户上下文）。
   */
  private void doDetect(String tenantId) {
    try {
      List<FlowAnomalyVO> anomalies = analyticsService.detectAnomalies(
          tenantId,
          alertProperties.getStuckThresholdHours(),
          alertProperties.getRejectRateThreshold(),
          alertProperties.getBacklogThreshold());

      if (anomalies == null || anomalies.isEmpty()) {
        return;
      }

      long redCount = anomalies.stream()
          .filter(a -> "RED".equals(a.getWarnLevel()))
          .count();
      long yellowCount = anomalies.stream()
          .filter(a -> "YELLOW".equals(a.getWarnLevel()))
          .count();

      if (redCount > 0) {
        log.warn("[FlowAnomalyAlert] 检测到 {} 条 RED 告警, {} 条 YELLOW 告警, 需关注! anomalies={}",
            redCount, yellowCount, summarizeAnomalies(anomalies));
      } else if (yellowCount > 0) {
        log.info("[FlowAnomalyAlert] 检测到 {} 条 YELLOW 告警", yellowCount);
      }

    } catch (Exception e) {
      log.warn("[FlowAnomalyAlert] 异常检测执行失败: {}", e.getMessage());
    }
  }

  /**
   * 汇总异常信息用于日志输出（仅输出前 5 条的 type + description）。
   */
  private String summarizeAnomalies(List<FlowAnomalyVO> anomalies) {
    return anomalies.stream()
        .limit(5)
        .map(a -> a.getType() + ":" + a.getDescription())
        .reduce((a, b) -> a + " | " + b)
        .orElse("");
  }
}
