package com.njydsz.common.lock.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.njydsz.common.lock.aspect.DistributedScheduledAspect;
import com.njydsz.common.lock.aspect.IdempotentAspect;
import com.njydsz.common.lock.aspect.RepeatSubmitAspect;
import com.njydsz.common.lock.aspect.YdszDistributedLockAspect;
import com.njydsz.common.lock.core.LockTemplate;
import com.njydsz.common.lock.health.LockHealthIndicator;
import com.njydsz.common.lock.idempotent.IdempotentStrategy;
import com.njydsz.common.lock.idempotent.RedisIdempotentStrategy;
import com.njydsz.common.lock.idempotent.RepeatSubmitTokenService;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.metrics.LockMetricsExporter;
import com.njydsz.common.lock.notify.LockReleaseNotifier;
import com.njydsz.common.lock.renewal.LockRenewalService;
import com.njydsz.common.lock.scheduler.LockLeakDetector;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.lock.strategy.DefaultLockStrategy;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.redis.service.ops.RedisStringOps;


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
 * @author ydsz-team
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
     * 创建统一的锁续期服务 Bean。
     *
     * <p>v1.2.0 引入，将散落在各锁实现中的续期 Lua 脚本统一收口，消除双锁冗余。
     * 业务方可覆盖此 Bean 以注入自定义 {@link LockRenewalService.LockRenewalStrategy}。
     *
     * @return LockRenewalService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockRenewalService lockRenewalService() {
        return new LockRenewalService();
    }

    /**
     * 创建看门狗 Bean。
     *
     * <p>v1.2.0 变更：通过 {@link LockRenewalService} 统一执行续期，
     * 不再在本地维护续期 Lua 脚本。
     *
     * @param lockWatchDogScheduler 看门狗调度线程池
     * @param stringRedisTemplate   Redis 模板
     * @param lockProperties        锁配置属性
     * @param lockMetrics           锁指标收集器
     * @param renewalService        统一的锁续期服务
     * @return LockWatchDog 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockWatchDog lockWatchDog(@Qualifier("lockWatchDogScheduler") TaskScheduler lockWatchDogScheduler,
                                     StringRedisTemplate stringRedisTemplate,
                                     LockProperties lockProperties,
                                     LockMetrics lockMetrics,
                                     LockRenewalService renewalService) {
        LockWatchDog lockWatchDog = new LockWatchDog(
                renewalService,
                lockWatchDogScheduler,
                stringRedisTemplate,
                lockProperties.getMaxRenewTimes()
        );
        lockWatchDog.setLockMetrics(lockMetrics);
        return lockWatchDog;
    }

    /**
     * 创建锁释放通知器 Bean。
     *
     * <p>依赖 common-redis 提供的 {@link RedisMessageListenerContainer}，
     * 基于 Redis pub/sub 在锁释放时唤醒等待线程，减少高竞争场景下的空轮询。
     * 容器未配置时该 Bean 不注册，等待路径自动退化为指数退避轮询。
     *
     * @param stringRedisTemplate Redis 模板
     * @param listenerContainer   Redis 消息监听容器
     * @return LockReleaseNotifier 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisMessageListenerContainer.class)
    public LockReleaseNotifier lockReleaseNotifier(StringRedisTemplate stringRedisTemplate,
                                                   RedisMessageListenerContainer listenerContainer) {
        return new LockReleaseNotifier(stringRedisTemplate, listenerContainer);
    }

    /**
     * 创建锁指标导出器 Bean（供监控/日志聚合系统消费 JSON 指标快照）
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
     * 创建锁策略 Bean
     *
     * <p>可选依赖（RedisStringOps / RedisTemplate / TaskScheduler / LockRenewalService /
     * LockReleaseNotifier）通过 {@link LockOptionalDependencies} 聚合注入，
     * 避免 Bean 方法参数超过 5 个（云顶编码规范 5.4 节）。
     *
     * @param stringRedisTemplate Redis 模板
     * @param lockWatchDog         看门狗
     * @param lockMetrics          锁指标收集器
     * @param lockProperties       锁配置属性
     * @param optionalDependencies 可选依赖聚合
     * @return LockStrategy 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockStrategy lockStrategy(StringRedisTemplate stringRedisTemplate, LockWatchDog lockWatchDog,
                                     LockMetrics lockMetrics, LockProperties lockProperties,
                                     LockOptionalDependencies optionalDependencies) {
        String namespace = lockProperties.getNamespace();
        DefaultLockStrategy strategy = new DefaultLockStrategy(
                stringRedisTemplate, lockWatchDog,
                optionalDependencies.stringOpsProvider().getIfAvailable(),
                optionalDependencies.redisTemplateProvider().getIfAvailable(),
                lockMetrics,
                optionalDependencies.schedulerProvider().getIfAvailable(),
                namespace, lockProperties.getMultiLock(),
                optionalDependencies.notifierProvider().getIfAvailable());
        optionalDependencies.renewalServiceProvider().ifAvailable(strategy::setLockRenewalService);
        return strategy;
    }

    /**
     * 创建锁策略可选依赖聚合 Bean
     *
     * @param stringOpsProvider      RedisStringOps 提供者
     * @param redisTemplateProvider  RedisTemplate 提供者
     * @param schedulerProvider      TaskScheduler 提供者
     * @param renewalServiceProvider LockRenewalService 提供者
     * @param notifierProvider       LockReleaseNotifier 提供者
     * @return 可选依赖聚合
     */
    @Bean
    @ConditionalOnMissingBean
    public LockOptionalDependencies lockOptionalDependencies(
            ObjectProvider<RedisStringOps> stringOpsProvider,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
            ObjectProvider<TaskScheduler> schedulerProvider,
            ObjectProvider<LockRenewalService> renewalServiceProvider,
            ObjectProvider<LockReleaseNotifier> notifierProvider) {
        return new LockOptionalDependencies(stringOpsProvider, redisTemplateProvider,
                schedulerProvider, renewalServiceProvider, notifierProvider);
    }

    /**
     * 创建编程式锁操作模板 Bean
     *
     * <p>提供 {@link LockTemplate#execute} 系列方法，自动管理锁的获取与释放。
     *
     * @param lockStrategy 锁策略
     * @return LockTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public LockTemplate lockTemplate(LockStrategy lockStrategy) {
        return new LockTemplate(lockStrategy);
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
    public YdszDistributedLockAspect distributedLockAspect(LockStrategy lockStrategy, LockMetrics lockMetrics,
                                                           LockProperties lockProperties) {
        YdszDistributedLockAspect aspect = new YdszDistributedLockAspect(lockStrategy, lockProperties.isFallbackEnabled());
        aspect.setLockMetrics(lockMetrics);
        return aspect;
    }

    /**
     * 创建幂等策略 Bean
     *
     * <p>基于 Redis SET NX EX 实现，Redis 不可用时降级放行。
     *
     * @param stringRedisTemplate Redis 客户端
     * @return IdempotentStrategy 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentStrategy idempotentStrategy(StringRedisTemplate stringRedisTemplate) {
        return new RedisIdempotentStrategy(stringRedisTemplate);
    }

    /**
     * 创建接口幂等性 AOP 切面 Bean
     *
     * <p>拦截 {@link com.njydsz.common.lock.annotation.Idempotent} 注解方法，
     * 基于 Redis {@code SET NX EX} Lua 脚本实现"在 TTL 窗口内同一幂等键只处理一次"。
     *
     * <p>项目硬约束：AOP 组件必须通过 {@code @ConditionalOnMissingBean} 注册，
     * 允许业务方覆盖默认实现。
     *
     * @param idempotentStrategy 幂等策略
     * @param lockMetrics        锁指标收集器（可选）
     * @param lockProperties     锁配置属性（用于获取 namespace）
     * @return IdempotentAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect(IdempotentStrategy idempotentStrategy,
                                             LockMetrics lockMetrics,
                                             LockProperties lockProperties) {
        return new IdempotentAspect(idempotentStrategy,
                lockProperties.getIdempotent().getKeyPrefix(),
                lockProperties.getNamespace(),
                lockMetrics);
    }

    /**
     * 注册分布式锁健康检查指示器 Bean。
     *
     * <p>探测 Redis 连接可用性、看门狗存活与锁指标采集状态，对外暴露锁子系统健康度。
     * 依赖 Spring HealthIndicator 类与 Redis 连接工厂存在时启用；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnBean(StringRedisTemplate.class)
    public LockHealthIndicator lockHealthIndicator(RedisConnectionFactory redisConnectionFactory,
                                                    ObjectProvider<LockWatchDog> lockWatchDogProvider,
                                                    ObjectProvider<LockMetrics> lockMetricsProvider) {
        return new LockHealthIndicator(redisConnectionFactory, lockWatchDogProvider, lockMetricsProvider);
    }

    /**
     * 注册锁泄漏检测器 Bean。
     *
     * <p>周期性扫描持有超时的锁记录（如看门狗续期失败、节点崩溃遗留），触发告警或强制释放，
     * 防止死锁导致业务线程永久阻塞。依赖看门狗实例存在时启用；无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LockWatchDog.class)
    public LockLeakDetector lockLeakDetector(ObjectProvider<LockWatchDog> watchDogProvider,
                                               ObjectProvider<LockMetrics> lockMetricsProvider) {
        return new LockLeakDetector(watchDogProvider, lockMetricsProvider);
    }

    /**
     * 创建表单重复提交 Token 服务 Bean
     *
     * <p>提供 Token 的生成、校验和删除功能，用于防止表单重复提交。
     *
     * @param stringRedisTemplate Redis 客户端
     * @return RepeatSubmitTokenService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RepeatSubmitTokenService repeatSubmitTokenService(StringRedisTemplate stringRedisTemplate) {
        return new RepeatSubmitTokenService(stringRedisTemplate);
    }

    /**
     * 创建表单重复提交 AOP 切面 Bean
     *
     * <p>拦截 {@link com.njydsz.common.lock.annotation.RepeatSubmit} 注解方法，
     * 基于 Token 令牌模式防止表单重复提交。
     *
     * @param repeatSubmitTokenService Token 服务
     * @return RepeatSubmitAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RepeatSubmitAspect repeatSubmitAspect(RepeatSubmitTokenService repeatSubmitTokenService) {
        return new RepeatSubmitAspect(repeatSubmitTokenService);
    }

    /**
     * 创建分布式定时任务 AOP 切面 Bean
     *
     * <p>拦截 {@link com.njydsz.common.lock.annotation.DistributedScheduled} 注解方法，
     * 确保多节点部署时同一 @Scheduled 任务同一时刻只有一个节点执行。
     * 获取不到锁的节点直接跳过本次执行（非阻塞）。
     *
     * <p>降级策略：LockStrategy 不存在时直接执行任务不加锁。
     *
     * @param lockStrategy 锁策略（可选）
     * @return DistributedScheduledAspect 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedScheduledAspect distributedScheduledAspect(ObjectProvider<LockStrategy> lockStrategyProvider) {
        LockStrategy lockStrategy = lockStrategyProvider.getIfAvailable();
        return new DistributedScheduledAspect(lockStrategy);
    }

    /**
     * 创建锁获取线程池（Spring 管理，支持优雅停机和配置化）
     *
     * @param lockProperties 锁配置属性
     * @return ExecutorService 实例
     */
    /** 锁获取线程池空闲线程存活时间（秒） */
    private static final long ACQUIRE_THREAD_KEEP_ALIVE_SECONDS = 60L;

    @Bean("lockAcquireExecutor")
    @ConditionalOnMissingBean(name = "lockAcquireExecutor")
    public ExecutorService lockAcquireExecutor(LockProperties lockProperties) {
        LockProperties.ThreadPool pool = lockProperties.getAcquirePool();
        AtomicInteger threadNumber = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                pool.getCoreSize(), pool.getMaxSize(), ACQUIRE_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
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
