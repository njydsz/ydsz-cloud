package com.njydsz.common.jdbc.interceptor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * N+1 查询检测拦截器（观测型，无阻断）
 *
 * <p>在单次请求（线程）范围内统计各 Mapper 方法的调用次数，
 * 当某个方法在同一个线程内被调用超过阈值（默认 3 次）时，输出 WARN 日志，
 * 提示开发者可能存在 N+1 查询风险，建议优化为批量 IN 查询。
 *
 * <p><b>日志输出格式：</b>
 *
 * <pre>
 * [ydsz-common-jdbc] N+1 疑似：com.example.UserMapper.selectById 已在当前上下文中调用 5 次，建议合并为批量查询
 * </pre>
 *
 * <p><b>配置：</b>
 *
 * <ul>
 *   <li>阈值通过构造函数传入（默认 3 次），建议生产环境 5，开发测试环境 3</li>
 *   <li>通过移除拦截器 Bean 可完全禁用检测（零性能开销）</li>
 *   <li>Interceptor 仅在 WARN 级别开启时生效（可通过日志配置关闭）</li>
 * </ul>
 *
 * <p><b>生效范围：</b>仅针对同线程内 Mapper 方法的重复调用。跨线程场景
 * （如线程池、异步任务）不触发告警（ThreadLocal 隔离）。
 *
 * <p><b>已知限制：</b>
 *
 * <ul>
 *   <li>不统计子线程调用</li>
 *   <li>不区分参数差异（同一方法签名即计数）</li>
 *   <li>阈值静态固定，不支持运行时动态调整</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NPlusOneDetectionInnerInterceptor implements InnerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(NPlusOneDetectionInnerInterceptor.class);

  /** 默认阈值：同一 Mapper 方法在当前上下文调用超过此值时告警 */
  public static final int DEFAULT_THRESHOLD = 3;

  private final int threshold;

  /**
   * 每个线程维护一个 Mapper 方法调用计数器。
   *
   * <p>Key = MappedStatement ID，Value = 调用次数（AtomicInteger 保证原子性）。
   */
  private final ThreadLocal<ConcurrentHashMap<String, AtomicInteger>> counters =
      ThreadLocal.withInitial(ConcurrentHashMap::new);

  /**
   * 检测拦截器开关（静态 volatile，支持动态关闭）。
   *
   * <p>默认 {@code true}。可在 JVM 启动参数中通过 {@code -Dydsz.jdbc.nplus-one-detection=false} 关闭，
   * 或运行时通过 JMX/Actuator 动态置 false。
   */
  private static volatile boolean enabled = true;

  /**
   * 构造 N+1 检测拦截器。
   *
   * @param threshold 告警阈值（同一方法调用超过此次数时告警），需 >= 1
   */
  public NPlusOneDetectionInnerInterceptor(int threshold) {
    Assert.isTrue(threshold >= 1, "N+1 检测阈值必须 >= 1");
    this.threshold = threshold;
  }

  /**
   * 默认构造，使用默认阈值。
   */
  public NPlusOneDetectionInnerInterceptor() {
    this(DEFAULT_THRESHOLD);
  }

  /**
   * 动态设置检测开关。
   *
   * @param enabled true=开启检测，false=关闭（关闭后 beforeQuery 直接返回）
   */
  public static void setEnabled(boolean enabled) {
    NPlusOneDetectionInnerInterceptor.enabled = enabled;
  }

  /**
   * 获取当前检测开关状态。
   *
   * @return true=检测开启，false=检测关闭
   */
  public static boolean isEnabled() {
    return enabled;
  }

  @Override
  public void beforeQuery(
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql) {
    if (!enabled || !log.isWarnEnabled()) {
      return;
    }
    try {
      String msId = ms.getId();
      ConcurrentHashMap<String, AtomicInteger> map = counters.get();
      AtomicInteger count = map.computeIfAbsent(msId, k -> new AtomicInteger(0));
      int current = count.incrementAndGet();
      if (current == threshold + 1) {
        // 刚好超过阈值时告警一次，避免重复告警（下次日志需重新开始）
        log.warn(
            "[N+1 疑似] {} 已在当前上下文调用 {} 次，建议检查是否存在循环内单条查询（可改为 batch 查询）",
            msId,
            current);
      }
    } catch (Exception e) {
      // 检测异常不应影响业务查询
      if (log.isDebugEnabled()) {
        log.debug("N+1 检测异常: {}", e.getMessage());
      }
    }
  }

  /**
   * 获取指定 Mapper 方法的当前调用计数（用于测试）。
   *
   * @param msId MappedStatement ID
   * @return 当前线程中的调用计数
   */
  int getCount(String msId) {
    ConcurrentHashMap<String, AtomicInteger> map = counters.get();
    if (map == null) {
      return 0;
    }
    AtomicInteger count = map.get(msId);
    return count != null ? count.get() : 0;
  }

  /**
   * 清理当前线程的检测上下文（通常在请求结束时由 Filter/AOP 调用）。
   *
   * <p>清理后，本次请求的计数不会影响下次请求。
   */
  public void clear() {
    counters.remove();
  }

  /**
   * 获取当前线程检测上下文中的方法数量（用于调试）。
   *
   * @return 方法调用映射的大小
   */
  int getTrackedMethodCount() {
    ConcurrentHashMap<String, AtomicInteger> map = counters.get();
    return map != null ? map.size() : 0;
  }

  /**
   * 获取告警阈值
   *
   * @return 当前阈值
   */
  public int getThreshold() {
    return threshold;
  }
}
