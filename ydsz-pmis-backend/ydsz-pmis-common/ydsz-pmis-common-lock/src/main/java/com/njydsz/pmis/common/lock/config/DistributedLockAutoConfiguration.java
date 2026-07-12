package com.njydsz.pmis.common.lock.config;

import com.njydsz.pmis.common.lock.aspect.DistributedLockAspect;
import com.njydsz.pmis.common.lock.metrics.LockMetrics;
import com.njydsz.pmis.common.lock.metrics.LockMetricsExporter;
import com.njydsz.pmis.common.lock.scheduler.LockWatchDog;
import com.njydsz.pmis.common.lock.strategy.DefaultLockStrategy;
import com.njydsz.pmis.common.lock.strategy.LockStrategy;
import com.njydsz.pmis.common.redis.service.RedisService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式锁自动配置类
 *
 * <p>基于 Spring Boot AutoConfiguration 机制，自动装配分布式锁相关的 Bean，
 * 包括锁策略、看门狗、指标收集器、AOP 切面等组件。
 *
 * <p><b>配置项：</b>
 * <ul>
 *   <li>ydsz.lock.enabled - 是否启用分布式锁（默认 true）</li>
 *   <li>ydsz.lock.fallback-enabled - 是否启用降级策略</li>
 *   <li>ydsz.lock.watchdog-enabled - 是否启用看门狗自动续期</li>
 *   <li>ydsz.lock.max-renew-times - 最大续期次数</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({StringRedisTemplate.class})
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "ydsz.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({LockProperties.class})
public class DistributedLockAutoConfiguration {

    /**
     * 创建锁指标收集器 Bean
     *
     * @return LockMetrics 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockMetrics lockMetrics() {
        return new LockMetrics();
    }

    /**
     * 创建锁指标导出器 Bean
     *
     * @param lockMetrics 锁指标收集器
     * @return LockMetricsExporter 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockMetricsExporter lockMetricsExporter(LockMetrics lockMetrics) {
        return new LockMetricsExporter(lockMetrics);
    }

    /**
     * 创建看门狗 Bean（原始版本）
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockProperties 锁配置属性
     * @param lockMetrics 锁指标收集器
     * @return LockWatchDog 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockWatchDog lockWatchDog(TaskScheduler lockWatchDogScheduler, StringRedisTemplate stringRedisTemplate, LockProperties lockProperties, LockMetrics lockMetrics) {
        LockWatchDog lockWatchDog = new LockWatchDog(lockWatchDogScheduler, stringRedisTemplate, lockProperties.getMaxRenewTimes());
        lockWatchDog.setLockMetrics(lockMetrics);
        return lockWatchDog;
    }

    /**
     * 创建锁策略 Bean
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog 看门狗
     * @param lockMetrics 锁指标收集器
     * @param redisServiceProvider RedisService 提供者
     * @return LockStrategy 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockStrategy lockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog,
                                     LockMetrics lockMetrics, LockProperties lockProperties,
                                     org.springframework.beans.factory.ObjectProvider<RedisService> redisServiceProvider,
                                     org.springframework.beans.factory.ObjectProvider<TaskScheduler> schedulerProvider) {
        RedisService redisService = redisServiceProvider.getIfAvailable();
        TaskScheduler scheduler = schedulerProvider.getIfAvailable();
        String namespace = lockProperties.getNamespace();
        if (redisService != null) {
            return new DefaultLockStrategy(stringRedisTemplate, lockWatchDog, redisService, lockMetrics, scheduler, namespace);
        }
        return new DefaultLockStrategy(stringRedisTemplate, lockWatchDog, null, lockMetrics, scheduler, namespace);
    }

    /**
     * 创建分布式锁 AOP 切面 Bean
     *
     * @param lockStrategy 锁策略
     * @param lockMetrics 锁指标收集器
     * @param lockProperties 锁配置属性
     * @return DistributedLockAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect distributedLockAspect(LockStrategy lockStrategy, LockMetrics lockMetrics,
                                                       LockProperties lockProperties) {
        DistributedLockAspect aspect = new DistributedLockAspect(lockStrategy, lockProperties.isFallbackEnabled());
        aspect.setLockMetrics(lockMetrics);
        return aspect;
    }

    /**
     * 创建锁获取线程池（Spring 管理，支持优雅停机和配置化）
     *
     * @param lockProperties 锁配置属性
     * @return ExecutorService 实例
     */
    @Bean("lockAcquireExecutor")
    @ConditionalOnMissingBean(name = "lockAcquireExecutor")
    public ExecutorService lockAcquireExecutor(LockProperties lockProperties) {
        LockProperties.ThreadPool pool = lockProperties.getAcquirePool();
        AtomicInteger threadNumber = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                pool.getCoreSize(), pool.getMaxSize(), 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(pool.getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "ydsz-lock-acquire-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建调度线程池（用于 WatchDog 续期和信号量超时调度，Spring 管理，支持优雅停机和配置化）
     *
     * @param lockProperties 锁配置属性
     * @return TaskScheduler 实例
     */
    @Bean("lockWatchDogScheduler")
    @ConditionalOnMissingBean(name = "lockWatchDogScheduler")
    public TaskScheduler lockWatchDogScheduler(LockProperties lockProperties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(lockProperties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("ydsz-lock-watchdog-");
        scheduler.setDaemon(true);
        scheduler.afterPropertiesSet();
        return scheduler;
    }
}
