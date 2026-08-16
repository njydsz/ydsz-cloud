package com.njydsz.common.tenant.feign;

import org.springframework.util.StringUtils;

import com.njydsz.common.tenant.config.TenantProperties.TenantField;

/**
 * Feign / WebFilter 跨服务 header 契约（共享解析规则）。
 *
 * <p>确保 {@code TenantContextFeignInterceptor}（写入端）与 {@code TenantContextWebFilter}（读取端）对同一字段使用完全一致的
 * header 名称 和 claim 回退优先级。
 *
 * <p><b>header 名计算规则：</b>
 *
 * <ol>
 *   <li>字段显式配置了 {@code header} → 使用显式值（如 {@code X-Company-Id}）
 *   <li>否则 → {@code X-Tenant-{fieldKey}}（fieldKey = claim 名或列名）
 * </ol>
 *
 * <p><b>effecitiveClaim（值来源 key）：</b>
 *
 * <ul>
 *   <li>字段配置了 {@code claim} → 使用 claim 名（JWT 读取 + Map key）
 *   <li>否则 → 使用 {@code column} 名（Map key）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.10.0
 */
public final class TenantHeaderContract {

  public static final String HEADER_PREFIX = "X-Tenant-";

  private TenantHeaderContract() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 计算字段在 HTTP header 中使用的名称。
   *
   * <p>Feign 写入与 WebFilter 读取共用此方法，确保两端一致。
   *
   * @param field 租户字段配置
   * @param fieldKey 字段在上下文 Map 中的 key（来自 {@link #effectiveKey}）
   * @return HTTP header 名称（非空）
   */
  public static String resolveHeaderName(TenantField field, String fieldKey) {
    if (StringUtils.hasText(field.getHeader())) {
      return field.getHeader();
    }
    return HEADER_PREFIX + fieldKey;
  }

  /**
   * 计算字段的"有效 key"（用于上下文 Map 存储和 header 命名回退）。
   *
   * <p>优先使用 claim 名（JWT 友好），否则回退到列名。
   *
   * @param field 租户字段配置
   * @return 非空 key
   */
  public static String effectiveKey(TenantField field) {
    return StringUtils.hasText(field.getClaim()) ? field.getClaim() : field.getColumn();
  }

  /**
   * 判断字段是否有有效的值来源（claim 或 header 至少一个非空）。
   *
   * @param field 租户字段配置
   * @return true=可解析
   */
  public static boolean hasValueSource(TenantField field) {
    return StringUtils.hasText(field.getClaim()) || StringUtils.hasText(field.getHeader());
  }

  /**
   * 获取主租户 ID header 名称（兼容 DataPermission 模块常量）。
   *
   * @return 主租户 ID header 名
   */
  public static String primaryTenantIdHeader() {
    return HEADER_PREFIX + "Id";
  }
}
