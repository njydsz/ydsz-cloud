package com.njydsz.common.core.context;

/**
 * 租户上下文持有者 — 全模块唯一的类型安全读写入口。
 *
 * <p>基于 {@link ContextKey} 提供编译期类型保证，统一替代旧双路径。
 *
 * <p><b>迁移说明：</b>P2-3 从 {@code com.njydsz.common.tenant.TenantContextHolder} 下沉至 common-core，
 * 以打破 common-cache ↔ common-tenant 的循环依赖。原有包路径保留废弃转发声明，现有 import 仍可用。
 *
 * <p><b>唯一写入口：</b>{@link #set(TenantContext)}。
 *
 * <p><b>读入口：</b>{@link #get()}、{@link #getTenantId()}、{@link #isSuperAdmin()}、{@link #isSkipIsolation()}。
 *
 * <p><b>生命周期：</b>请求结束时必须调用 {@link #clear()}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TenantContextHolder {

  /** 类型安全键 */
  @SuppressWarnings("java:S1075")
  public static final ContextKey<TenantContext> KEY =
      ContextKey.of(BizContextKeys.KEY_TENANT_CONTEXT, TenantContext.class);

  /** 保护构造器允许 common-tenant 子类转发 */
  protected TenantContextHolder() {}

  /**
   * 设置租户上下文（唯一写入口）。
   *
   * @param context 租户上下文，传入 {@code null} 等同于 {@link #clear()}
   */
  public static void set(TenantContext context) {
    if (context == null) {
      clear();
      return;
    }
    RequestContext.put(KEY.key(), context);
  }

  /**
   * 获取租户上下文（类型安全，无需强转）。
   *
   * @return 当前租户上下文，不存在返回 {@code null}
   */
  public static TenantContext get() {
    Object value = RequestContext.get(KEY.key());
    return KEY.type().isInstance(value) ? KEY.type().cast(value) : null;
  }

  /**
   * 获取主租户 ID（从上下文派生，非独立存储）。
   *
   * @return 租户 ID，上下文不存在返回 {@code null}
   */
  public static String getTenantId() {
    TenantContext ctx = get();
    return ctx != null ? ctx.getTenantId() : null;
  }

  /**
   * 是否已设置租户上下文。
   *
   * @return true=已设置
   */
  public static boolean isPresent() {
    return RequestContext.has(KEY);
  }

  /**
   * 当前租户是否跳过隔离。
   *
   * @return true=跳过隔离
   */
  public static boolean isSkipIsolation() {
    TenantContext ctx = get();
    return ctx != null && ctx.isSkipIsolation();
  }

  /**
   * 当前租户是否为超级管理员。
   *
   * @return true=超级管理员
   */
  public static boolean isSuperAdmin() {
    TenantContext ctx = get();
    return ctx != null && ctx.isSuperAdmin();
  }

  /**
   * 是否为系统租户（定时任务/MQ Consumer/内部调用）。
   *
   * @return true=系统租户
   */
  public static boolean isSystemTenant() {
    TenantContext ctx = get();
    return ctx != null && ctx.isSystemTenant();
  }

  /** 清除租户上下文。 */
  public static void clear() {
    RequestContext.remove(KEY.key());
  }

  /**
   * 获取当前快照（用于异步传播）。
   *
   * @return 上下文快照，不存在返回 {@code null}
   */
  public static TenantContext snapshot() {
    TenantContext ctx = get();
    return ctx != null ? ctx.snapshot() : null;
  }

  /**
   * 在租户上下文中执行逻辑，执行后自动清理。
   *
   * <p>用于无用户上下文的场景（定时任务、MQ Consumer）。
   *
   * @param context 预设的租户上下文
   * @param runnable 待执行逻辑
   */
  public static void runWithContext(TenantContext context, Runnable runnable) {
    set(context);
    try {
      runnable.run();
    } finally {
      clear();
    }
  }
}
