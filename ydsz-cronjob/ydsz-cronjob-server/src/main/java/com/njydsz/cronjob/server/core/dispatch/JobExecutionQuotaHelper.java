package com.njydsz.cronjob.server.core.dispatch;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行配额管理辅助类。
 *
 * <p>封装租户并发配额、日执行配额的检查与记录逻辑， 遵循云顶编码规范，将 {@link DefaultTaskDispatcher} 中的配额管理职责独立出来，
 * 降低主类复杂度，提升代码可维护性。
 *
 * <h3>职责范围</h3>
 *
 * <ul>
 *   <li>租户并发配额检查
 *   <li>租户日执行配额检查
 *   <li>任务执行开始/结束的配额记录
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobExecutionQuotaHelper {

  private final ObjectProvider<TenantQuotaService> tenantQuotaServiceProvider;

  /**
   * 构造配额管理辅助类。
   *
   * @param tenantQuotaServiceProvider 租户配额服务提供者
   */
  public JobExecutionQuotaHelper(ObjectProvider<TenantQuotaService> tenantQuotaServiceProvider) {
    this.tenantQuotaServiceProvider = tenantQuotaServiceProvider;
  }

  /**
   * 检查租户并发配额 + 日执行配额。
   *
   * <p>仅对 CRON/RETRY/DEPENDENT/MISFIRED 触发类型调用（MANUAL 不检查）。 配额超限时抛 {@link SysException}，任务不会被派发。
   * 配额服务不可用时降级放行（不影响任务执行）。
   *
   * @param tenantId 租户 ID
   * @param jobKey 任务 KEY（用于日志）
   */
  public void checkExecutionQuota(String tenantId, String jobKey) {
    TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
    if (quotaService == null) {
      return;
    }
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }
    try {
      quotaService.checkConcurrentQuota(tenantId);
      quotaService.checkDailyExecutionQuota(tenantId);
    } catch (SysException e) {
      // 配额超限，记录日志后重新抛出
      log.warn(
          "[Dispatcher] 租户配额超限, 拒绝派发: key={} tenant={} code={}",
          jobKey,
          tenantId,
          e.getCode());
      throw e;
    } catch (Exception e) {
      // 配额服务异常，降级放行
      log.warn(
          "[Dispatcher] 配额检查异常, 降级放行: key={} tenant={} reason={}",
          jobKey,
          tenantId,
          e.getMessage());
    }
  }

  /**
   * 记录任务执行开始（INCR 并发计数器 + 日执行计数器）。
   *
   * <p>TenantQuotaService 不可用时跳过；内部有容错，不会抛异常。
   *
   * @param tenantId 租户 ID
   */
  public void recordExecutionStart(String tenantId) {
    TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
    if (quotaService == null || tenantId == null || tenantId.isBlank()) {
      return;
    }
    try {
      quotaService.recordExecutionStart(tenantId);
    } catch (Exception e) {
      log.debug(
          "[Dispatcher] recordExecutionStart 失败(不影响主流程): tenant={} reason={}",
          tenantId,
          e.getMessage());
    }
  }

  /**
   * 记录任务执行结束（DECR 并发计数器）。
   *
   * <p>TenantQuotaService 不可用时跳过；内部有容错，不会抛异常。
   *
   * @param tenantId 租户 ID
   */
  public void recordExecutionEnd(String tenantId) {
    TenantQuotaService quotaService = tenantQuotaServiceProvider.getIfAvailable();
    if (quotaService == null || tenantId == null || tenantId.isBlank()) {
      return;
    }
    try {
      quotaService.recordExecutionEnd(tenantId);
    } catch (Exception e) {
      log.debug(
          "[Dispatcher] recordExecutionEnd 失败(不影响主流程): tenant={} reason={}",
          tenantId,
          e.getMessage());
    }
  }
}
