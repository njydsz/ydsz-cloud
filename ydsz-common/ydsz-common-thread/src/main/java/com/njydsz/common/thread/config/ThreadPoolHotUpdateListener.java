package com.njydsz.common.thread.config;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池运行时动态参数调整器。
 *
 * <p>基于 Spring Cloud Configuration / Nacos 配置变更机制触发：
 *
 * <ul>
 *   <li>{@code setCorePoolSize} / {@code setMaximumPoolSize} 可运行时调整
 *   <li>{@code queueCapacity} 无法动态调整（阻塞队列不可 resize），新值记录后下次创建生效
 *   <li>{@code threadNamePrefix} 运行时修改仅影响后续创建的新线程，已有线程名不变
 *   <li>{@code rejectPolicy} 运行时可直接替换执行器持有的拒绝策略引用
 * </ul>
 *
 * <p>启用方式：在 application.yml 中设置 {@code ydsz.thread.hot-update.enabled=true}， 或通过 {@link
 * ThreadPoolHotUpdateAutoConfiguration} 注册。
 *
 * <p>26.09.01 变更：
 *
 * <ul>
 *   <li>从依赖 {@link ThreadPoolAutoConfiguration} 改为直接注入 {@link ApplicationContext}， 降低耦合并纳入自动配置体系
 *   <li>由 {@link ThreadPoolHotUpdateAutoConfiguration} 自动注册，无需业务模块手动创建 Bean
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ThreadPoolHotUpdateListener implements ApplicationContextAware {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolHotUpdateListener.class);

  /**
   * Bean 名称常量，供其他模块引用。
   *
   * @since 26.09.01
   */
  public static final String BEAN_NAME = "threadPoolHotUpdateListener";

  private ApplicationContext applicationContext;

  /** 构造线程池运行时参数调整器。 */
  public ThreadPoolHotUpdateListener() {}

  /**
   * 注入 {@link ApplicationContext} 以运行时查询线程池 Bean。
   *
   * @param applicationContext Spring 应用上下文
   */
  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * 应用上下文刷新完成后回调，打印线程池注册摘要。
   *
   * <p>该方法由 Spring 容器在 {@link ContextRefreshedEvent} 发布时自动调用。
   *
   * @param event 上下文刷新事件
   */
  @EventListener(ContextRefreshedEvent.class)
  public void onContextReady(ContextRefreshedEvent event) {
    Map<String, ThreadPoolTaskExecutor> executors = getExecutors();
    LOG.info(
        "[ThreadPoolHotUpdate] 热更新监听器就绪，当前共 {} 个平台线程池: {}", executors.size(), executors.keySet());
  }

  /**
   * 动态调整指定线程池的 coreSize 和 maxSize。
   *
   * <p>自动处理调序：先扩大 max 再调整 core（避免 core > max 异常）。
   *
   * @param poolName 线程池配置 key（如 "io"）
   * @param newCoreSize 新的核心线程数（必须 >= 1）
   * @param newMaxSize 新的最大线程数（必须 >= newCoreSize）
   */
  public void resizePool(String poolName, int newCoreSize, int newMaxSize) {
    ThreadPoolTaskExecutor executor = getExecutor(poolName);
    if (executor == null) {
      LOG.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
      return;
    }
    if (newCoreSize < 1 || newMaxSize < 1 || newCoreSize > newMaxSize) {
      LOG.warn("[ThreadPoolHotUpdate] 参数非法: core={}, max={}, 跳过", newCoreSize, newMaxSize);
      return;
    }

    resizeInternal(executor, newCoreSize, newMaxSize, poolName);
  }

  /**
   * 动态调整指定线程池的拒绝策略。
   *
   * @param poolName 线程池配置 key（如 "io"）
   * @param newPolicy 新的拒绝策略
   */
  public void updateRejectPolicy(String poolName, ThreadPoolProperties.RejectPolicy newPolicy) {
    ThreadPoolTaskExecutor executor = getExecutor(poolName);
    if (executor == null) {
      LOG.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
      return;
    }
    RejectedExecutionHandler newHandler = createRejectHandler(newPolicy);
    executor.setRejectedExecutionHandler(newHandler);
    LOG.info("[ThreadPoolHotUpdate] 线程池 [{}] 拒绝策略已更新为 {}", poolName, newPolicy);
  }

  /**
   * 动态更新 threadNamePrefix（仅影响新创建的线程）。
   *
   * @param poolName 线程池配置 key
   * @param newThreadPrefix 新的线程名前缀
   */
  public void updateThreadNamePrefix(String poolName, String newThreadPrefix) {
    ThreadPoolTaskExecutor executor = getExecutor(poolName);
    if (executor == null) {
      LOG.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
      return;
    }
    executor.setThreadNamePrefix(newThreadPrefix);
    LOG.info("[ThreadPoolHotUpdate] 线程池 [{}] 线程名前缀已更新为 {}（仅影响新线程）", poolName, newThreadPrefix);
  }

  /**
   * 获取指定线程池的当前快照。
   *
   * @param poolName 线程池配置 key
   * @return 线程池快照信息
   */
  public ThreadPoolSnapshot snapshot(String poolName) {
    ThreadPoolTaskExecutor executor = getExecutor(poolName);
    if (executor == null) {
      return null;
    }
    try {
      ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
      return new ThreadPoolSnapshot(
          poolName,
          pool.getCorePoolSize(),
          pool.getMaximumPoolSize(),
          pool.getActiveCount(),
          pool.getPoolSize(),
          pool.getQueue().size(),
          pool.getCompletedTaskCount());
    } catch (Exception e) {
      LOG.warn("[ThreadPoolHotUpdate] 线程池 [{}] 快照获取失败: {}", poolName, e.getMessage());
      return null;
    }
  }

  /**
   * 获取所有线程池的快照。
   *
   * @return poolName → snapshot 的映射
   */
  public Map<String, ThreadPoolSnapshot> snapshotAll() {
    Map<String, ThreadPoolSnapshot> result = new LinkedHashMap<>(16);