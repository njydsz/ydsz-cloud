package com.njydsz.common.cache.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓存模块线程工厂（L1 自持，零内部模块依赖）。
 *
 * <p><b>存在原因（YDIZ-ARCH-L1-PURITY）</b>：ydsz-common-cache 属 L1 工具层，按云顶编码规范
 * §22.2「L1 工具纯度」要求<b>禁止依赖任何 L2+ 内部模块</b>。历史实现委托
 * {@code com.njydsz.common.thread.factory.InternalExecutorFactory}（L4），构成 L1→L4 反向依赖，
 * 且规范原文已将此写法列为违规示例。本工厂用纯 JDK API 复刻同等语义（命名线程 + 守护 +
 * 未捕获异常日志），使 cache 模块可解除对 common-thread 的 compile 依赖。
 *
 * <p><b>与 common-thread 的兼容</b>：如需将线程池纳入 {@code ThreadPoolRegistry} 统一监控，
 * 请通过 {@code ydzs-common-thread}（provided/optional）+ 反射调用
 * {@code ThreadPoolRegistry.register}，或通过 Spring 事件在业务侧注册，避免 L1 反向依赖。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
public final class CacheThreadFactory implements ThreadFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CacheThreadFactory.class);

  /** 线程名前缀（与 common-thread 的 InternalExecutorFactory 保持同名规则，便于日志检索） */
  private static final String THREAD_NAME_PREFIX = "ydsz-internal-cache-";

  /** 线程名中的池标识 */
  private final String poolName;

  /** 线程序号生成器 */
  private final AtomicInteger threadNumber = new AtomicInteger(1);

  /**
   * 构造缓存线程工厂。
   *
   * @param poolName 池名称（用于线程命名，如 "load-sync"）
   */
  public CacheThreadFactory(String poolName) {
    this.poolName = poolName == null ? "unnamed" : poolName;
  }

  /**
   * 创建命名线程。
   *
   * @param runnable 待执行任务
   * @return 已配置的线程（daemon、NORM_PRIORITY、带未捕获异常日志）
   */
  @Override
  public Thread newThread(Runnable runnable) {
    Thread thread = new Thread(runnable);
    thread.setName(THREAD_NAME_PREFIX + poolName + "-" + threadNumber.getAndIncrement());
    thread.setDaemon(true);
    thread.setPriority(Thread.NORM_PRIORITY);
    thread.setUncaughtExceptionHandler(
        (t, e) -> LOG.error("[CacheThreadFactory] 线程 [{}] 未捕获异常", t.getName(), e));
    return thread;
  }
}
