package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.concurrent.Callable;

import org.springframework.context.i18n.LocaleContextHolder;

/**
 * i18n 上下文异步传播器（L2 工具类）
 *
 * <p>Spring 的 {@link LocaleContextHolder} 将 Locale 绑定到当前线程的 ThreadLocal，当代码切换到子线程（{@code
 * CompletableFuture}、{@code @Async}、线程池）时，子线程无法自动继承父线程的 Locale，导致 i18n 翻译回退到系统默认语言。
 *
 * <p>本类解决上述问题：提供 {@link #wrap(Runnable, Locale)} 和 {@link #wrap(Callable, Locale)} 两个工厂方法，
 * 将任意回调包装为"进入前自动 setLocale、最终自动恢复"的装饰器。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 并行处理时保持当前请求的 Locale
 * Locale requestLocale = Locales.current();
 * CompletableFuture&lt;String&gt; future = CompletableFuture.supplyAsync(
 *     I18nContextPropagator.wrap(() -> i18n.resolve("report.title"), requestLocale));
 *
 * // Spring @Async 场景
 * I18nContextPropagator.wrap(this::generateReportInCurrentLocale, requestLocale).call();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see org.springframework.core.task.TaskDecorator
 */
public final class I18nContextPropagator {

  private I18nContextPropagator() {
    // 工具类禁止实例化
  }

  /**
   * 包装 Runnable，在执行前切换至目标 Locale，执行后恢复原 Locale。
   *
   * @param task 要执行的任务（不可为 null）
   * @param locale 执行期间使用的 Locale；传 null 则使用 {@link Locale#ROOT}
   * @return 包装后的 Runnable，可直接提交给线程池
   */
  public static Runnable wrap(Runnable task, Locale locale) {
    if (task == null) {
      throw new IllegalArgumentException("task must not be null");
    }
    return () -> {
      Locale previous = LocaleContextHolder.getLocale();
      try {
        LocaleContextHolder.setLocale(locale != null ? locale : Locale.ROOT);
        task.run();
      } finally {
        LocaleContextHolder.setLocale(previous);
      }
    };
  }

  /**
   * 包装 Callable，在执行前切换至目标 Locale，执行后恢复原 Locale。
   *
   * <p>注意：被包装的 Callable 抛出的任何异常都会原样传播给调用方，finally 块保证兜底恢复。
   *
   * @param task 要执行的任务（不可为 null）
   * @param locale 执行期间使用的 Locale；传 null 则使用 {@link Locale#ROOT}
   * @param <V> 返回值类型
   * @return 包装后的 Callable
   */
  public static <V> Callable<V> wrap(Callable<V> task, Locale locale) {
    if (task == null) {
      throw new IllegalArgumentException("task must not be null");
    }
    return () -> {
      Locale previous = LocaleContextHolder.getLocale();
      try {
        LocaleContextHolder.setLocale(locale != null ? locale : Locale.ROOT);
        return task.call();
      } finally {
        LocaleContextHolder.setLocale(previous);
      }
    };
  }

  /**
   * 包装当前请求 Locale 的 Runnable（快捷方法，等同于 {@code wrap(task, Locales.current())}）。
   *
   * @param task 要执行的任务（不可为 null）
   * @return 包装后的 Runnable，继承当前线程 Locale
   */
  public static Runnable wrapWithCurrentLocale(Runnable task) {
    return wrap(task, Locales.current());
  }

  /**
   * 包装当前请求 Locale 的 Callable（快捷方法，等同于 {@code wrap(task, Locales.current())}）。
   *
   * @param task 要执行的任务（不可为 null）
   * @param <V> 返回值类型
   * @return 包装后的 Callable，继承当前线程 Locale
   */
  public static <V> Callable<V> wrapWithCurrentLocale(Callable<V> task) {
    return wrap(task, Locales.current());
  }
}
