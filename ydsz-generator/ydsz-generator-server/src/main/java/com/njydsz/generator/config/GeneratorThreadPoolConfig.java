package com.njydsz.generator.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 代码生成器线程池声明式配置。
 *
 * <p>替代旧版运行时动态创建方式，通过 Spring 容器统一管理线程池生命周期，
 * 运维侧可监控/调整 coreMaxSize。
 *
 * <p>参数通过 {@code generator.thread-pool} 配置项外化：
 * <ul>
 *   <li>{@code core-size} — 核心线程数（默认 CPU 核数）</li>
 *   <li>{@code max-size}  — 最大线程数（默认 16）</li>
 *   <li>{@code queue-capacity} — 排队容量（默认 64）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Slf4j
@Configuration
public class GeneratorThreadPoolConfig {

  /** 线程池 Bean 名称，供 {@code @Qualifier} 精确注入。 */
  public static final String BEAN_CODE_GEN_POOL = "codeGenTaskExecutor";

  /**
   * 代码生成异步任务线程池。
   *
   * <p>核心线程数 = min(CPU核数, max-size)，队列容量外化可配。
   * 拒绝策略使用 CallerRunsPolicy 防止任务静默丢失。
   *
   * @param coreSize       核心线程数
   * @param maxSize        最大线程数
   * @param queueCapacity  队列容量
   * @return ExecutorService（实际为 ThreadPoolTaskExecutor 适配的 JDK 线程池）
   */
  @Bean(destroyMethod = "shutdown", name = BEAN_CODE_GEN_POOL)
  public ExecutorService codeGenTaskExecutor(
      @Value("${generator.thread-pool.core-size:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
      int coreSize,
      @Value("${generator.thread-pool.max-size:16}")
      int maxSize,
      @Value("${generator.thread-pool.queue-capacity:64}")
      int queueCapacity) {

    // 当 YAML 未配置或配置为 0 时，使用 CPU 核数作为默认值
    int effectiveCore = coreSize > 0 ? coreSize
        : Runtime.getRuntime().availableProcessors();
    // 确保 max >= core
    int effectiveMax = Math.max(effectiveCore, maxSize);

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(effectiveCore);
    executor.setMaxPoolSize(effectiveMax);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("code-gen-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();

    log.info("代码生成线程池初始化完成 core={} max={} queue={}",
        effectiveCore, effectiveMax, queueCapacity);

    return executor.getThreadPoolExecutor();
  }
}
