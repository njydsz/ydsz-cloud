package com.njydsz.common.seata.config;

import com.njydsz.common.seata.annotation.YdszGlobalTransactional;
import com.njydsz.common.seata.datasource.SeataDynamicDataSourceAdapter;
import com.njydsz.common.seata.fallback.SeataFallbackBeanPostProcessor;
import com.njydsz.common.seata.health.SeataHealthIndicator;
import com.njydsz.common.seata.validator.SeataConfigurationValidator;
import com.njydsz.common.seata.validator.UndoLogSchemaValidator;
import com.njydsz.common.seata.xid.FeignXidRequestInterceptor;
import com.njydsz.common.seata.xid.XidServletFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Seata 分布式事务自动配置
 *
 * <p>当 {@code ydsz.seata.enabled=true} 且 classpath 存在 Seata 相关类时自动装配。
 *
 * <p>该模块不对 Seata 原生自动配置（{@code io.seata.spring.boot.autoconfigure.SeataAutoConfiguration}）
 * 做任何拦截覆盖，而是按照 Spring Cloud Alibaba 约定提供自研属性前缀 {@code ydsz.seata.*} 的桥接配置。
 *
 * <h2>自动注册能力清单</h2>
 * <ul>
 *   <li>{@link SeataHealthIndicator}：健康检查（TC 连通性、TransactionManager 初始化状态）</li>
 *   <li>{@link FeignXidRequestInterceptor}：XID 跨服务传播（Feign 请求拦截器）</li>
 *   <li>{@link XidServletFilter}：XID 接收绑定（Servlet Filter）</li>
 *   <li>{@link SeataDynamicDataSourceAdapter}：AT 模式与 DynamicRoutingDataSource 集成</li>
 *   <li>{@link SeataConfigurationValidator}：启动期配置校验（Fail Fast）</li>
 *   <li>{@link SeataPropertyBridgeConfiguration}：属性桥接日志（ydsz.seata.* → seata.*）</li>
 * </ul>
 *
 * <h2>与现有系统的整合点</h2>
 * <ul>
 *   <li>{@code DynamicRoutingDataSource}（ydsz-common-jdbc）：Seata AT 模式的 {@code DataSourceProxy}
 *       会包装已有动态数据源，{@code ydsz.seata.data-source-proxy-mode=AT} 时自动生效</li>
 *   <li>Outbox 事件（ydsz-common-event）：分布式事务提交后，领域事件再通过 Outbox 投递，保证最终一致性</li>
 *   <li>Feign（ydsz-common-feign）：通过 {@link FeignXidRequestInterceptor} + {@link XidServletFilter}
 *       实现 XID 跨服务传播</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @YdszGlobalTransactional(name = "order-create-order")
 *     public void createOrder(OrderDTO dto) {
 *         orderRepository.save(order);
 *         inventoryClient.deduct(dto.getSkuId(), dto.getQuantity());
 *     }
 * }
 * }</pre>
 *
 * <h2>接入 Checklist（规范 §25.8）</h2>
 * <ul>
 *   <li>pom.xml 中添加 ydsz-common-seata 依赖</li>
 *   <li>application.yml 中配置 ydsz.seata.enabled: true 及 default-type</li>
 *   <li>如使用 TCC 模式：实现 TccAction 接口</li>
 *   <li>如使用 SEATA_AT 模式：确保 undo_log 表已初始化</li>
 *   <li>生产环境：配置 ydsz.seata.xid-sign-secret</li>
 * </ul>
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

    /** Seata 配置属性（注入用于 @PostConstruct 初始化） */
    private final SeataProperties properties;

    /**
     * 构造注入 Seata 配置属性。
     *
     * @param properties Seata 配置属性
     */
    public SeataAutoConfiguration(SeataProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册健康检查指示器（运行时级：含 TC 连通性和 TransactionManager 初始化状态）。
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
        LOG.info("SeataHealthIndicator registered (runtime-level: TC connectivity + TM status)");
        return new SeataHealthIndicator(properties, meterRegistryProvider.getIfAvailable());
    }

    /**
     * 注册 XID 跨服务传播拦截器（Feign 请求 → 注入 XID Header）。
     *
     * <p>规范 YDIZ-TX-004 要求 Feign 调用链透传 XID。
     *
     * @return XID 传播拦截器
     */
    @Bean
    @ConditionalOnMissingBean(name = "seataFeignXidRequestInterceptor")
    // CHECKSTYLE.OFF: RegexpSinglelineJava
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    public FeignXidRequestInterceptor seataFeignXidRequestInterceptor() {
        LOG.info("FeignXidRequestInterceptor registered for XID cross-service propagation");
        return new FeignXidRequestInterceptor();
    }

    /**
     * 注册 XID 签名密钥到 XidServletFilter（静态注入）。
     *
     * <p>当配置了 ydzs.seata.xid-sign-secret 后，Filter 自动启用 HMAC-SHA256 验签。
     */
    @PostConstruct
    public void configureXidSignature() {
        if (properties.getXidSignSecret() != null && !properties.getXidSignSecret().isEmpty()) {
            XidServletFilter.setXidSignSecret(properties.getXidSignSecret());
        }
    }

    /**
     * 注册 XID 接收 Filter（接收上游传播的 XID 并绑定到 RootContext）。
     *
     * <p>规范 YDIZ-TX-004 要求下游服务自动接收 XID。order 为 HIGHEST_PRECEDENCE + 100，
     * 确保在业务 Filter 之前优先执行 XID 绑定。
     *
     * @return XID Filter 注册对象
     */
    @Bean
    @ConditionalOnMissingBean(name = "seataXidServletFilterRegistration")
    // CHECKSTYLE.OFF: RegexpSinglelineJava
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    public FilterRegistrationBean<XidServletFilter> seataXidServletFilterRegistration() {
        FilterRegistrationBean<XidServletFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XidServletFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setName("seataXidServletFilter");
        LOG.info("XidServletFilter registered with URL pattern /* (order={})",
            Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }

    /**
     * 注册 Seata DynamicRoutingDataSource 适配器。
     *
     * <p>当 classpath 同时存在 Seata DataSourceProxy 和 DynamicRoutingDataSource 时自动生成，
     * 解决 AT 模式与 YDSZ 动态数据源的路由冲突。
     *
     * @return Seata 动态数据源适配器
     */
    @Bean
    @ConditionalOnMissingBean(SeataDynamicDataSourceAdapter.class)
    // CHECKSTYLE.OFF: RegexpSinglelineJava
    @ConditionalOnClass(
        name = {
            "io.seata.rm.datasource.DataSourceProxy",
            "com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource"
        })
    // CHECKSTYLE.ON: RegexpSinglelineJava
    public SeataDynamicDataSourceAdapter seataDynamicRoutingDataSource() {
        LOG.info("SeataDynamicDataSourceAdapter registered for AT mode + dynamic datasource integration");
        return new SeataDynamicDataSourceAdapter();
    }

    /**
     * 注册 undo_log Schema 启动期校验器。
     *
     * <p>当配置 undo-log.validate-schema=true 时，启动期检查 undo_log 表是否存在。
     * 仅在 DataSource Bean 存在时注册。
     *
     * @param dataSourceProvider 应用主数据源（由 ydsz-common-jdbc 或其他数据源组件提供）
     * @return Schema 校验器
     */
    @Bean
    @ConditionalOnMissingBean
    // CHECKSTYLE.OFF: RegexpSinglelineJava
    @ConditionalOnClass(name = "javax.sql.DataSource")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    @ConditionalOnProperty(
        prefix = "ydsz.seata.undo-log",
        name = "validate-schema",
        havingValue = "true")
    public UndoLogSchemaValidator undoLogSchemaValidator(ObjectProvider<DataSource> dataSourceProvider) {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            LOG.warn("DataSource not available, undo_log schema validation disabled");
            return null;
        }
        LOG.info("UndoLogSchemaValidator registered (startup validation enabled)");
        return new UndoLogSchemaValidator(properties, ds);
    }

    /**
     * 注册 Seata 事务降级处理器。
     *
     * <p>为标注 {@code @YdszGlobalTransactional(fallbackMode != FAIL)} 的 Bean 生成代理，
     * 拦截 Seata 不可用异常并按配置策略降级。
     *
     * @return 降级处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public SeataFallbackBeanPostProcessor seataFallbackBeanPostProcessor() {
        LOG.info("SeataFallbackBeanPostProcessor registered (fallback mode support: FAIL / LOCAL_TRANSACTION / SKIP)");
        return new SeataFallbackBeanPostProcessor();
    }

    /**
     * 注册启动期配置校验器（Fail Fast）。
     *
     * <p>在 Bean 初始化时校验 ydzs.seata.* 配置合法性，不合规时抛出 IllegalArgumentException。
     *
     * @param properties Seata 配置属性
     * @return 配置校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public SeataConfigurationValidator seataConfigurationValidator(SeataProperties properties) {
        return new SeataConfigurationValidator(properties);
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
         * @param properties ydzs.seata 配置属性
         */
        public SeataPropertyBridgeConfiguration(SeataProperties properties) {
            LOG.info(
                "Seata SEATA TxGroup bridged: txServiceGroup={}, proxyMode={}, timeout={}ms",
                properties.getTxServiceGroup(),
                properties.getDataSourceProxyMode(),
                properties.getTm().getGlobalTransactionTimeout());
        }
    }
}
