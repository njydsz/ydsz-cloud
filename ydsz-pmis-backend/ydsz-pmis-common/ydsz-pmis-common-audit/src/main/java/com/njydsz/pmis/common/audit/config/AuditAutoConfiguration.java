package com.njydsz.pmis.common.audit.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.pmis.common.audit.aspect.AuditAspect;
import com.njydsz.pmis.common.audit.core.AsyncAuditRecorder;
import com.njydsz.pmis.common.audit.core.AuditQueryService;
import com.njydsz.pmis.common.audit.core.AuditRecorder;
import com.njydsz.pmis.common.audit.core.AuditStorage;
import com.njydsz.pmis.common.audit.core.DefaultAuditQueryService;
import com.njydsz.pmis.common.audit.core.DefaultAuditRecorder;
import com.njydsz.pmis.common.audit.core.DisruptorAuditRecorder;
import com.njydsz.pmis.common.audit.health.AuditHealthIndicator;
import com.njydsz.pmis.common.audit.sharding.DailyShardingStrategy;
import com.njydsz.pmis.common.audit.sharding.MonthlyShardingStrategy;
import com.njydsz.pmis.common.audit.sharding.TableShardingStrategy;
import com.njydsz.pmis.common.audit.sharding.YearlyShardingStrategy;
import com.njydsz.pmis.common.audit.storage.DefaultAuditStorage;
import com.njydsz.pmis.common.audit.storage.JdbcAuditStorage;
import com.njydsz.pmis.common.audit.template.AuditTemplateProcessor;

import lombok.RequiredArgsConstructor;

/**
 * 审计模块自动配置
 * <p>
 * 通过 {@code @EnableYdszAudit} 启用审计模块后，自动注册以下核心 Bean：
 * <ul>
 *   <li>{@link AuditAspect}：审计切面，拦截 {@link com.njydsz.pmis.common.audit.annotation.Audit} 注解</li>
 *   <li>{@link AuditTemplateProcessor}：SpEL 模板解析器</li>
 *   <li>{@link com.njydsz.pmis.common.audit.core.AuditStorage}：审计日志存储（JDBC / 控制台）</li>
 *   <li>{@link com.njydsz.pmis.common.audit.core.AuditRecorder}：异步/同步审计记录器</li>
 *   <li>{@link com.njydsz.pmis.common.audit.sharding.TableShardingStrategy}：分表策略</li>
 *   <li>{@link com.njydsz.pmis.common.audit.core.AuditQueryService}：审计日志查询服务</li>
 *   <li>{@link com.njydsz.pmis.common.audit.health.AuditHealthIndicator}：健康检查指示器</li>
 * </ul>
 * </p>
 *
 * <p>优先级与覆盖规则：所有 Bean 均标注 {@code @ConditionalOnMissingBean}，
 * 业务方可提供同名 Bean 进行覆盖。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "ydsz.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuditAutoConfiguration.class);

    /** 异步审计记录器引用，用于优雅停机时调用 shutdown */
    private AsyncAuditRecorder asyncAuditRecorder;

    /**
     * 创建 SpEL 模板处理器 Bean
     *
     * @return SpEL 模板处理器
     */
    @Bean
    @ConditionalOnMissingBean(AuditTemplateProcessor.class)
    public AuditTemplateProcessor auditTemplateProcessor() {
        log.info("初始化审计模板处理器: AuditTemplateProcessor");
        return new AuditTemplateProcessor();
    }

    /**
     * 创建分表策略 Bean
     * 当启用分表功能时自动创建
     *
     * @param properties 审计配置属性
     * @return 分表策略，若未启用分表则返回 null
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.audit.sharding", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(TableShardingStrategy.class)
    public TableShardingStrategy tableShardingStrategy(AuditProperties properties) {
        String type = properties.getShardingType();
        log.info("初始化分表策略: type={}", type);
        return switch (type.toLowerCase()) {
            case "daily" -> new DailyShardingStrategy();
            case "yearly" -> new YearlyShardingStrategy();
            default -> new MonthlyShardingStrategy();
        };
    }

    /**
     * 创建 JDBC 审计日志存储 Bean
     * 当存在 DataSource 且未提供自定义 AuditStorage 时创建
     *
     * @param dataSource         数据源
     * @param shardingStrategy   分表策略（可选）
     * @param properties         审计配置属性
     * @return JDBC 审计日志存储
     */
    @Bean
    @ConditionalOnMissingBean(AuditStorage.class)
    @ConditionalOnBean(DataSource.class)
    public AuditStorage jdbcAuditStorage(DataSource dataSource,
                                         TableShardingStrategy shardingStrategy,
                                         AuditProperties properties) {
        String baseTableName = properties.getShardingBaseTableName();
        log.info("初始化 JDBC 审计日志存储: JdbcAuditStorage, 分表策略={}, 基础表名={}",
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED", baseTableName);
        return new JdbcAuditStorage(dataSource, shardingStrategy, baseTableName);
    }

    /**
     * 创建默认审计日志存储 Bean
     * 当系统中不存在 DataSource 时降级为控制台输出
     *
     * @return 默认审计日志存储
     */
    @Bean
    @ConditionalOnMissingBean(AuditStorage.class)
    public AuditStorage defaultAuditStorage() {
        log.info("初始化默认审计日志存储: DefaultAuditStorage(控制台输出)，未检测到 DataSource，降级使用控制台存储");
        return new DefaultAuditStorage();
    }

    /**
     * 创建审计日志切面 Bean
     *
     * @param eventPublisher 事件发布器
     * @param properties 审计配置属性
     * @param templateProcessor SpEL 模板处理器
     * @return 审计日志切面
     */
    @Bean
    @ConditionalOnMissingBean(AuditAspect.class)
    @ConditionalOnClass(name = "com.njydsz.pmis.common.json.Json")
    public AuditAspect auditAspect(ApplicationEventPublisher eventPublisher, AuditProperties properties, AuditTemplateProcessor templateProcessor) {
        log.info("初始化审计日志切面: AuditAspect, 存储策略={}", properties.getStorageType());
        return new AuditAspect(eventPublisher, properties, templateProcessor);
    }

    /**
     * 审计专用异步线程池
     * 与主业务线程池隔离，避免审计 IO 影响核心链路
     *
     * @param properties 审计配置属性
     * @return 异步执行器
     */
    @Bean("auditAsyncExecutor")
    public Executor auditAsyncExecutor(AuditProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("audit-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * 创建审计事件监听器
     * 接收审计事件并委托给 AuditRecorder 进行异步批量保存
     *
     * @param auditRecorder 审计记录器
     * @return 事件监听器 Bean 方法
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditEventListener auditEventListener(AuditRecorder auditRecorder) {
        return new AuditEventListener(auditRecorder);
    }

    /**
     * 创建异步审计记录器 Bean
     * 当存在 DataSource 且未提供自定义 AuditRecorder 时，根据配置决定是否启用异步模式
     * 优先使用 Disruptor（如果 classpath 中存在），否则使用 LinkedBlockingQueue 实现
     *
     * @param dataSource       数据源
     * @param properties       审计配置属性
     * @param shardingStrategy 分表策略（可选）
     * @return 异步审计记录器，若不满足条件则返回 null
     */
    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    @ConditionalOnProperty(prefix = "ydsz.audit", name = "async", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnClass(name = "com.njydsz.pmis.common.json.Json")
    public AuditRecorder asyncAuditRecorder(DataSource dataSource, AuditProperties properties,
                                            TableShardingStrategy shardingStrategy) {
        String baseTableName = properties.getShardingBaseTableName();
        AuditProperties.AsyncProperties asyncProps = properties.getAsync();

        // 优先使用 Disruptor（如果 classpath 中存在）
        if (isDisruptorAvailable()) {
            log.info("初始化 Disruptor 审计记录器: DisruptorAuditRecorder, RingBuffer容量={}, 批量阈值={}, 分表策略={}",
                    asyncProps.getQueueCapacity(), asyncProps.getBatchSize(),
                    shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED");
            DisruptorAuditRecorder recorder = new DisruptorAuditRecorder(dataSource, properties, shardingStrategy, baseTableName, properties.getAsync().getWaitStrategy());
            this.asyncAuditRecorder = null; // Disruptor 自己管理停机
            return recorder;
        }

        // 降级使用 LinkedBlockingQueue 实现
        log.info("初始化异步审计记录器: AsyncAuditRecorder, 队列容量={}, 批量阈值={}, 刷新间隔={}ms, 分表策略={}",
                asyncProps.getQueueCapacity(), asyncProps.getBatchSize(), asyncProps.getBatchIntervalMillis(),
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED");
        AsyncAuditRecorder recorder = new AsyncAuditRecorder(dataSource, properties, shardingStrategy, baseTableName);
        this.asyncAuditRecorder = recorder;
        return recorder;
    }

    /**
     * 检查 Disruptor 是否在 classpath 中可用
     */
    private boolean isDisruptorAvailable() {
        try {
            Class.forName("com.lmax.disruptor.Disruptor");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 创建默认审计记录器 Bean
     * 当系统中不存在 AuditRecorder 类型的 Bean 且未启用异步模式时创建
     *
     * @param auditStorage 审计日志存储
     * @return 默认审计记录器
     */
    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    @ConditionalOnProperty(prefix = "ydsz.audit", name = "async", havingValue = "false", matchIfMissing = false)
    public AuditRecorder auditRecorder(AuditStorage auditStorage) {
        log.info("初始化默认审计记录器: DefaultAuditRecorder");
        return new DefaultAuditRecorder(auditStorage);
    }

    /**
     * 创建默认审计查询服务 Bean
     * 需要 DataSource 才可用，用于从数据库查询审计日志
     *
     * @param dataSource       数据源
     * @param shardingStrategy 分表策略（可选）
     * @param properties       审计配置属性
     * @return 默认审计查询服务
     */
    @Bean
    @ConditionalOnMissingBean(AuditQueryService.class)
    @ConditionalOnBean(DataSource.class)
    public AuditQueryService auditQueryService(DataSource dataSource,
                                               TableShardingStrategy shardingStrategy,
                                               AuditProperties properties) {
        String baseTableName = properties.getShardingBaseTableName();
        log.info("初始化默认审计查询服务: DefaultAuditQueryService, 分表策略={}",
                shardingStrategy != null ? shardingStrategy.getShardType() : "DISABLED");
        return new DefaultAuditQueryService(dataSource, shardingStrategy, baseTableName);
    }

    /**
     * 创建审计模块健康检查指示器 Bean
     * 当存在 HealthIndicator 类且启用审计模块时创建
     *
     * @param dataSource 数据源
     * @param properties 审计配置属性
     * @return 审计健康检查指示器
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnBean(AuditRecorder.class)
    @ConditionalOnProperty(prefix = "ydsz.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "auditHealthIndicator")
    public AuditHealthIndicator auditHealthIndicator(AuditRecorder auditRecorder, AuditProperties properties) {
        log.info("初始化审计健康检查指示器: AuditHealthIndicator");
        return new AuditHealthIndicator(auditRecorder, properties);
    }

    /**
     * 优雅停机时确保异步记录器队列中剩余日志全部写入
     */
    @PreDestroy
    public void destroy() {
        if (asyncAuditRecorder != null) {
            log.info("审计模块关闭中，执行异步记录器优雅停机...");
            asyncAuditRecorder.shutdown();
        }
    }
}
