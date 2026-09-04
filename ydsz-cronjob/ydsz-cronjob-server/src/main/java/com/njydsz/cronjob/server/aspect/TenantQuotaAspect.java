package com.njydsz.cronjob.server.aspect;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.cronjob.server.annotation.TenantQuotaCheck.QuotaType;
import com.njydsz.cronjob.server.annotation.TenantQuotaCheck;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

/**
 * 租户配额检查切面（P0-5）。
 *
 * <p>拦截 {@link TenantQuotaCheck} 注解标注的方法，在方法执行前自动进行租户配额校验。 支持三种配额类型：任务数配额、并发配额、日执行量配额。
 *
 * <p>租户 ID 提取逻辑：
 *
 * <ol>
 *   <li>优先从注解指定的方法参数名提取（{@code tenantIdParam}）
 *   <li>其次从方法参数中查找名为 {@code tenantId} 的参数
 *   <li>最后从 {@link com.njydsz.common.tenant.TenantContextHolder} 获取当前租户 ID
 * </ol>
 *
 * <p>配额检查失败时抛出 {@link com.njydsz.common.exception.custom.SysException}， 错误码为 {@code
 * QUOTA_EXCEEDED}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantQuotaAspect {

  private final TenantQuotaService tenantQuotaService;

  /**
   * 在方法执行前进行配额检查。
   *
   * @param joinPoint 连接点
   * @param annotation 配额检查注解
   */
  @Before("@annotation(annotation)")
  public void checkQuota(JoinPoint joinPoint, TenantQuotaCheck annotation) {
    String tenantId = extractTenantId(joinPoint, annotation);
    if (tenantId == null || tenantId.isBlank()) {
      log.warn(
          "[TenantQuota] 租户 ID 为空，跳过配额检查: method={}", joinPoint.getSignature().toShortString());
      return;
    }

    QuotaType quotaType = annotation.type();
    log.debug(
        "[TenantQuota] 开始配额检查: tenant={} type={} method={}",
        tenantId,
        quotaType,
        joinPoint.getSignature().toShortString());

    switch (quotaType) {
      case JOB:
        tenantQuotaService.checkJobQuota(tenantId);
        break;
      case CONCURRENT:
        tenantQuotaService.checkConcurrentQuota(tenantId);
        break;
      case DAILY_EXECUTION:
        tenantQuotaService.checkDailyExecutionQuota(tenantId);
        break;
      default:
        log.warn("[TenantQuota] 未知的配额类型: {}", quotaType);
    }
  }

  /**
   * 从方法参数或 TenantContext 中提取租户 ID。
   *
   * @param joinPoint 连接点
   * @param annotation 配额检查注解
   * @return 租户 ID
   */
  private String extractTenantId(JoinPoint joinPoint, TenantQuotaCheck annotation) {
    // 1. 优先从注解指定的参数名提取
    String paramName = annotation.tenantIdParam();
    if (paramName != null && !paramName.isBlank()) {
      return extractParamByName(joinPoint, paramName);
    }

    // 2. 从方法参数中查找名为 "tenantId" 的参数
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] args = joinPoint.getArgs();

    for (int i = 0; i < parameters.length; i++) {
      if ("tenantId".equals(parameters[i].getName())) {
        Object value = args[i];
        if (value instanceof String) {
          return (String) value;
        }
      }
    }

    // 3. 从 TenantContext 获取
    return TenantContextHolder.getTenantId();
  }

  /**
   * 按参数名提取方法参数值。
   *
   * @param joinPoint 连接点
   * @param paramName 参数名
   * @return 参数值
   */
  private String extractParamByName(JoinPoint joinPoint, String paramName) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] args = joinPoint.getArgs();

    for (int i = 0; i < parameters.length; i++) {
      if (paramName.equals(parameters[i].getName())) {
        Object value = args[i];
        if (value instanceof String) {
          return (String) value;
        }
      }
    }
    return null;
  }
}
