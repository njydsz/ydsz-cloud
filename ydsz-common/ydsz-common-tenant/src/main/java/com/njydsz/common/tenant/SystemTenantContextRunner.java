package com.njydsz.common.tenant;

import java.util.concurrent.Callable;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 系统租户上下文执行器。
 *
 * <p>定时任务、MQ Consumer、{@code @Scheduled} 等无用户上下文的场景， 通过此工具类注入系统租户上下文（tenantId = 配置的
 * system-tenant-id）。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Scheduled(cron = "0 0 2 * * ?")
 * public void scanJobs() {
 *     SystemTenantContextRunner.run(() -> {
 *         // 此处 TenantContextHolder.getTenantId() = systemTenantId
 *         jobScanner.scan();
 *     });
 * }
 *
 * // 有返回值
 * Result result = SystemTenantContextRunner.call(() -> service.query());
 * }</pre>
 *
 * <p>线程安全：{@code systemTenantId} 在启动时设置一次，运行时只读。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SystemTenantContextRunner {

  private static volatile String systemTenantId = "0";

  private SystemTenantContextRunner() {}

  /**
   * 初始化系统租户 ID（由 {@code TenantAutoConfiguration} 在启动时调用）。
   *
   * @param tenantId 系统租户 ID
   */
  public static void init(String tenantId) {
    if (tenantId != null && !tenantId.isEmpty()) {
      systemTenantId = tenantId;
    }
  }

  /**
   * 在系统租户上下文中执行 Runnable。
   *
   * @param runnable 待执行逻辑
   */
  public static void run(Runnable runnable) {
    applySystemTenant();
    try {
      runnable.run();
    } finally {
      clearTenant();
    }
  }

  /** 写入系统租户上下文（含 tenantId 同步）。 */
  private static void applySystemTenant() {
    TenantContextHolder.set(TenantContext.system(systemTenantId));
  }

  /** 清除租户上下文（对应 RequestContext 清理语义）。 */
  private static void clearTenant() {
    TenantContextHolder.clear();
  }

  /**
   * 在系统租户上下文中执行 Callable。
   *
   * @param callable 待执行逻辑
   * @param <T> 返回类型
   * @return 执行结果
   * @throws RuntimeException 包装后的异常
   */
  public static <T> T call(Callable<T> callable) {
    applySystemTenant();
    try {
      return callable.call();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(CoreExceptionCode.SYSTEM_ERROR, e);
    } finally {
      clearTenant();
    }
  }
}
