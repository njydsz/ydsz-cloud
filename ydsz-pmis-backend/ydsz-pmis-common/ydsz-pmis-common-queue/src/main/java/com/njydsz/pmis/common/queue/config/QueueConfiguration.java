package com.njydsz.pmis.common.queue.config;

import com.njydsz.pmis.common.queue.manager.QueueManager;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import com.njydsz.pmis.common.queue.queue.MessageQueueFactory;
import com.njydsz.pmis.common.queue.scheduler.DeadLetterRetryScheduler;
import com.njydsz.pmis.common.queue.service.DeadLetterQueueService;
import com.njydsz.pmis.common.queue.service.impl.DeadLetterQueueServiceImpl;
import com.njydsz.pmis.common.queue.service.impl.NoOpDeadLetterQueueService;
import com.njydsz.pmis.common.queue.trace.DefaultMessageTraceRecorder;
import com.njydsz.pmis.common.queue.trace.MessageTraceAspect;
import com.njydsz.pmis.common.queue.trace.MessageTraceRecorder;
import com.njydsz.pmis.common.queue.trace.RedisMessageTraceRecorder;
import com.njydsz.pmis.common.redis.service.RedisService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
/**
 * 消息队列自动配置类
 *
 * <p>提供消息队列模块的 Spring Boot 自动配置能力。
 * 当配置项 {@code remi.queue.enabled=true} 时自动启用。
 *
 * <p><b>Redis 连接复用：</b>
 * 优先使用 ydsz-pmis-common-redis 的 {@link RedisService} 复用 Redis 连接，
 * 当 RedisService 不可用时回退到 QueueProperties 中的连接配置自建 JedisPool。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * # application.yml
 * spring:
 *   data:
 *     redis:
 *       host: 127.0.0.1
 *       port: 6379
 * remi:
 *   queue:
 *     enabled: true
 *     stream-group: remi-group
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(QueueProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "remi.queue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QueueConfiguration {

    private final QueueProperties queueProperties;
    private final ObjectProvider<RedisService> redisServiceProvider;

    public QueueConfiguration(QueueProperties queueProperties,
                               ObjectProvider<RedisService> redisServiceProvider) {
        this.queueProperties = queueProperties;
        this.redisServiceProvider = redisServiceProvider;
    }

    /**
     * 初始化验证
     * <p>在 Spring 容器初始化完成后验证配置的基本有效性
     */
    @PostConstruct
    public void init() {
        RedisService redisService = redisServiceProvider.getIfAvailable();
        if (redisService != null) {
            log.info("[Queue] 消息队列配置初始化，Redis 连接：复用 ydsz-pmis-common-redis");
        } else {
            log.info("[Queue] 消息队列配置初始化，Redis 连接：自建 JedisPool（{}:{}）",
                    queueProperties.resolvedHost(),
                    queueProperties.resolvedPort());
            log.info("[Queue] 提示：推荐引入 ydsz-pmis-common-redis 模块以复用 Redis 连接，" +
                    "通过 spring.data.redis.* 配置连接信息");
        }
        log.debug("[Queue] 队列配置详情：{}", queueProperties);
    }

    /**
     * 创建消息队列管理器
     *
     * <p>提供统一的队列生命周期管理和监控指标收集。
     * 负责注册、查询、移除队列实例及其对应的监控指标。
     *
     * @return QueueManager 实例
     */
    @Bean
    @ConditionalOnMissingBean(QueueManager.class)
    public QueueManager queueManager() {
        log.info("[Queue] 创建消息队列管理器");
        return new QueueManager();
    }

    /**
     * 创建消息队列异步消费者线程池（Spring 管理，支持优雅停机）
     *
     * <p>统一托管所有消息队列异步消费者的执行线程，避免业务代码直接 new Thread。
     *
     * @param queueProperties 队列配置属性
     * @return 消费者线程池
     */
    @Bean("queueConsumerExecutor")
    @ConditionalOnMissingBean(name = "queueConsumerExecutor")
    public ExecutorService queueConsumerExecutor(QueueProperties queueProperties) {
        QueueProperties.ExecutorConfig cfg = queueProperties.resolvedConsumerExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCoreSize());
        executor.setMaxPoolSize(cfg.getMaxSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix(cfg.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(cfg.getAwaitTerminationSeconds());
        executor.initialize();
        log.info("[Queue] 创建异步消费者线程池，core={}, max={}, queue={}",
                cfg.getCoreSize(), cfg.getMaxSize(), cfg.getQueueCapacity());
        return executor.getThreadPoolExecutor();
    }

    /**
     * 创建消息队列提供者
     *
     * <p>消息队列提供者负责根据 QueueType 创建对应的队列实例。
     * 优先注入 ydsz-pmis-common-redis 的 {@link RedisService} 复用连接，
     * 当 RedisService 不可用时回退到自建 JedisPool 连接。
     *
     * @param consumerExecutor 消费者线程池
     * @return 消息队列提供者实例
     */
    @Bean
    @ConditionalOnMissingBean(IMessageQueueProvider.class)
    public IMessageQueueProvider messageQueueProvider(ExecutorService consumerExecutor) {
        RedisService redisService = redisServiceProvider.getIfAvailable();
        if (redisService != null) {
            log.info("[Queue] 创建消息队列提供者（复用 ydsz-pmis-common-redis 连接）");
            return new MessageQueueFactory(queueProperties, redisService, consumerExecutor);
        }
        log.info("[Queue] 创建消息队列提供者（自建 JedisPool 连接）");
        return new MessageQueueFactory(queueProperties, null, consumerExecutor);
    }

    /**
     * 创建死信队列服务
     *
     * <p>当 RedisService 可用时，创建基于 Redis 的死信队列服务实例。
     *
     * @param messageQueueProvider 消息队列提供者
     * @return 死信队列服务实例
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueueService.class)
    public DeadLetterQueueService deadLetterQueueService(IMessageQueueProvider messageQueueProvider) {
        RedisService redisService = redisServiceProvider.getIfAvailable();
        if (redisService != null) {
            log.info("[Queue] 创建死信队列服务（复用 ydsz-pmis-common-redis 连接）");
            RedisTemplate<String, Object> redisTemplate = redisService.getRedisTemplate();
            return new DeadLetterQueueServiceImpl(redisTemplate, messageQueueProvider, queueProperties);
        }
        log.warn("[Queue] RedisService 不可用，返回空操作死信队列服务");
        return new NoOpDeadLetterQueueService();
    }

    /**
     * 创建死信队列自动重试调度器
     *
     * <p>当死信队列服务可用且启用了自动重试时，创建定时调度器实例。
     *
     * @param deadLetterQueueService 死信队列服务
     * @return 死信队列重试调度器实例
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterRetryScheduler.class)
    public DeadLetterRetryScheduler deadLetterRetryScheduler(DeadLetterQueueService deadLetterQueueService) {
        if (deadLetterQueueService != null
                && !(deadLetterQueueService instanceof NoOpDeadLetterQueueService)
                && queueProperties.resolvedDeadLetterRetryEnabled()) {
            log.info("[Queue] 创建死信队列自动重试调度器，间隔: {}ms",
                    queueProperties.resolvedDeadLetterRetryInterval());
            return new DeadLetterRetryScheduler(deadLetterQueueService, queueProperties);
        }
        log.info("[Queue] 死信队列自动重试已禁用，跳过调度器创建");
        return null;
    }

    // ==================== 消息轨迹相关配置 ====================

    /**
     * 创建消息轨迹记录器
     *
     * <p>根据配置的后端类型创建对应的记录器实例：
     * <ul>
     *   <li>memory: 基于内存的 LRU 缓存实现</li>
     *   <li>redis: 基于 Redis Hash 的持久化实现</li>
     * </ul>
     *
     * @return 消息轨迹记录器实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageTraceRecorder.class)
    @ConditionalOnProperty(prefix = "remi.queue.trace", name = "enabled", havingValue = "true")
    public MessageTraceRecorder messageTraceRecorder() {
        String backend = queueProperties.getTrace().resolvedBackend();
        int ttlMinutes = queueProperties.getTrace().getTtlMinutes();

        if ("redis".equalsIgnoreCase(backend)) {
            RedisService redisService = redisServiceProvider.getIfAvailable();
            if (redisService != null) {
                log.info("[Queue] 创建 Redis 消息轨迹记录器，TTL: {} 分钟", ttlMinutes);
                return new RedisMessageTraceRecorder(redisService, ttlMinutes);
            }
            log.warn("[Queue] Redis 消息轨迹后端已配置但 RedisService 不可用，回退到内存模式");
        }

        int maxCapacity = queueProperties.getTrace().getMaxCapacity();
        log.info("[Queue] 创建内存消息轨迹记录器，容量: {}, TTL: {} 分钟", maxCapacity, ttlMinutes);
        return new DefaultMessageTraceRecorder(maxCapacity, ttlMinutes);
    }

    /**
     * 创建消息轨迹 AOP 切面
     *
     * <p>拦截 IMessagePublisher.publish() 方法，自动记录消息发送轨迹。
     *
     * @param traceRecorder 消息轨迹记录器
     * @return AOP 切面实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "remi.queue.trace", name = "enabled", havingValue = "true")
    public MessageTraceAspect messageTraceAspect(MessageTraceRecorder traceRecorder) {
        log.info("[Queue] 注册消息轨迹 AOP 切面");
        return new MessageTraceAspect(traceRecorder);
    }
}
