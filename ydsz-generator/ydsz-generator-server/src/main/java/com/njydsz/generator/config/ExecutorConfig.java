package com.njydsz.generator.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.thread.util.ExecutorUtils;

/**
 * 代码生成器异步线程池配置。
 *
 * <p>使用 {@link ExecutorUtils} 创建符合云顶编码规范的线程池，
 * 纳入 {@code ThreadPoolRegistry} 统一治理，拒绝策略使用 CallerRunsPolicy 保证任务不丢失。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Configuration
public class ExecutorConfig {

  /** 核心线程数（默认 CPU 核数，最小 2）。 */
  @Value("${generator.thread-pool.core-size:0}")
  private int coreSize;

  /** 最大线程数（默认 16）。 */
  @Value("${generator.thread-pool.max-size:16}")
  private int maxSize;

  /** 排队容量（默认 64）。 */
  @Value("${generator.thread-pool.queue-capacity:64}")
  private int queueCapacity;

  /**
   * 创建代码生成异步任务线程池。
   *
   * <p>Bean 名称 {@code codeGenExecutor} 与 {@link com.njydsz.generator.service.CodeGenService}
   * 构造器参数 {@code @Qualifier("codeGenExecutor")} 对应。
   *
   * @return 代码生成异步线程池
   */
  @Bean(name = "codeGenExecutor", destroyMethod = "shutdown")
  public ExecutorService codeGenExecutor() {
    int cores = coreSize > 0 ? coreSize : Math.max(Runtime.getRuntime().availableProcessors(), 2);
    log.info("[ExecutorConfig] 创建 codeGenExecutor 线程池 (core={}, max={}, queue={})",
        cores, maxSize, queueCapacity);
    return ExecutorUtils.builder()
        .corePoolSize(cores)
        .maxPoolSize(maxSize)
        .queueCapacity(queueCapacity)
        .threadNamePrefix("ydsz-code-gen-")
        .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
        .buildAndRegister();
  }
}
