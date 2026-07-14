package com.njydsz.pmis.common.core.context;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.alibaba.ttl.TtlRunnable;

/**
 * TTL 异步上下文传播自动配置
 *
 * <p>自动注册 {@link TtlTaskDecorator}，使 Spring 管理的所有异步线程池
 * 自动传播 {@code TransmittableThreadLocal} 上下文。
 *
 * <p>启用条件：
 * <ul>
 *   <li>类路径存在 {@link TtlRunnable}（alibaba/transmittable-thread-local）</li>
 *   <li>{@code pmis.ttl.enabled=true}（默认 true）</li>
 * </ul>
 *
 * <p>禁用方式：
 * <pre>{@code
 * pmis:
 *   ttl:
 *     enabled: false
 * }</pre>
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@AutoConfiguration
@ConditionalOnClass(TtlRunnable.class)
@ConditionalOnProperty(prefix = "pmis.ttl", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
@EnableScheduling
public class TtlAsyncAutoConfiguration {

    /**
     * TTL 任务装饰器
     *
     * <p>Spring 会自动将此装饰器应用到所有 {@code TaskExecutor}。
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator ttlTaskDecorator() {
        return new TtlTaskDecorator();
    }
}
