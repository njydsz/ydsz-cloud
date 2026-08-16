package com.njydsz.common.jdbc.permission;

import java.util.function.Supplier;
import org.springframework.core.NamedThreadLocal;

/**
 * 数据权限系统级绕过工具——用于非 Web 后台任务（定时批处理、MQ 消费）。
 *
 * <p>在无 HTTP Request 的场景下，{@link DataPermissionContextResolver#resolve()} 无法获取 请求头，导致 {@code
 * RowPermissionInnerInterceptor} 以 fail-closed 原则返回 {@code 1=0}，
 * 静默拒绝所有查询。此时可通过本工具显式声明"当前线程无需数据权限检查"， 使拦截器跳过权限条件追加。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 方式一：try-with-resources（推荐）
 * try (DataPermissionBypass.Scope scope = DataPermissionBypass.open()) {
 *     // 此处所有查询不追加数据权限条件
 *     List<Result> results = batchMapper.selectAll();
 * }
 *
 * // 方式二：callback 模式
 * List<Result> results = DataPermissionBypass.runWithoutCheck(() -> batchMapper.selectAll());
 *
 * // 方式三：手动清理
 * DataPermissionBypass.disable();
 * try {
 *     batchMapper.selectAll();
 * } finally {
 *     DataPermissionBypass.enable();
 * }
 * }</pre>
 *
 * <p><b>线程安全：</b>基于 {@link ThreadLocal} 实现，仅在当前线程生效。 异步子线程场景下，请在每个子线程入口处使用 {@link
 * #runWithoutCheck(Runnable)} 包裹，或使用 TTL（TransmittableThreadLocal）等线程池上下文传递方案。
 *
 * <p><b>与 {@link DataPermissionIgnore} 的区别：</b>
 *
 * <ul>
 *   <li>{@code @DataPermissionIgnore} 是注解，需标注在 Mapper 方法上，静态声明
 *   <li>{@code DataPermissionBypass} 是编程式 API，适用于运行时动态判断场景（如后台批处理）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.jdbc.interceptor.RowPermissionInnerInterceptor
 * @see DataPermissionIgnore
 */
public final class DataPermissionBypass {

  private DataPermissionBypass() {}

  /**
   * 绕过标志 ThreadLocal。
   *
   * <p>{@code true} 表示当前线程已禁用数据权限检查，拦截器应跳过条件追加。
   */
  private static final ThreadLocal<Boolean> BYPASS_FLAG =
      new NamedThreadLocal<>("DataPermission Bypass") {
        @Override
        protected Boolean initialValue() {
          return Boolean.FALSE;
        }
      };

  /**
   * 禁用当前线程的数据权限检查。
   *
   * <p>需在操作完成后调用 {@link #enable()} 恢复，或使用 try-with-resources 自动恢复。
   */
  public static void disable() {
    BYPASS_FLAG.set(Boolean.TRUE);
  }

  /** 恢复当前线程的数据权限检查（清除绕过标志）。 */
  public static void enable() {
    BYPASS_FLAG.remove();
  }

  /**
   * 检查当前线程是否设置了数据权限绕过标志。
   *
   * <p>供 {@code RowPermissionInnerInterceptor.beforePrepare()} 在拦截 SQL 前检查。
   *
   * @return 当前线程应绕过数据权限检查时返回 {@code true}
   */
  public static boolean isActive() {
    return Boolean.TRUE.equals(BYPASS_FLAG.get());
  }

  /**
   * 打开一个数据权限绕过作用域（try-with-resources 自动清理）。
   *
   * @return Scope 对象，关闭后恢复数据权限检查
   */
  public static Scope open() {
    disable();
    return new DefaultScope();
  }

  /**
   * 执行 {@code action} 期间禁用数据权限检查，执行完毕后自动恢复。
   *
   * <p>等效于：
   *
   * <pre>{@code
   * try (Scope scope = DataPermissionBypass.open()) {
   *     return action.get();
   * }
   * }</pre>
   *
   * @param action 需要绕过数据权限检查的操作
   * @param <T> 返回值类型
   * @return action 的返回值
   */
  public static <T> T runWithoutCheck(Supplier<T> action) {
    try (Scope scope = open()) {
      return action.get();
    }
  }

  /**
   * 执行 {@code action} 期间禁用数据权限检查，执行完毕后自动恢复（无返回值）。
   *
   * @param action 需要绕过数据权限检查的操作
   */
  public static void runWithoutCheck(Runnable action) {
    try (Scope scope = open()) {
      action.run();
    }
  }

  /** 数据权限绕过作用域接口——try-with-resources 的 AutoCloseable 抽象。 */
  public interface Scope extends AutoCloseable {

    /** 关闭作用域，恢复数据权限检查。 */
    @Override
    void close();
  }

  /** 默认 Scope 实现，关闭时恢复数据权限检查。 */
  private static class DefaultScope implements Scope {

    @Override
    public void close() {
      enable();
    }
  }
}
