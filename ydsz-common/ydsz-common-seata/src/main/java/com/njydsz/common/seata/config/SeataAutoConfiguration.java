package com.njydsz.common.seata.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import com.njydsz.common.seata.api.DistributedTransactionManager;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.api.TccTransactionLogStore;
import com.njydsz.common.seata.api.XidPropagator;
import com.njydsz.common.seata.impl.DefaultXidPropagator;
import com.njydsz.common.seata.impl.InMemoryTccTransactionLogStore;
import com.njydsz.common.seata.impl.DbTccTransactionLogStore;
import com.njydsz.common.seata.impl.LocalTransactionManager;
import com.njydsz.common.seata.impl.RedisTccTransactionLogStore;
import com.njydsz.common.seata.impl.SagaOrchestrator;
import com.njydsz.common.seata.impl.SeataTransactionManager;
import com.njydsz.common.seata.impl.TccTransactionManager;
import com.njydsz.common.seata.impl.TccTransactionRecoveryScanner;
import com.njydsz.common.seata.interceptor.FeignXidRequestInterceptor;
import com.njydsz.common.seata.interceptor.XidServletFilter;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.health.SeataHealthIndicator;
import com.njydsz.common.seata.metrics.SeataMetrics;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.context.annotation.Configuration;
/**
 * 分布式事务自动配置
 *
 * <p>根据类路径和配置自动选择事务管理器实现：
 * <ul>
 *   <li>Seata 在类路径且 {@code default-type=SEATA_AT} → SeataTransactionManager（待实现）</li>
 *   <li>{@code default-type=TCC} → TccTransactionManager（带事务日志 + 恢复扫描）</li>
 *   <li>默认 → LocalTransactionManager（降级）</li>
 * </ul>
 *
 * <p><b>P0-2 修复</b>：{@code @ConditionalOnClass} 类名从 {@code io.seata}（1.x）
 * 修正为 {@code org.apache.seata}（2.x），匹配项目使用的 Seata 2.5.0。
 *
 * <p><b>P0-4/P0-11/P0-12</b>：注册 {@link TccTransactionLogStore}（内存版）和
 * {@link TccTransactionRecoveryScanner}（定时恢复扫描）。
 *
 * <p><b>P1-4 修复</b>：新增 {@link RedisTccTransactionLogStore} 注册路径。
 * 当 {@code ydsz.seata.tcc-log-store=redis} 且类路径存在 {@code RedisTemplate} 时
 * 自动切换到 Redis 实现，支持跨服务事务状态共享；否则回退到 {@link InMemoryTccTransactionLogStore}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = "ydsz.seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeataAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeataAutoConfiguration.class);

    /**
     * TCC 事务日志存储（内存版，默认）
     *
     * <p>仅当 {@code ydsz.seata.tcc-log-store} 未设置为 {@code redis} 或
     * 类路径无 {@code RedisTemplate} 时注册。
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionLogStore.class)
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "tcc-log-store",
            havingValue = "memory", matchIfMissing = true)
    public TccTransactionLogStore inMemoryTccTransactionLogStore() {
        return new InMemoryTccTransactionLogStore();
    }

    /**
     * TCC 事务日志存储（Redis 版，生产环境推荐）
     *
     * <p>当 {@code ydsz.seata.tcc-log-store=redis} 且类路径存在
     * {@code RedisTemplate} 时注册，支持跨服务事务状态共享与持久化。
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionLogStore.class)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "tcc-log-store", havingValue = "redis")
    public TccTransactionLogStore redisTccTransactionLogStore(
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
            SeataProperties properties) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("ydsz.seata.tcc-log-store=redis but no RedisTemplate available, fallback to InMemory");
            return new InMemoryTccTransactionLogStore();
        }
        Duration retention = Duration.ofHours(Math.max(1, properties.getTccLogRedisRetentionHours()));
        return new RedisTccTransactionLogStore(
                redisTemplate,
                properties.getTccLogRedisKeyPrefix(),
                retention);
    }

    /**
     * TCC 事务日志存储（DB 版，生产环境强持久化）
     *
     * <p>当 {@code ydsz.seata.tcc-log-store=db} 且类路径存在 {@code JdbcTemplate} 时注册，
     * 支持跨服务事务状态共享，无需 Redis。适用于不使用 Redis 但需要持久化事务日志的生产环境。
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionLogStore.class)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "tcc-log-store", havingValue = "db")
    public TccTransactionLogStore dbTccTransactionLogStore(
            JdbcTemplate jdbcTemplate,
            SeataProperties properties) {
        return new DbTccTransactionLogStore(jdbcTemplate, properties.getTccLogDbTable());
    }

    /**
     * TCC 事务管理器
     *
     * <p>集成事务日志存储，解决空回滚/悬挂/幂等三大问题，支持 Confirm/Cancel 重试。
     *
     * <p>可通过 {@code ydsz.seata.tcc-enabled=false} 关闭
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionManager.class)
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "tcc-enabled", havingValue = "true", matchIfMissing = true)
    public TccTransactionManager tccTransactionManager(
            ObjectProvider<TccTransactionLogStore> logStoreProvider,
            SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        return new TccTransactionManager(logStoreProvider.getIfAvailable(), properties, metricsProvider, auditProvider);
    }

    /**
     * TCC 事务恢复扫描器
     *
     * <p>定时扫描超时未完成的 TCC 分支事务，重新执行 Confirm 或 Cancel。
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionRecoveryScanner.class)
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "tcc-enabled", havingValue = "true", matchIfMissing = true)
    public TccTransactionRecoveryScanner tccTransactionRecoveryScanner(
            TccTransactionLogStore logStore,
            SeataProperties properties,
            TccTransactionManager tccManager) {
        return new TccTransactionRecoveryScanner(logStore, properties, tccManager);
    }

    /**
     * 默认分布式事务管理器
     *
     * <p>根据 {@code ydsz.seata.default-type} 选择实现
     */
    @Bean
    @ConditionalOnMissingBean(DistributedTransactionManager.class)
    public DistributedTransactionManager distributedTransactionManager(
            SeataProperties properties,
            ObjectProvider<PlatformTransactionManager> txManagerProvider,
            ObjectProvider<TccTransactionManager> tccManagerProvider,
            ObjectProvider<SeataTransactionManager> seataTmProvider,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {

        if (properties.getDefaultType() == TransactionType.TCC) {
            TccTransactionManager tcc = tccManagerProvider.getIfAvailable();
            if (tcc != null) {
                return tcc;
            }
            return new TccTransactionManager();
        }

        if (properties.getDefaultType() == TransactionType.SEATA_AT) {
            // delegate to SeataTransactionManager
            SeataTransactionManager seataTm = seataTmProvider.getIfAvailable();
            if (seataTm != null) { return seataTm; }
        }

        PlatformTransactionManager txManager = txManagerProvider.getIfAvailable();
        if (txManager == null) {
            throw new IllegalStateException(
                    "No PlatformTransactionManager available. "
                    + "Ensure spring-dataSource / jdbc starter is on the classpath, "
                    + "or set ydsz.seata.default-type=TCC to use TCC mode without DataSource.");
        }
        return new LocalTransactionManager(txManager, metricsProvider, auditProvider);
    }

    /**
     * XID 传播器（P0-6）
     */
    @Bean
    @ConditionalOnMissingBean(XidPropagator.class)
    public XidPropagator xidPropagator() {
        return new DefaultXidPropagator();
    }

    /**
     * Feign XID 请求拦截器（当 Feign 在类路径时注册）
     */
    @Bean
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnMissingBean(FeignXidRequestInterceptor.class)
    public FeignXidRequestInterceptor feignXidRequestInterceptor(XidPropagator xidPropagator) {
        return new FeignXidRequestInterceptor(xidPropagator);
    }

    /**
     * XID Servlet 过滤器（当 Spring Web 在类路径时注册）
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnMissingBean(XidServletFilter.class)
    public XidServletFilter xidServletFilter(XidPropagator xidPropagator) {
        return new XidServletFilter(xidPropagator);
    }

    /**
     * 事务审计日志（P1-3）
     */
    @Bean
    @ConditionalOnMissingBean(TransactionAuditLogger.class)
    public TransactionAuditLogger transactionAuditLogger() {
        return new TransactionAuditLogger();
    }

    /**
     * 事务指标采集（P1-2）
     *
     * <p>当 Micrometer 在类路径时注册
     */
    @Bean
    @ConditionalOnMissingBean(SeataMetrics.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public SeataMetrics seataMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        return new SeataMetrics(registryProvider);
    }

    /**
     * 分布式事务健康检查（P1-1）
     *
     * <p>当 Spring Boot Health 在类路径时注册
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    @ConditionalOnMissingBean(SeataHealthIndicator.class)
    public SeataHealthIndicator seataHealthIndicator(
            SeataProperties properties,
            ObjectProvider<SeataTransactionManager> seataTmProvider,
            ObjectProvider<TccTransactionLogStore> logStoreProvider) {
        return new SeataHealthIndicator(properties, seataTmProvider, logStoreProvider);
    }

    /**
     * SAGA 事务编排器（P0-3）
     *
     * <p>可通过 {@code ydsz.seata.saga-enabled=false} 关闭
     */
    @Bean
    @ConditionalOnMissingBean(SagaOrchestrator.class)
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "saga-enabled", havingValue = "true", matchIfMissing = true)
    public SagaOrchestrator sagaOrchestrator(
            SeataProperties properties,
            ObjectProvider<SeataMetrics> metricsProvider,
            ObjectProvider<TransactionAuditLogger> auditProvider) {
        return new SagaOrchestrator(properties, metricsProvider, auditProvider);
    }

    /**
     * Seata AT 模式配置
     *
     * <p>当 Seata 在类路径且 {@code seata-at-enabled=true} 时注册
     * {@link SeataTransactionManager}，使用 Seata 原生 API 实现 AT 模式。
     */
    @Configuration
    @ConditionalOnClass(name = "org.apache.seata.tm.api.GlobalTransactionContext")
    @ConditionalOnProperty(prefix = "ydsz.seata", name = "seata-at-enabled", havingValue = "true", matchIfMissing = true)
    public static class SeataAtConfiguration {

        /**
         * 注册 Seata AT 事务管理器 Bean。
         *
         * <p>使用 Seata 原生 {@link org.apache.seata.tm.api.GlobalTransactionContext} API，
         * 在 Seata AT 启用时装配；无自定义 Bean 时注册，供 {@link DistributedTransactionManager} 委派。
         */
        @Bean
        @ConditionalOnMissingBean(SeataTransactionManager.class)
        public SeataTransactionManager seataTransactionManager(
                ObjectProvider<SeataMetrics> metricsProvider,
                ObjectProvider<TransactionAuditLogger> auditProvider) {
            return new SeataTransactionManager(metricsProvider, auditProvider);
        }
    }
}
