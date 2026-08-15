package com.njydsz.common.queue.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.queue.controller.DeadLetterQueueController;
import com.njydsz.common.queue.controller.TraceQueryController;
import com.njydsz.common.queue.actuator.QueueEndpoint;
import com.njydsz.common.queue.dedup.DedupCleanupScheduler;
import com.njydsz.common.queue.dedup.MessageDeduplicator;
import com.njydsz.common.queue.health.QueueHealthIndicator;
import com.njydsz.common.queue.manager.QueueManager;
import com.njydsz.common.queue.metrics.QueueMetricsBinder;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.queue.MessageQueueFactory;
import com.njydsz.common.queue.scheduler.DeadLetterRetryScheduler;
import com.njydsz.common.queue.service.DeadLetterQueueService;
import com.njydsz.common.queue.service.impl.DeadLetterQueueServiceImpl;
import com.njydsz.common.queue.service.impl.NoOpDeadLetterQueueService;
import com.njydsz.common.queue.trace.DefaultMessageTraceRecorder;
import com.njydsz.common.queue.trace.MessageTraceAspect;
import com.njydsz.common.queue.trace.MessageTraceFilter;
import com.njydsz.common.queue.trace.MessageTraceRecorder;
import com.njydsz.common.queue.trace.RedisMessageTraceRecorder;
import com.njydsz.common.queue.topology.MultiMQTopology;
import com.njydsz.common.queue.topology.TopologyType;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.extern.slf4j.Slf4j;
/**
 * 消息队列自动配置类
 *
 * <p>提供消息队列模块的 Spring Boot 自动配置能力。
 * 当配置项 {@code ydsz.queue.enabled=true} 时自动启用。
 *
 * <p><b>Redis 连接复用：</b>
 * 优先使用 ydsz-common-redis 的 RedisTemplate 复用 Redis 连接，
 * 当 RedisTemplate 不可用时回退到 QueueProperties 中的连接配置自建 JedisPool。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * # application.yml
 * spring:
 *   data:
 *     redis:
 *       host: 127.0.0.1
 *       port: 6379
 * ydsz:
 *   queue:
 *     enabled: true
 *     stream-group: ydsz-group
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(QueueProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.queue", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QueueConfiguration {

    private final QueueProperties queueProperties;
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final ObjectProvider<RedisStringOps> redisStringOpsProvider;

    public QueueConfiguration(QueueProperties queueProperties,
                               ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
                               ObjectProvider<RedisStringOps> redisStringOpsProvider) {
        this.queueProperties = queueProperties;
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisStringOpsProvider = redisStringOpsProvider;
    }

    /**
     * 初始化验证
     * <p>在 Spring 容器初始化完成后验证配置的基本有效性
     */
    @PostConstruct
    public void init() {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("[Queue] 消息队列配置初始化，Redis 连接：复用 ydsz-common-redis");
        } else {
            log.info("[Queue] 消息队列配置初始化，Redis 连接：自建 JedisPool（{}:{}）",
                    queueProperties.resolvedHost(),
                    queueProperties.resolvedPort());
            log.info("[Queue] 提示：推荐引入 ydsz-common-redis 模块以复用 Redis 连接，" +
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
     * 创建消息队列异步消费者线程池（Spring 管理，支持优雅停机）。
     *
     * <p>统一托管所有消息队列异步消费者的执行线程，避免业务代码直接 new Thread。
     *
     * <p>通过 {@code @ConditionalOnMissingBean} 允许业务方通过
     * {@code ydsz.thread.pools.queueConsumerExecutor} 注入 ydsz-common-thread 统一管理线程池覆盖本默认实现。
     *
     * <p><b>线程池管理规范：</b>
     * 本方法仅在 ydsz-common-thread 未提供 {@code queueConsumerExecutor} Bean 时生效。
     * 生产环境推荐通过 ydsz-common-thread 配置消费者线程池，获得监控、热更新、上下文传播能力。
     *
     * @param queueProperties 队列配置属性
     * @return 消费者线程池
     */
    @Bean("queueConsumerExecutor")
    @ConditionalOnMissingBean(name = "queueConsumerExecutor")
    public ExecutorService queueConsumerExecutor(QueueProperties queueProperties) {
        QueueProperties.ExecutorConfig cfg = queueProperties.resolvedConsumerExecutor();
        // CHECKSTYLE.OFF: ThreadPoolCreate
        // 兜底线程池：仅当 ydsz-common-thread 未提供 queueConsumerExecutor Bean 时生效。
        // 生产环境应通过 ydsz.thread.pools.queueConsumerExecutor 配置统一管理。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // CHECKSTYLE.ON: ThreadPoolCreate
        executor.setCorePoolSize(cfg.getCoreSize());
        executor.setMaxPoolSize(cfg.getMaxSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        // 符合云顶编码规范 15.4.4 命名约定：ydsz-{module}-{biz}-
        String prefix = cfg.getThreadNamePrefix();
        executor.setThreadNamePrefix(prefix.startsWith("ydsz-") ? prefix : "ydsz-" + prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(cfg.getAwaitTerminationSeconds());
        executor.initialize();
        log.info("[Queue] 创建兜底异步消费者线程池，core={}, max={}, queue={}, prefix={} "
                + "(生产环境推荐使用 ydsz-common-thread 统一管理)",
                cfg.getCoreSize(), cfg.getMaxSize(), cfg.getQueueCapacity(), executor.getThreadNamePrefix());
        return executor.getThreadPoolExecutor();
    }

    /**
     * 创建消息队列提供者
     *
     * <p>消息队列提供者负责根据 QueueType 创建对应的队列实例。
     * 优先注入 ydsz-common-redis 的 RedisTemplate 复用连接，
     * 当 RedisTemplate 不可用时回退到自建 JedisPool 连接。
     *
     * @param consumerExecutor 消费者线程池
     * @return 消息队列提供者实例
     */
    @Bean
    @ConditionalOnMissingBean(IMessageQueueProvider.class)
    public IMessageQueueProvider messageQueueProvider(ExecutorService consumerExecutor) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("[Queue] 创建消息队列提供者（复用 ydsz-common-redis 连接）");
            return new MessageQueueFactory(queueProperties, redisTemplate, consumerExecutor);
        }
        log.info("[Queue] 创建消息队列提供者（自建 JedisPool 连接）");
        return new MessageQueueFactory(queueProperties, null, consumerExecutor);
    }

    /**
     * 创建死信队列服务
     *
     * <p>当 RedisTemplate 可用时，创建基于 Redis 的死信队列服务实例。
     *
     * @param messageQueueProvider 消息队列提供者
     * @return 死信队列服务实例
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueueService.class)
    public DeadLetterQueueService deadLetterQueueService(IMessageQueueProvider messageQueueProvider) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            log.info("[Queue] 创建死信队列服务（复用 ydsz-common-redis 连接）");
            return new DeadLetterQueueServiceImpl(redisTemplate, messageQueueProvider, queueProperties);
        }
        log.warn("[Queue] RedisTemplate 不可用，返回空操作死信队列服务");
        return new NoOpDeadLetterQueueService();
    }

    /**
     * 创建死信队列自动重试调度器
     *
     * <p>当死信队列服务可用且启用了自动重试时，创建定时调度器实例。
     * 调度器使用带抖动的延迟策略，避免多实例同时扫描造成惊群。
     *
     * @param deadLetterQueueService 死信队列服务
     * @return 死信队列重试调度器实例，或 null（禁用时）
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterRetryScheduler.class)
    @ConditionalOnBean(DeadLetterQueueService.class)
    public DeadLetterRetryScheduler deadLetterRetryScheduler(DeadLetterQueueService deadLetterQueueService) {
        if (deadLetterQueueService == null
                || deadLetterQueueService instanceof NoOpDeadLetterQueueService
                || !queueProperties.resolvedDeadLetterRetryEnabled()) {
            log.info("[Queue] 死信队列自动重试已禁用，跳过调度器创建");
            return null;
        }
        // CHECKSTYLE.OFF: ThreadPoolCreate
        // 单线程调度池：仅用于定时触发死信扫描，短生命周期任务无上下文传播需求。
        // 直接构造 ScheduledThreadPoolExecutor 以绕过 Executors 禁用限制。
        ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1,
                r -> new Thread(r, "ydsz-queue-dlq-retry"));
        // CHECKSTYLE.ON: ThreadPoolCreate
        log.info("[Queue] 创建死信队列自动重试调度器，间隔: {}ms, 抖动: {}%",
                queueProperties.resolvedDeadLetterRetryInterval(),
                queueProperties.getDeadLetterRetryJitterPercent());
        return new DeadLetterRetryScheduler(deadLetterQueueService, queueProperties, scheduler);
    }

    // ==================== 消息去重配置 ====================

    /**
     * 创建内存消息去重器（单实例场景）
     *
     * <p>当 ydsz.queue.dedup-enabled=true 且 RedisTemplate 不可用时创建。
     * 分布式场景应使用 RedisMessageDeduplicator。
     *
     * @return 内存去重器实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicator.class)
    @ConditionalOnProperty(prefix = "ydsz.queue", name = "dedup-enabled", havingValue = "true")
    public MessageDeduplicator messageDeduplicator() {
        long window = queueProperties.resolvedDedupWindowMillis();
        log.info("[Queue] 创建内存消息去重器，窗口: {}ms", window);
        return new MessageDeduplicator(window);
    }

    /**
     * 创建去重记录定时清理调度器
     *
     * <p>当 MessageDeduplicator Bean 存在时自动创建，定期清理过期去重记录。
     *
     * @param messageDeduplicator 内存去重器实例
     * @return 清理调度器实例
     */
    @Bean
    @ConditionalOnMissingBean(DedupCleanupScheduler.class)
    @ConditionalOnBean(MessageDeduplicator.class)
    public DedupCleanupScheduler dedupCleanupScheduler(MessageDeduplicator messageDeduplicator) {
        log.info("[Queue] 创建去重记录定时清理调度器");
        return new DedupCleanupScheduler(messageDeduplicator);
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
    @ConditionalOnProperty(prefix = "ydsz.queue.trace", name = "enabled", havingValue = "true")
    public MessageTraceRecorder messageTraceRecorder() {
        String backend = queueProperties.getTrace().resolvedBackend();
        int ttlMinutes = queueProperties.getTrace().getTtlMinutes();

        if ("redis".equalsIgnoreCase(backend)) {
            RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                log.info("[Queue] 创建 Redis 消息轨迹记录器，TTL: {} 分钟", ttlMinutes);
                return new RedisMessageTraceRecorder(redisTemplate, ttlMinutes);
            }
            log.warn("[Queue] Redis 消息轨迹后端已配置但 RedisTemplate 不可用，回退到内存模式");
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
    @ConditionalOnProperty(prefix = "ydsz.queue.trace", name = "enabled", havingValue = "true")
    public MessageTraceAspect messageTraceAspect(MessageTraceRecorder traceRecorder) {
        log.info("[Queue] 注册消息轨迹 AOP 切面");
        return new MessageTraceAspect(traceRecorder);
    }

    /**
     * 创建消息队列链路追踪过滤器
     *
     * <p>从 HTTP 请求头中提取 traceId 并注入到 MDC 和 RequestContext，
     * 确保 REST API 调用消息发布者时 traceId 能够全链路传递。
     *
     * <p>当 MessageTraceRecorder 和 Servlet API 均可用时自动注册。
     *
     * @return 链路追踪过滤器实例
     */
    @Bean
    @ConditionalOnBean(MessageTraceRecorder.class)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public MessageTraceFilter messageTraceFilter() {
        log.info("[Queue] 注册消息队列链路追踪过滤器");
        return new MessageTraceFilter();
    }

    /**
     * 注册消息轨迹查询 REST API
     *
     * <p>提供按消息ID或链路追踪ID查询消息轨迹的 REST 接口，
     * 用于问题排查和全链路追踪可视化。
     *
     * @param traceRecorder 消息轨迹记录器
     * @return 轨迹查询控制器实例
     */
    @Bean
    @ConditionalOnBean(MessageTraceRecorder.class)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    @ConditionalOnMissingBean(TraceQueryController.class)
    public TraceQueryController traceQueryController(MessageTraceRecorder traceRecorder) {
        log.info("[Queue] 注册消息轨迹查询 REST API");
        return new TraceQueryController(traceRecorder);
    }

    // ==================== 健康检查配置 ====================

    /**
     * 创建多 MQ 组合拓扑实例
     *
     * <p>当 ydsz.queue.multi-topology.enabled=true 时，根据配置创建多 MQ 拓扑实例。
     * 支持主备切换、扇出和多源聚合三种拓扑模式。
     *
     * @param messageQueueProvider 消息队列提供者
     * @return 多 MQ 拓扑实例（禁用时返回 null）
     */
    @Bean
    @ConditionalOnMissingBean(MultiMQTopology.class)
    @ConditionalOnProperty(prefix = "ydsz.queue.multi-topology", name = "enabled", havingValue = "true")
    public MultiMQTopology multiMQTopology(IMessageQueueProvider messageQueueProvider) {
        QueueProperties.MultiTopologyConfig config = queueProperties.getMultiTopology();
        TopologyType type = config.resolvedType();
        List<QueueType> participantTypes = config.resolvedParticipants();
        String topologyName = config.getTopologyName();

        if (participantTypes.size() < 2) {
            log.warn("[Queue] 多 MQ 拓扑配置参与者不足 2 个，跳过拓扑创建: {}", config.getParticipants());
            return null;
        }

        log.info("[Queue] 创建多 MQ 拓扑，名称: {}, 类型: {}, 参与者: {}", topologyName, type, participantTypes);

        List<IMessageQueue> participants = participantTypes.stream()
                .map(messageQueueProvider::createMessageQueue)
                .toList();

        return new MultiMQTopology(topologyName, type, participants);
    }

    // ==================== 健康检查配置 ====================

    /**
     * 创建消息队列健康检查器
     *
     * <p>当 spring-boot-health 模块在 classpath 中时自动注册。
     * Redis 类型复用 RedisStringOps 连接检查，非 Redis 类型通过 TCP 端口连通性检查。
     *
     * @return 健康检查器实例
     */
    @Bean
    @ConditionalOnMissingBean(QueueHealthIndicator.class)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public QueueHealthIndicator queueHealthIndicator() {
        log.info("[Queue] 创建消息队列健康检查器");
        return new QueueHealthIndicator(queueProperties, redisStringOpsProvider);
    }

    // ==================== REST API 配置 ====================

    /**
     * 注册死信队列管理 REST API
     *
     * <p>当 DeadLetterQueueService 和 spring-web 在 classpath 中时自动注册。
     *
     * @param deadLetterQueueService 死信队列服务
     * @return 死信队列控制器实例
     */
    @Bean
    @ConditionalOnBean(DeadLetterQueueService.class)
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    @ConditionalOnMissingBean(DeadLetterQueueController.class)
    public DeadLetterQueueController deadLetterQueueController(DeadLetterQueueService deadLetterQueueService) {
        log.info("[Queue] 注册死信队列管理 REST API");
        return new DeadLetterQueueController(deadLetterQueueService);
    }

    // ==================== Actuator 端点配置 ====================

    /**
     * 注册消息队列 Actuator 端点
     *
     * <p>提供 /actuator/queues 端点，用于查询消息队列运行状态。
     * 需要 spring-boot-actuator 依赖。
     *
     * @param queueManager 队列管理器
     * @return 队列监控端点实例
     */
    @Bean
    @ConditionalOnMissingBean(QueueEndpoint.class)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public QueueEndpoint queueEndpoint(QueueManager queueManager) {
        log.info("[Queue] 注册消息队列 Actuator 端点");
        return new QueueEndpoint(queueProperties, queueManager);
    }

    // ==================== Micrometer 指标配置 ====================

    /**
     * 创建消息队列 Micrometer 指标桥接器
     *
     * <p>当 classpath 中存在 Micrometer MeterRegistry 时自动注册。
     * 将 QueueManager 中所有队列的 MessageMetrics 暴露为 Prometheus 指标。
     *
     * @param queueManager 队列管理器
     * @param meterRegistry MeterRegistry 实例（可选依赖）
     * @return 指标桥接器实例，当 MeterRegistry 不可用时返回 null
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean({QueueManager.class, MeterRegistry.class})
    @ConditionalOnMissingBean(QueueMetricsBinder.class)
    public QueueMetricsBinder queueMetricsBinder(QueueManager queueManager,
                                                   ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            log.info("[Queue] MeterRegistry 不可用，跳过 Micrometer 指标注册");
            return null;
        }
        QueueMetricsBinder binder = new QueueMetricsBinder(queueManager);
        binder.bindTo(meterRegistry);
        log.info("[Queue] 创建消息队列 Micrometer 指标桥接器");
        return binder;
    }
}
