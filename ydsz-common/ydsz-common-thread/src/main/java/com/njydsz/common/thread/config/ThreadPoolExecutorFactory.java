package com.njydsz.common.thread.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.thread.config.ThreadPoolProperties.PoolConfig;
import com.njydsz.common.thread.config.ThreadPoolProperties.RejectPolicy;

/**
 * 线程池执行器工厂。
 *
 * <p>供 {@link ThreadPoolRegistrar} 的工厂方法调用，负责创建具体的线程池实例。
 *
 * <p>v1.3.0 重构：从 {@link ThreadPoolAutoConfiguration} 内部类提取为独立组件， 确保 {@code
 * BeanDefinitionRegistryPostProcessor} 可在测试环境中正确运行。
 *
 * <p>v1.4.0 变更：
 *
 * <ul>
 *   <li>TimedTaskDecorator 自动注入，支持慢任务阈值传递
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class ThreadPoolExecutorFactory implements ApplicationContextAware, InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolExecutorFactory.class);

  private ApplicationContext applicationContext;

  /**
   * 注入 ApplicationContext，供 TaskDecorator 配置使用。
   *
   * <p>该方法由 Spring 容器在 Bean 初始化阶段自动回调。
   *
   * @param applicationContext Spring 应用上下文
   */
  @Override
  public void setApplicationContext(@NonNull ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterPropertiesSet() {
    if (applicationContext == null) {
      LOG.warn(
          "ydsz-thread: ThreadPoolExecutorFactory ApplicationContext 为 null，"
              + "TaskDecorator 配置将不可用");
    }
  }

  /**
   * 创建虚拟线程池（JDK 21+）。
   *
   * @param name 线程池名称
   * @param config 线程池配置
   * @return 虚拟线程池 ExecutorService
   */
  public ExecutorService createVirtualExecutor(String name, PoolConfig config) {
    LOG.info("ydsz-thread: 创建虚拟线程池 [{}]", name);
    return Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name(config.getThreadNamePrefix(), 0).factory());
  }

  /**
   * 创建平台线程池。
   *
   * <p>v1.4.0 变更：自动装配 {@link com.njydsz.common.thread.metrics.TimedTaskDecorator}， 使用配置中指定的慢任务阈值。
   *
   * @param name 线程池名称
   * @param config 线程池配置
   * @return 平台线程池
   */
  public ThreadPoolTaskExecutor createTaskExecutor(String name, PoolConfig config) {
    LOG.info(
        "ydsz-thread: 创建线程池 [{}] (core={}, max={}, queue={})",
        name,
        config.getCoreSize(),
        config.getMaxSize(),
        config.getQueueCapacity());
    // CHECKSTYLE.OFF: RegexpSinglelineJava — 线程池工厂内部构造托管 ThreadPoolTaskExecutor（云顶规范 15.4 豁免模块）
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // CHECKSTYLE.ON: RegexpSinglelineJava
    executor.setCorePoolSize(config.getCoreSize());
    executor.setMaxPoolSize(config.getMaxSize());
    executor.setQueueCapacity(config.getQueueCapacity());
    executor.setThreadNamePrefix(config.getThreadNamePrefix());
    executor.setRejectedExecutionHandler(createRejectHandler(config.getRejectPolicy()));
    executor.setAwaitTerminationSeconds(config.getAwaitTerminationSeconds());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAllowCoreThreadTimeOut(config.isAllowCoreThreadTimeOut());
    executor.setKeepAliveSeconds(config.getKeepAliveSeconds());

    // TaskDecorator 支持：跨线程传播上下文 + 耗时追踪
    applyTaskDecorators(executor, name, config);

    executor.initialize();
    return executor;
  }

  /**
   * 应用 TaskDecorator 链，包括用户配置的装饰器和内置的 TimedTaskDecorator。
   *
   * @param executor 目标线程池
   * @param name 线程池名称
   * @param config 线程池配置
   */
  private void applyTaskDecorators(
      ThreadPoolTaskExecutor executor, String name, PoolConfig config) {
    if (applicationContext == null) {
      LOG.warn("ydsz-thread: ApplicationContext 未注入，无法配置 TaskDecorator (pool={})", name);
      return;
    }

    List<TaskDecorator> decorators = new ArrayList<>();

    // 1. 用户配置的 TaskDecorator（如 MDC、RequestContext 传播）
    List<String> decoratorBeanNames = config.getTaskDecoratorBeanNames();
    if (decoratorBeanNames != null && !decoratorBeanNames.isEmpty()) {
      for (String beanName : decoratorBeanNames) {
        decorators.addAll(resolveTaskDecorator(beanName, name));
      }
    }

    // 2. TimedTaskDecorator（耗时追踪，始终存在）
    decorators.add(createTimedTaskDecorator(name, config));

    // 应用装饰器链
    if (!decorators.isEmpty()) {
      executor.setTaskDecorator(
          decorators.size() == 1 ? decorators.get(0) : new CompositeTaskDecorator(decorators));
      LOG.info(
          "ydsz-thread: 已为线程池 [{}] 启用 TaskDecorator: 用户={}, 耗时追踪=true",
          name,
          decoratorBeanNames == null ? 0 : decoratorBeanNames.size());
    }
  }

  /**
   * 创建耗时追踪装饰器。
   *
   * <p>优先从容器中获取已注册的 ThreadPoolTimerMetrics Bean， 如果不存在则创建一个新的（仅用于非生产场景）。
   *
   * @param name 线程池名称
   * @param config 线程池配置
   * @return TimedTaskDecorator 实例
   */
  private com.njydsz.common.thread.metrics.TimedTaskDecorator createTimedTaskDecorator(
      String name, PoolConfig config) {
    com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics timerMetrics = null;

    // 尝试获取已注册的 ThreadPoolTimerMetrics Bean
    String metricsBeanName = name + "ExecutorTimerMetrics";
    if (applicationContext.containsBean(metricsBeanName)) {
      Object bean = applicationContext.getBean(metricsBeanName);
      if (bean instanceof com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics) {
        timerMetrics = (com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics) bean;
      }
    }

    // 如果没有预注册的 Bean，则创建一个（此时需要 MeterRegistry，可能为 null）
    if (timerMetrics == null) {
      MeterRegistry meterRegistry = null;
      try {
        meterRegistry = applicationContext.getBean(MeterRegistry.class);
      } catch (Exception e) {
        // Micrometer 不存在时，指标数据不注册，追踪仍然工作
        LOG.debug("ydsz-thread: Micrometer MeterRegistry 不可用，耗时指标将不会被上报 (pool={})", name);
      }
      timerMetrics =
          com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics.createIfMeterRegistryPresent(
              name, meterRegistry);
    }

    return new com.njydsz.common.thread.metrics.TimedTaskDecorator(
        name, config.getSlowTaskThresholdMs(), timerMetrics);
  }

  /**
   * 根据 Bean 名称解析 TaskDecorator。
   *
   * @param beanName TaskDecorator Bean 名称
   * @param poolName 线程池名称（用于日志）
   * @return TaskDecorator 列表（可能为空）
   */
  private List<TaskDecorator> resolveTaskDecorator(String beanName, String poolName) {
    List<TaskDecorator> result = new ArrayList<>();
    if (!applicationContext.containsBean(beanName)) {
      LOG.warn("ydsz-thread: TaskDecorator Bean [{}] 不存在，跳过 (pool={})", beanName, poolName);
      return result;
    }
    try {
      Object bean = applicationContext.getBean(beanName);
      if (bean instanceof TaskDecorator) {
        result.add((TaskDecorator) bean);
      } else {
        LOG.warn("ydsz-thread: Bean [{}] 不是 TaskDecorator 类型，跳过 (pool={})", beanName, poolName);
      }
    } catch (Exception e) {
      LOG.warn(
          "ydsz-thread: 解析 TaskDecorator Bean [{}] 失败 (pool={}): {}",
          beanName,
          poolName,
          e.getMessage());
    }
    return result;
  }

  private RejectedExecutionHandler createRejectHandler(RejectPolicy policy) {
    if (policy == null) {
      return new ThreadPoolExecutor.CallerRunsPolicy();
    }
    switch (policy) {
      case ABORT:
        return new ThreadPoolExecutor.AbortPolicy();
      case CALLER_RUNS:
        return new ThreadPoolExecutor.CallerRunsPolicy();
      case DISCARD_OLDEST:
        return new ThreadPoolExecutor.DiscardOldestPolicy();
      case DISCARD:
        return new ThreadPoolExecutor.DiscardPolicy();
      default:
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }
  }

  /**
   * 组合式 TaskDecorator：将多个 TaskDecorator 串联执行。
   *
   * <p>只有在用户配置了多个 TaskDecorator Bean 名称， 或需要组合用户装饰器与内置 TimedTaskDecorator 时使用。
   */
  public static class CompositeTaskDecorator implements TaskDecorator {

    private final List<TaskDecorator> decorators;

    public CompositeTaskDecorator(List<TaskDecorator> decorators) {
      this.decorators = decorators;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
      Runnable result = runnable;
      for (TaskDecorator decorator : decorators) {
        result = decorator.decorate(result);
      }
      return result;
    }
  }
}
