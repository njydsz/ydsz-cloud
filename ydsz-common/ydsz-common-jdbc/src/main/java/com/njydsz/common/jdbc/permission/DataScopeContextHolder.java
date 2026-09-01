package com.njydsz.common.jdbc.permission;

/**
 * 数据权限上下文 ThreadLocal 持有器。
 *
 * <p>由 {@code ydsz-common-auth} 的 {@code AuthRowPermissionAspect} 在执行数据权限解析后写入， 供 {@link
 * DataPermissionContextResolver} 在 SQL 拦截器中优先读取， 实现注解层到 SQL 拦截层的直接数据传递，避免经过 HTTP Header
 * 反序列化的性能开销与精度损失。
 *
 * <p><b>生命周期：</b>请求级别。切面在 {@code try-finally} 块中确保清除， 即使发生异常也不会污染后续请求。
 *
 * <p><b>使用示例（切面侧）：</b>
 *
 * <pre>{@code
 * DataScopeContextHolder.set(context);
 * try {
 *     return joinPoint.proceed();
 * } finally {
 *     DataScopeContextHolder.remove();
 * }
 * }</pre>
 *
 * <p><b>使用示例（解析器侧）：</b>
 *
 * <pre>{@code
 * DataPermissionContext context = DataScopeContextHolder.get();
 * if (context != null) {
 *     return context;
 * }
 * // 降级到 HTTP Header 解析
 * return resolveFromHeaders();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DataPermissionContextResolver
 * @see DataPermissionContext
 */
public final class DataScopeContextHolder {

  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已通过 clear()/remove() 在使用后清理（云顶规范 15.1）
  private static final ThreadLocal<DataPermissionContext> CONTEXT_HOLDER = new ThreadLocal<>();
  // CHECKSTYLE.ON: RegexpSinglelineJava

  private DataScopeContextHolder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 设置当前线程的数据权限上下文。
   *
   * @param context 数据权限上下文，可为 {@code null}
   */
  public static void set(DataPermissionContext context) {
    CONTEXT_HOLDER.set(context);
  }

  /**
   * 获取当前线程的数据权限上下文。
   *
   * @return 数据权限上下文，未设置时返回 {@code null}
   */
  public static DataPermissionContext get() {
    return CONTEXT_HOLDER.get();
  }

  /**
   * 清除当前线程的数据权限上下文。
   *
   * <p>应在 {@code finally} 块中调用，防止 ThreadLocal 在线程池复用场景下泄漏。
   */
  public static void remove() {
    CONTEXT_HOLDER.remove();
  }
}
