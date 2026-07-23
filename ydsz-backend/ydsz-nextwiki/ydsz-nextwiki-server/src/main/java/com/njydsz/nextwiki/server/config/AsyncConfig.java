package com.njydsz.nextwiki.server.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

/**
 * NextWiki 异步任务与基础设施配置
 * <p>
 * 统一管理 @Async 线程池、缓存开关与外部 HTTP 客户端 Bean。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Configuration
@EnableAsync
@EnableCaching
public class AsyncConfig {

    /**
     * NextWiki 通用异步任务线程池（预览生成、缩略图、事件监听等）
     */
    @Bean("nextwikiTaskExecutor")
    public ThreadPoolTaskExecutor nextwikiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setExecutorQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("nextwiki-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * NextWiki 定时任务线程池
     */
    @Bean("nextwikiScheduleExecutor")
    public ThreadPoolTaskExecutor nextwikiScheduleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("nextwiki-sched-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * RestTemplate Bean（供 LLM 摘要等外部 HTTP 调用使用）
     */
    @Bean
    public RestTemplate nextwikiRestTemplate() {
        return new RestTemplate();
    }
}
