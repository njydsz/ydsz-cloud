package com.njydsz.pmis.common.seata.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.pmis.common.seata.api.DistributedTransactionManager;
import com.njydsz.pmis.common.seata.api.TransactionType;
import com.njydsz.pmis.common.seata.impl.LocalTransactionManager;
import com.njydsz.pmis.common.seata.impl.TccTransactionManager;

/**
 * 分布式事务自动配置
 *
 * <p>根据类路径和配置自动选择事务管理器实现：
 * <ul>
 *   <li>Seata 在类路径 → SeataTransactionManager（待实现）</li>
 *   <li>仅 Local → LocalTransactionManager（降级）</li>
 *   <li>TccTransactionManager 始终注册（供 executeTcc 方法使用）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(prefix = "pmis.seata", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeataAutoConfiguration {

    /**
     * TCC 事务管理器（始终注册）
     */
    @Bean
    @ConditionalOnMissingBean(TccTransactionManager.class)
    public TccTransactionManager tccTransactionManager() {
        return new TccTransactionManager();
    }

    /**
     * 分布式事务管理器（默认 Local 降级）
     *
     * <p>当 Seata 在类路径时，可通过 {@code pmis.seata.default-type=SEATA_AT}
     * 切换为 Seata 实现。
     */
    @Configuration
    @ConditionalOnClass(name = "io.seata.spring.annotation.GlobalTransactional")
    @ConditionalOnProperty(prefix = "pmis.seata", name = "default-type", havingValue = "SEATA_AT")
    public static class SeataConfiguration {
        // Seata 集成待实现
        // 当 Seata 在类路径时，注册 SeataTransactionManager
        // 目前降级为 Local
    }

    /**
     * 默认事务管理器（Local 降级）
     */
    @Bean
    @ConditionalOnMissingBean(DistributedTransactionManager.class)
    public DistributedTransactionManager distributedTransactionManager(SeataProperties properties) {
        if (properties.getDefaultType() == TransactionType.TCC) {
            return new TccTransactionManager();
        }
        return new LocalTransactionManager();
    }
}
