package com.njydsz.cronjob.server.config;

import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.njydsz.common.tenant.async.TenantContextTaskDecorator;
import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * Cronjob 调度器线程池配置（YDIZ-CONC-001 合规）。
 *
 * <p>将 {@link ThreadPoolTaskScheduler} 定义为 Spring {@code @Bean}，由容器统一管理线程池生命周期，
 * 避免业务代码直接 {@code new} 自建线程池（YDIZ-CONC-001：禁止业务代码自建线程池）。 配置参数通过 {@link
 * CronjobProperties} 注入，注册到 {@link ThreadPoolRegistry} 统一监控。
 *
 * <p>配合 {@code JobServiceImpl} 构造器注入使用，替代原有的 {@code @PostConstruct initScheduler()} 自建逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
public class CronjobSchedulerConfig {

  /**
   * 创建并配置 cronjob 调度器线程池。
   *
   * <p>Bean 名称 {@code cronjobTaskScheduler}，通过 {@link CronjobProperties} 注入 poolSize /
   * awaitTerminationSeconds 等参数。 注册到 {@link ThreadPoolRegistry} 统一监控。
   *
   * @param cronjobProperties 调度配置属性（poolSize、awaitTerminationSeconds 等）
   * @param tenantContextTaskDecorator 租户上下文装饰器（自动传播租户到异步线程）
   * @return 已初始化并注册到 {@link ThreadPoolRegistry} 的调度器实例
   */
  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskScheduler cronjobTaskScheduler(
      CronjobProperties cronjobProperties,
      TenantContextTaskDecorator tenantContextTaskDecorator) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(cronjobProperties.getSchedulerPoolSize());
    scheduler.setThreadNamePrefix("ydsz-cronjob-scheduler-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(cronjobProperties.getSchedulerAwaitTerminationSeconds());
    scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    // P0-FIX: 注入租户上下文装饰器，定时任务异步执行时自动传播租户上下文
    scheduler.setTaskDecorator(tenantContextTaskDecorator);
    scheduler.initialize();
    // P0-2: 注册至 ThreadPoolRegistry 统一监控
    ThreadPoolRegistry.register("cronjob-scheduler", scheduler.getScheduledThreadPoolExecutor());
    log.info("[Cronjob] 任务调度器初始化完成, poolSize={}", cronjobProperties.getSchedulerPoolSize());
    return scheduler;
  }
}
