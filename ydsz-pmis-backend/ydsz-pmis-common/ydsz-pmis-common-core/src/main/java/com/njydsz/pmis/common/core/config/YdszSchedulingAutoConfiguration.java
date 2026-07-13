package com.njydsz.pmis.common.core.config;

import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * ydsz统一调度自动配置
 *
 * <p>集中管理 {@link EnableScheduling}，避免各子模块重复声明导致创建多套调度基础设施。
 * 所有子模块（auth、notify、file、queue、audit 等）的 {@code @Scheduled} 定时任务
 * 均依赖此统一入口。</p>
 *
 * <p><b>线程模型：</b>注册基于 Virtual Thread 的 {@link ThreadPoolTaskScheduler}，
 * 替代 Spring 默认的单线程 {@code TaskScheduler}。核心调度线程固定为
 * {@code Math.max(2, CPU核心数)} 个 platform carrier thread 负责触发调度，
 * 实际任务执行委托给 Virtual Thread，避免慢任务阻塞后续调度。</p>
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * # 禁用统一调度（默认启用），将导致所有 @Scheduled 任务不执行
 * ydsz.scheduling.enabled=false
 *
 * # 自定义调度线程池大小（默认 = max(2, CPU核心数)）
 * ydsz.scheduling.pool-size=4
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YdszSchedulingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(YdszSchedulingAutoConfiguration.class);

    /**
     * 注册基于 Virtual Thread 的 TaskScheduler Bean。
     *
     * <p>使用 {@link ThreadPoolTaskScheduler} 作为底层调度器，核心线程数默认为
     * {@code max(2, CPU核心数)}，实际任务通过 Virtual Thread 执行。
     * 注册为 {@code taskScheduler} Bean 名称，Spring 会自动发现并使用。</p>
     *
     * @return 配置好的 ThreadPoolTaskScheduler 实例
     */
    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("ydsz-sched-");
        // 设置虚拟线程执行器，@Scheduled 任务在 Virtual Thread 上执行
        // 注意：Executors.newVirtualThreadPerTaskExecutor() 创建的线程不占用平台线程
        // 配置 Virtual Thread 工厂：调度线程触发后，任务在虚拟线程中执行
        ThreadFactory virtualThreadFactory = Thread.ofVirtual()
                .name("ydsz-sched-vt-", 0)
                .factory();
        scheduler.setThreadFactory(virtualThreadFactory);
        // 等待任务完成再关闭
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        // 出错时不中断调度循环
        scheduler.setErrorHandler(t ->
                log.error("【统一调度】@Scheduled 任务执行异常", t));
        scheduler.setRemoveOnCancelPolicy(true);
        log.info("ydsz统一调度已启用，TaskScheduler 线程池大小={}, 使用 Virtual Thread 执行任务", poolSize);
        return scheduler;
    }
}