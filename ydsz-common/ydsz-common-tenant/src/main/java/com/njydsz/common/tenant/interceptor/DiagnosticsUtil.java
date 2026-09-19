package com.njydsz.common.tenant.interceptor;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.jdbc.exception.TenantIsolationException.Diagnostics;

/**
 * 租户隔离诊断信息收集工具。
 *
 * <p>在 fail-closed 触发时自动收集运行时上下文线索，封装为 {@link Diagnostics} 附加到异常消息末尾。
 *
 * <p>收集项包括：
 *
 * <ul>
 *   <li>thread — 当前线程名（判断是否异步/线程池场景）
 *   <li>uri — 当前请求 URI（如有）
 *   <li>hasTenantContext — TenantContext 是否已设置
 *   <li>tenantId — 当前租户 ID（如有）
 *   <li>requestSnapshot — HTTP 请求快照是否存在（判断 Filter 是否已执行）
 * </ul>
 *
 * <p>诊断信息仅用于日志排查，不影响异常码与 HTTP 状态码。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
final class DiagnosticsUtil {

  private DiagnosticsUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 收集当前运行时诊断信息。
   *
   * @param reason fail-closed 触发原因标记
   * @return 诊断信息，不会为 null
   */
  static Diagnostics collect(String reason) {
    Diagnostics diagnostics = Diagnostics.of().put("reason", reason);

    // 当前线程名（判断是否异步/定时任务场景）
    diagnostics.put("thread", Thread.currentThread().getName());

    // TenantContext 状态
    diagnostics.put("hasTenantContext", TenantContextHolder.isPresent());
    diagnostics.put("tenantId", TenantContextHolder.getTenantId());

    // HTTP 请求上下文状态（判断 Web Filter 是否已执行）
    boolean hasSnapshot = RequestContext.has(BizContextKeys.KEY_HTTP_REQUEST);
    diagnostics.put("hasRequestSnapshot", hasSnapshot);

    // 请求 URI（如有）
    RequestSnapshot snapshot = RequestContext.getRequestSnapshot();
    if (snapshot != null) {
      diagnostics.put("uri", snapshot.getRequestUri());
    }

    return diagnostics;
  }
}
