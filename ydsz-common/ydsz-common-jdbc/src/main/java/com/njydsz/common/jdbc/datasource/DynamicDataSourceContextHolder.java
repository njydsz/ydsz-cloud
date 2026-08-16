package com.njydsz.common.jdbc.datasource;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.core.NamedThreadLocal;

/**
 * 动态数据源上下文持有者
 *
 * <p>使用 ThreadLocal 存储当前线程的数据源名称，支持嵌套切换（栈式管理）。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 切换数据源
 * DynamicDataSourceContextHolder.push("slave");
 * try {
 *     // 执行数据库操作
 * } finally {
 *     // 恢复上一层数据源
 *     DynamicDataSourceContextHolder.poll();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class DynamicDataSourceContextHolder {

  private static final ThreadLocal<Deque<String>> CONTEXT_HOLDER =
      new NamedThreadLocal<>("Dynamic DataSource Context") {
        @Override
        protected Deque<String> initialValue() {
          return new ArrayDeque<>();
        }
      };

  private DynamicDataSourceContextHolder() {}

  /**
   * 压入数据源名称（支持嵌套切换）
   *
   * @param ds 数据源名称
   */
  public static void push(String ds) {
    CONTEXT_HOLDER.get().push(ds);
  }

  /**
   * 弹出当前数据源名称（恢复上一层）
   *
   * @return 弹出的数据源名称，栈为空时返回 null
   */
  public static String poll() {
    Deque<String> deque = CONTEXT_HOLDER.get();
    if (deque.isEmpty()) {
      return null;
    }
    return deque.pop();
  }

  /**
   * 获取当前数据源名称
   *
   * @return 当前数据源名称，未设置时返回 null
   */
  public static String peek() {
    Deque<String> deque = CONTEXT_HOLDER.get();
    return deque.peek();
  }

  /** 清除当前线程的数据源上下文 */
  public static void clear() {
    CONTEXT_HOLDER.remove();
  }
}
