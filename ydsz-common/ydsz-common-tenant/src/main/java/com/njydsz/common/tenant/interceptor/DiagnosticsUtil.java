package com.njydsz.common.tenant.interceptor;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.context.RequestSnapshot;
import com.njydsz.common.core.context.TenantContextHolder;

/**
 * 租户隔离诊断信息收集工具。
 *
 * <p>在 fail-closed 触发时自动收集运行时上下文线索，格式化为 {@code [key=value, ...]} 字符串，
 * 附加到异常消息末尾，便于从错误日志快速定位根因。
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
   * 收集当前运行时诊断信息并格式化为字符串。
   *
   * @param reason fail-closed 触发原因标记
   * @return 格式化后的诊断字符串（{@code [key=value, ...]}），不会为 null
   */
  static String collect(String reason) {
    Map<String, String> data = new LinkedHashMap<>(8);
    data.put("reason", reason);

    // 当前线程名（判断是否异步/定时任务场景）
    data.put("thread", Thread.currentThread().getName());

    // TenantContext 状态
    data.put("hasTenantContext", Boolean.toString(TenantContextHolder.isPresent()));
    data.put("tenantId", TenantContextHolder.getTenantId());

    // HTTP 请求上下文状态（判断 Web Filter 是否已执行）
    boolean hasSnapshot = RequestContext.has(BizContextKeys.KEY_HTTP_REQUEST);
    data.put("hasRequestSnapshot", Boolean.toString(hasSnapshot));

    // 请求 URI（如有）
    RequestSnapshot snapshot = RequestContext.getRequestSnapshot();
    if (snapshot != null) {
      data.put("uri", snapshot.getRequestUri());
    }

    return format(data);
  }

  private static String format(Map<String, String> data) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (Map.Entry<String, String> entry : data.entrySet()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue());
      first = false;
    }
    sb.append("]");
    return sb.toString();
  }
}
