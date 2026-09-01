package com.njydsz.common.tenant.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.njydsz.common.core.context.BizContextKeys;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.model.CurrentUser;
import com.njydsz.common.tenant.TenantContext;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.tenant.config.TenantProperties;
import com.njydsz.common.tenant.config.TenantProperties.TenantField;
import com.njydsz.common.tenant.feign.TenantHeaderContract;
import com.njydsz.common.tenant.metrics.TenantMetrics;

/**
 * 租户上下文 Web 过滤器。
 *
 * <p>在请求入口从 HTTP Header 解析全部配置的租户字段， 设置到 {@link TenantContextHolder} 和 MDC 日志上下文。
 *
 * <p><b>解析逻辑（逐字段）：</b>
 *
 * <ol>
 *   <li>HTTP header 取值（配置了 {@code header}）
 *   <li>JWT claim 取值（配置了 {@code claim} 且 JWT 可用时，注意：此方式 直接读取 AuthInfo（来自 ydsz-common-core），单元测试需
 *       mock 该静态方法）
 *   <li>多值字段（{@code multiValue=true}）→ 逗号分隔解析为 List
 * </ol>
 *
 * <p><b>安全清洗：</b>外部请求的 X-Tenant-* header 在网关层已被清洗， 此处 header 恢复仅在 JWT 不可用时触发（Feign 内部调用场景）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class TenantContextWebFilter implements Filter {

  private static final String MDC_TENANT_ID = "tenantId";

  private final TenantProperties properties;
  private final Set<String> anonUrls;
  private final List<TenantField> activeFields;
  private final TenantMetrics metrics;

  public TenantContextWebFilter(TenantProperties properties) {
    this(properties, null);
  }

  public TenantContextWebFilter(TenantProperties properties, TenantMetrics metrics) {
    this.properties = properties;
    this.anonUrls = properties.getNormalizedAnonUrls();
    this.activeFields = properties.getActiveTenantFields();
    this.metrics = metrics;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req;
    boolean contextSet = false;
    try {
      String requestUri = request.getRequestURI();

      // 1. 匿名 URL → 跳过隔离
      if (isAnonUrl(requestUri)) {
        setTenantContext(TenantContext.skip());
        contextSet = true;
        if (metrics != null) {
          metrics.incrementActiveContext();
        }
        chain.doFilter(req, res);
        return;
      }

      // 2. 逐字段解析值
      Map<String, Object> fields = new HashMap<>();
      String tenantId = null;

      for (TenantField field : activeFields) {
        Object value = resolveFieldValue(request, field);
        if (value != null) {
          // 有效 key = claim 优先，回退到列名（与 Feign 写入端一致）
          String fieldKey = TenantHeaderContract.effectiveKey(field);
          fields.put(fieldKey, value);

          // 第一个字段的值作为主 tenantId
          if (tenantId == null && value instanceof String s) {
            tenantId = s;
          }
        }
      }

      // 3. 设置上下文
      if (tenantId != null && !tenantId.isEmpty()) {
        boolean isSuperAdmin = properties.getSuperTenantId().equals(tenantId);
        TenantContext.Builder builder = TenantContext.builder(tenantId).superAdmin(isSuperAdmin);

        // SCHEMA 模式：设置 search_path
        if (properties.getMode() == TenantProperties.TenantMode.SCHEMA && !isSuperAdmin) {
          builder.schema("tenant_" + tenantId);
        }

        // 跨租户共享：附加可访问的源租户 ID
        List<String> sharedSources = properties.getTenantSharing().get(tenantId);
        if (sharedSources != null && !sharedSources.isEmpty()) {
          builder.sharedTenantIds(sharedSources);
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
          if (entry.getValue() instanceof String s) {
            builder.field(entry.getKey(), s);
          } else if (entry.getValue() instanceof List<?> list) {
            List<String> strList = new ArrayList<>(list.size());
            for (Object item : list) {
              if (item instanceof String s) {
                strList.add(s);
              }
            }
            builder.fieldValues(entry.getKey(), strList);
          }
        }
        setTenantContext(builder.build());
        contextSet = true;
        if (metrics != null) {
          metrics.incrementActiveContext();
        }

        MDC.put(MDC_TENANT_ID, tenantId);
      }
      // 无认证无跳过 → 不设置上下文，SQL 拦截器 fail-closed

      chain.doFilter(req, res);
    } finally {
      // 活跃上下文计数 -1（Gauge 观测，仅对设置了上下文的请求累计）
      if (contextSet && metrics != null) {
        metrics.decrementActiveContext();
      }
      clearTenantContext();
      MDC.remove(MDC_TENANT_ID);
    }
  }

  /**
   * 设置租户上下文到 RequestContext（含 tenantId 同步）。
   *
   * @param context 租户上下文
   */
  private static void setTenantContext(TenantContext context) {
    TenantContextHolder.set(context);
  }

  /** 清除租户上下文（对应 RequestContext 清理语义）。 */
  private static void clearTenantContext() {
    TenantContextHolder.clear();
  }

  /**
   * 解析单个租户字段的值。
   *
   * <p>优先从 JWT claim 取值，回退到 HTTP header。
   *
   * <p>header 名通过 {@link TenantHeaderContract#resolveHeaderName} 计算， 确保与 Feign 写入端使用同一规则。
   *
   * <p>多值字段用逗号分隔解析为 List。
   *
   * @param request HTTP 请求
   * @param field 租户字段配置
   * @return 解析后的值（String 或 List<String>），无值返回 null
   */
  private Object resolveFieldValue(HttpServletRequest request, TenantField field) {
    String value = null;

    // 优先从 JWT 取值（protected 方法，单元测试可覆盖）
    if (field.getClaim() != null && !field.getClaim().isEmpty()) {
      value = resolveClaimValue(field.getClaim());
    }

    // 回退到 HTTP header（使用与 Feign 写入端一致的 header 名）
    if ((value == null || value.isEmpty())) {
      String headerName =
          TenantHeaderContract.resolveHeaderName(field, TenantHeaderContract.effectiveKey(field));
      value = request.getHeader(headerName);
    }

    if (value == null || value.isEmpty()) {
      return null;
    }

    // 多值字段 → 逗号分隔解析
    if (field.isMultiValue()) {
      String[] parts = value.split(",");
      List<String> values = new ArrayList<>(parts.length);
      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          values.add(trimmed);
        }
      }
      return values.isEmpty() ? null : values;
    }

    return value;
  }

  /**
   * 从 JWT claim 解析值。
   *
   * <p>提取为 protected 方法以便单元测试中通过子类覆盖， 避免对 common-auth 的编译依赖。
   *
   * @param claim JWT claim 名
   * @return claim 值，不存在返回 null
   * @since 26.09.01
   */
  protected String resolveClaimValue(String claim) {
    if (claim == null || claim.isEmpty()) {
      return null;
    }
    Object authObj = RequestContext.get(BizContextKeys.KEY_AUTH_INFO);
    if (!(authObj instanceof CurrentUser auth)) {
      return null;
    }
    switch (claim) {
      case "tenantId":
        return auth.getTenantId();
      case "uniqueId":
      case "userId":
        return auth.getUniqueId();
      case "companyIds":
      case "deptIds":
      case "projectIds":
      case "regionIds":
        // 类型安全 SPI：通过 CurrentUser.getPermissionIds 获取权限 ID 集合（auth 模块实现覆盖）
        return invokeGetPermissionIds(auth, claim);
      default:
        return null;
    }
  }

  /**
   * 通过类型安全接口获取权限 ID 集合。
   *
   * <p>调用 {@link CurrentUser#getPermissionIds(String)}（auth 模块 {@code AuthInfo} 覆盖实现），
   * 替代早期基于反射拼方法名的脆弱实现。
   *
   * @param auth 认证信息对象（CurrentUser 契约）
   * @param claim claim 名（companyIds / deptIds / projectIds / regionIds）
   * @return 逗号拼接的 ID 集合；不可用时返回 null
   */
  private String invokeGetPermissionIds(CurrentUser auth, String claim) {
    try {
      Set<String> ids = auth.getPermissionIds(claim);
      if (ids != null && !ids.isEmpty()) {
        return String.join(",", ids.stream().map(Object::toString).toList());
      }
    } catch (Exception e) {
      // 静默降级，返回 null
      log.debug("[TenantContext] 权限 ID 获取失败，降级返回 null: {}", e.getMessage());
    }
    return null;
  }

  private boolean isAnonUrl(String requestUri) {
    if (requestUri == null || anonUrls.isEmpty()) {
      return false;
    }
    for (String anonUrl : anonUrls) {
      if (requestUri.startsWith(anonUrl)) {
        return true;
      }
    }
    return false;
  }
}
