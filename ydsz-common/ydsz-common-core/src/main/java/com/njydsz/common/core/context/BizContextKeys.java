package com.njydsz.common.core.context;

/**
 * 业务级上下文键名常量。
 *
 * <p>承载认证、租户、列权限、审计、缓存等业务域通用上下文键名。 这类键名已从 {@link RequestContext} 下沉到此专用类，使 core 模块
 * 保持对业务语义的零直接引用（仅通过 {@code BizContextKeys} 常量名 引用自身的 key 字符串，不引入业务类型依赖）。
 *
 * <p>业务 Filter（认证/数据权限/审计）应在入口处往 {@link RequestContext} 写这些 key，后续业务代码通过此常量类引用（避免硬编码字符串）。
 *
 * <p><b>注意：</b>core 模块不持有这些 key 对应的值类型 —— 值类型 （如 {@code AuthInfo}、{@code TenantContext}）定义在各自业务模块，
 * core 仅提供 String 键名常量。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RequestContext
 */
public final class BizContextKeys {

  private BizContextKeys() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ==================== 认证域 ====================

  /** 当前认证信息键名（认证 Filter 写入，业务模块按自身 AuthInfo 类型强转）。 */
  public static final String KEY_AUTH_INFO = "authInfo";

  /** 当前登录用户键名。 */
  public static final String KEY_LOGIN_USER = "loginUser";

  // ==================== 租户域 ====================

  /** 租户上下文键名。 */
  public static final String KEY_TENANT_CONTEXT = "tenantContext";

  // ==================== 数据权限域 ====================

  /** 列权限信息键名。 */
  public static final String KEY_COLUMN_PERMISSION = "columnPermission";

  /** 虚拟请求头键名（数据权限相关 header 的透传容器，如 X-Data-Scope）。 */
  public static final String KEY_EXTRA_HEADERS = "extraHeaders";

  // ==================== 审计域 ====================

  /** 审计数据键名。 */
  public static final String KEY_AUDIT_DATA = "auditData";

  // ==================== HTTP 请求 ====================

  /** HTTP 请求对象快照键名（已由 {@link RequestSnapshot} 替代持有的原生 HttpServletRequest）。 */
  public static final String KEY_HTTP_REQUEST = "httpRequest";

  // ==================== 缓存域 ====================

  /** 请求级用户信息缓存键名（与通用上下文分离，不跨线程传播）。 */
  public static final String KEY_CACHED_USER_INFO_MAP = "cachedUserInfoMap";
}
