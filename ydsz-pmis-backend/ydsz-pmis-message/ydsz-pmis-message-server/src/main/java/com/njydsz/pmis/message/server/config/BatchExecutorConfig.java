package com.njydsz.pmis.message.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 消息批次异步线程池配置�? *
 * <p>�?{@code BatchServiceImpl.executeBatchAsync} 提供独立线程池，
 * 避免批量发送占用主业务线程。核�?2 线程，最�?4 线程，队�?200�? * 拒绝策略 CallerRunsPolicy（队列满时降级为同步执行）�? *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@EnableAsync
public class BatchExecutorConfig {

    @Bean("messageBatchExecutor")
    public Executor messageBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("msg-batch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[BatchExecutor] 线程池已初始�? core=2 max=4 queue=200");
        return executor;
    }
}
