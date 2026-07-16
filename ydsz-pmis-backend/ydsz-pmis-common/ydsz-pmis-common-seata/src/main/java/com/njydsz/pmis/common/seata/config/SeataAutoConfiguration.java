package com.njydsz.pmis.common.seata.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;
import com.njydsz.pmis.common.seata.api.TransactionType;
import com.njydsz.pmis.common.seata.api.TccTransactionLogStore;
import com.njydsz.pmis.common.seata.api.XidPropagator;
import com.njydsz.pmis.common.seata.impl.DefaultXidPropagator;
import com.njydsz.pmis.common.seata.impl.GlobalTransactionExecutor;
import com.njydsz.pmis.common.seata.impl.InMemoryTccTransactionLogStore;
import com.njydsz.pmis.common.seata.impl.LocalTransactionManager;
import com.njydsz.pmis.common.seata.impl.SagaOrchestrator;
import com.njydsz.pmis.common.seata.impl.SeataGlobalTransactionExecutor;
import com.njydsz.pmis.common.seata.impl.SeataTransactionManager;
import com.njydsz.pmis.common.seata.impl.TccTransactionManager;
import com.njydsz.pmis.common.seata.impl.TccTransactionRecoveryScanner;
import com.njydsz.pmis.common.seata.interceptor.FeignXidRequestInterceptor;
import com.njydsz.pmis.common.seata.interceptor.XidServletFilter;
import com.njydsz.pmis.common.seata.audit.TransactionAuditLogger;
import com.njydsz.pmis.common.seata.health.SeataHealthIndicator;
import com.njydsz.pmis.common.seata.metrics.SeataMetrics;

import io.micrometer.core.instrument.MeterRegistry;

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
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = "pmis.seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeataAutoConfiguration {

    /**
     * TCC 事务日志存储（内存版，默认）
     *
     * <p>生产环境可覆盖为 {@code JdbcTccTransactionLogStore} 配合数据库持久化
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionLogStore.class)
    public TccTransactionLogStore tccTransactionLogStore() {
        return new InMemoryTccTransactionLogStore();
    }

    /**
     * TCC 事务管理器
     *
     * <p>集成事务日志存储，解决空回滚/悬挂/幂等三大问题，支持 Confirm/Cancel 重试。
     *
     * <p>可通过 {@code pmis.seata.tcc-enabled=false} 关闭
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionManager.class)
    @ConditionalOnProperty(prefix = "pmis.seata", name = "tcc-enabled", havingValue = "true", matchIfMissing = true)
    public TccTransactionManager tccTransactionManager(
            ObjectProvider<TccTransactionLogStore> logStoreProvider,
            SeataProperties properties) {
        TccTransactionLogStore logStore = logStoreProvider.getIfAvailable();
        if (logStore != null) {
            return new TccTransactionManager(logStore, properties);
        }
        return new TccTransactionManager();
    }

    /**
     * TCC 事务恢复扫描器
     *
     * <p>定时扫描超时未完成的 TCC 分支事务，重新执行 Confirm 或 Cancel。
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionRecoveryScanner.class)
    @ConditionalOnProperty(prefix = "pmis.seata", name = "tcc-enabled", havingValue = "true", matchIfMissing = true)
    public TccTransactionRecoveryScanner tccTransactionRecoveryScanner(
            TccTransactionLogStore logStore,
            SeataProperties properties,
            TccTransactionManager tccManager) {
        return new TccTransactionRecoveryScanner(logStore, properties, tccManager);
    }

    /**
     * 默认分布式事务管理器
     *
     * <p>根据 {@code pmis.seata.default-type} 选择实现
     */
    @Bean
    @ConditionalOnMissingBean(DistributedTransactionManager.class)
    public DistributedTransactionManager distributedTransactionManager(
            SeataProperties properties,
            ObjectProvider<PlatformTransactionManager> txManagerProvider,
            ObjectProvider<TccTransactionManager> tccManagerProvider) {

        if (properties.getDefaultType() == TransactionType.TCC) {
            TccTransactionManager tcc = tccManagerProvider.getIfAvailable();
            if (tcc != null) {
                return tcc;
            }
            return new TccTransactionManager();
        }

        if (properties.getDefaultType() == TransactionType.SEATA_AT) {
            // SeataTransactionManager 待实现，降级为 Local
        }

        PlatformTransactionManager txManager = txManagerProvider.getIfAvailable();
        if (txManager == null) {
            throw new IllegalStateException(
                    "No PlatformTransactionManager available. "
                    + "Ensure spring-dataSource / jdbc starter is on the classpath, "
                    + "or set pmis.seata.default-type=TCC to use TCC mode without DataSource.");
        }
        return new LocalTransactionManager(txManager);
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
            ObjectProvider<GlobalTransactionExecutor> globalExecutorProvider,
            ObjectProvider<TccTransactionLogStore> logStoreProvider) {
        return new SeataHealthIndicator(properties, globalExecutorProvider, logStoreProvider);
    }

    /**
     * SAGA 事务编排器（P0-3）
     *
     * <p>可通过 {@code pmis.seata.saga-enabled=false} 关闭
     */
    @Bean
    @ConditionalOnMissingBean(SagaOrchestrator.class)
    @ConditionalOnProperty(prefix = "pmis.seata", name = "saga-enabled", havingValue = "true", matchIfMissing = true)
    public SagaOrchestrator sagaOrchestrator(SeataProperties properties) {
        return new SagaOrchestrator(properties);
    }

    /**
     * Seata AT 模式配置
     *
     * <p>当 Seata 在类路径且 {@code seata-at-enabled=true} 时注册
     * {@link GlobalTransactionExecutor} 和 {@link SeataTransactionManager}。
     */
    @org.springframework.context.annotation.Configuration
    @ConditionalOnClass(name = "org.apache.seata.tm.api.GlobalTransactionContext")
    @ConditionalOnProperty(prefix = "pmis.seata", name = "seata-at-enabled", havingValue = "true", matchIfMissing = true)
    public static class SeataAtConfiguration {

        @Bean
        @ConditionalOnMissingBean(GlobalTransactionExecutor.class)
        public GlobalTransactionExecutor globalTransactionExecutor() throws Exception {
            return new SeataGlobalTransactionExecutor();
        }
    }
}
