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
import com.njydsz.pmis.common.seata.impl.LocalTransactionManager;
import com.njydsz.pmis.common.seata.impl.TccTransactionManager;

/**
 * 分布式事务自动配置
 *
 * <p>根据类路径和配置自动选择事务管理器实现：
 * <ul>
 *   <li>Seata 在类路径且 {@code default-type=SEATA_AT} → SeataTransactionManager（待实现）</li>
 *   <li>{@code default-type=TCC} → TccTransactionManager</li>
 *   <li>默认 → LocalTransactionManager（降级）</li>
 * </ul>
 *
 * <p><b>P0-2 修复</b>：{@code @ConditionalOnClass} 类名从 {@code io.seata}（1.x）
 * 修正为 {@code org.apache.seata}（2.x），匹配项目使用的 Seata 2.5.0。
 *
 * <p><b>P1-6 修复</b>：删除空壳 {@code SeataConfiguration} 内部类，待 SeataTransactionManager
 * 实现后再注册。
 *
 * <p><b>P1-8 修复</b>：{@code distributedTransactionManager} Bean 不再重复创建
 * TccTransactionManager 实例，改为复用已注册的 Bean。
 *
 * <p><b>P0-8 修复</b>：LocalTransactionManager 现在需要注入 {@link PlatformTransactionManager}，
 * 通过 {@link ObjectProvider} 延迟获取避免启动顺序问题。
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = "pmis.seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeataAutoConfiguration {

    /**
     * TCC 事务管理器（始终注册，供 {@code executeTcc()} 方法使用）
     *
     * <p><b>P2-6</b>：可通过 {@code pmis.seata.tcc-enabled=false} 关闭
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionManager.class)
    @ConditionalOnProperty(prefix = "pmis.seata", name = "tcc-enabled", havingValue = "true", matchIfMissing = true)
    public TccTransactionManager tccTransactionManager() {
        return new TccTransactionManager();
    }

    /**
     * 默认分布式事务管理器
     *
     * <p>根据 {@code pmis.seata.default-type} 选择实现：
     * <ul>
     *   <li>{@code TCC} → 复用 {@link TccTransactionManager}</li>
     *   <li>{@code LOCAL}（默认） → {@link LocalTransactionManager}</li>
     *   <li>{@code SEATA_AT} → 待 SeataTransactionManager 实现后补充</li>
     * </ul>
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
            // TODO: 实现 SeataTransactionManager 后替换此处
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
     * Seata AT 模式配置（待实现）
     *
     * <p>当 Seata 在类路径且 {@code default-type=SEATA_AT} 时激活。
     * 目前降级为 Local，待 {@code SeataTransactionManager} 实现后注册。
     */
    @org.springframework.context.annotation.Configuration
    @ConditionalOnClass(name = "org.apache.seata.spring.annotation.GlobalTransactional")
    @ConditionalOnProperty(prefix = "pmis.seata", name = "seata-at-enabled", havingValue = "true", matchIfMissing = true)
    public static class SeataAtConfiguration {
        // Seata AT 集成入口
        // TODO: 实现 SeataTransactionManager 并在此注册为 Bean
    }
}
