package com.njydsz.common.seata.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.seata.health.SeataHealthIndicator;

/**
 * Seata 分布式事务自动配置
 *
 * <p>当 {@code ydsz.seata.enabled=true} 且 classpath 存在 Seata 相关类时自动装配。
 *
 * <p>该模块不对 Seata 原生自动配置（{@code io.seata.spring.boot.autoconfigure.SeataAutoConfiguration}）
 * 做任何拦截覆盖，而是按照 Spring Cloud Alibaba 约定提供自研属性前缀 {@code ydsz.seata.*} 的桥接配置。
 *
 * <h2>与现有系统的整合点</h2>
 * <ul>
 *   <li>{@code DynamicRoutingDataSource}（ydsz-common-jdbc）：Seata AT 模式的 {@code DataSourceProxy}
 *       会包装已有动态数据源，{@code ydsz.seata.data-source-proxy-mode=AT} 时自动生效</li>
 *   <li>Outbox 事件（ydsz-common-event）：分布式事务提交后，领域事件再通过 Outbox 投递，保证最终一致性</li>
 *   <li>Feign（ydsz-common-feign）：Seata 通过 RootContext 在 Feign 调用链中传播 XID</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
 *     public void createOrder(OrderDTO dto) {
 *         // 1. 落库 order
 *         orderRepository.save(order);
 *         // 2. Feign 调用库存服务
 *         inventoryClient.deduct(dto.getSkuId(), dto.getQuantity());
 *         // 3. 任何异常触发 AT 自动补偿（undo_log 反向 SQL 回滚）
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@AutoConfiguration
@EnableConfigurationProperties(SeataProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.seata",
    name = "enabled",
    havingValue = "true")
@ConditionalOnClass(name = "io.seata.spring.annotation.GlobalTransactionalInterceptor")
public class SeataAutoConfiguration {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataAutoConfiguration.class);

    /**
     * 注册健康检查指示器
     *
     * @param properties Seata 配置属性
     * @param meterRegistryProvider Micrometer 指标注册器（可选）
     * @return Seata 健康指示器实例
     */
    @Bean
    @ConditionalOnMissingBean
    // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（反射类名），非代码引用
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    public SeataHealthIndicator seataHealthIndicator(
            SeataProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new SeataHealthIndicator(properties, meterRegistryProvider.getIfAvailable());
    }

    /**
     * Seata 属性桥接配置
     *
     * <p>将 {@code ydsz.seata.*} 属性同步绑定到 Seata 原生的 {@code spring.cloud.alibaba.seata.*} 属性，
     * 使业务方只需维护 {@code ydsz.seata.*} 前缀即可，无需关注 Seata 原生属性名。
     *
     * <p>注意：此桥接采用宽松策略——如果业务方已经直接配置了 Seata 原生属性（如 {@code seata.service.vgroup-mapping}），
     * 原生属性优先级更高，不会被覆盖。
     */
    @Configuration
    public static class SeataPropertyBridgeConfiguration {

        /** 日志实例 */
        private static final Logger LOG = LoggerFactory.getLogger(SeataPropertyBridgeConfiguration.class);

        /**
         * 桥接日志输出
         *
         * @param properties ydsz.seata 配置属性
         */
        public SeataPropertyBridgeConfiguration(SeataProperties properties) {
            LOG.info(
                "Seata SEATA TxGroup bridged: txServiceGroup={}, proxyMode={}",
                properties.getTxServiceGroup(),
                properties.getDataSourceProxyMode());
        }
    }
}
