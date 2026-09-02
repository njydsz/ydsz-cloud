package com.njydsz.common.tenant.feign;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties.TenantField;

/**
 * Feign 请求拦截器：跨服务透传全部租户字段。
 *
 * <p>将 {@link TenantContext} 中的所有字段透传为 HTTP header， 下游服务的 {@code TenantContextWebFilter} 从 header
 * 恢复全部字段。
 *
 * <p><b>header 名计算：</b>使用 {@link TenantHeaderContract} 共享规则， 确保与 WebFilter 读取端一致。
 *
 * <p>透传规则：
 *
 * <ul>
 *   <li>单值字段 → header 值为 String
 *   <li>多值字段 → header 值为逗号分隔 String（如 "dept_001,dept_002"）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class TenantContextFeignInterceptor implements RequestInterceptor {

  private final Map<String, TenantField> fieldByKey;

  /**
   * 构造租户上下文 Feign 拦截器。
   *
   * @param fields 激活的租户字段列表（来自 TenantProperties）
   */
  public TenantContextFeignInterceptor(List<TenantField> fields) {
    this.fieldByKey = new HashMap<>(16);
    if (fields != null) {
      for (TenantField field : fields) {
        String key = TenantHeaderContract.effectiveKey(field);
        fieldByKey.put(key, field);
      }
    }
  }

  @Override
  public void apply(RequestTemplate template) {
    TenantContext context = TenantContextHolder.get();
    if (context == null || context.isSkipIsolation() || context.getTenantId() == null) {
      return;
    }

    // 注入主租户 ID
    template.header(TenantHeaderContract.primaryTenantIdHeader(), context.getTenantId());

    // 透传全部字段
    Map<String, Object> fields = context.getFields();
    if (fields != null && !fields.isEmpty()) {
      for (Map.Entry<String, Object> entry : fields.entrySet()) {
        String key = entry.getKey();
        // 跳过 tenantId（已作为 X-Tenant-Id 透传）
        if ("tenantId".equals(key)) {
          continue;
        }
        Object value = entry.getValue();

        // 通过共享契约计算 header 名（显式 header 优先，否则 X-Tenant-{key}）
        TenantField field = fieldByKey.get(key);
        String headerName =
            TenantHeaderContract.resolveHeaderName(
                field != null ? field : new TenantField(key), key);

        if (value instanceof String s) {
          template.header(headerName, s);
        } else if (value instanceof List<?> list) {
          // 多值 → 逗号分隔
          String joined = joinList(list);
          if (joined != null) {
            template.header(headerName, joined);
          }
        }
      }
    }
  }

  private String joinList(List<?> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(list.get(i));
    }
    return sb.toString();
  }
}
