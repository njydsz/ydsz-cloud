package com.remisoft.common.event.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.remisoft.common.event.api.EventStore;
import com.remisoft.common.event.gateway.EventPublishGateway;
import com.remisoft.common.event.gateway.NoopEventPublishGateway;
import com.remisoft.common.event.health.OutboxHealthIndicator;
import com.remisoft.common.event.model.DatabaseDialect;
import com.remisoft.common.event.processor.OutboxProcessor;
import com.remisoft.common.event.repository.OutboxRepository;
import com.remisoft.common.event.service.OutboxEventStore;
import com.remisoft.common.event.service.OutboxService;

import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;

/**
 * Outbox 事件模块自动配置
 *
 * <p>当 {@code remi.event.outbox.enabled=true}（默认）且容器中存在
 * {@link JdbcTemplate} 时自动装配。
 *
 * <p>投递网关优先级：
 * <ol>
 *   <li>容器中已有的 {@link EventPublishGateway} Bean（业务模块自定义）</li>
 *   <li>当 RocketMQTemplate 在 classpath 时，通过 {@link RocketMqGatewayConfiguration} 自动注册</li>
 *   <li>降级为 {@link NoopEventPublishGateway}（生产环境应设置 fail-on-noop=true 阻止启动）</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(prefix = "remi.event.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
@Import(RocketMqGatewayConfiguration.class)
public class EventAutoConfiguration {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(EventAutoConfiguration.class);

    private OutboxProcessor outboxProcessor;
    private EventPublishGateway activeGateway;
    private EventProperties activeProperties;

    /**
     * 创建 Outbox 仓储实例
     *
     * @param jdbcTemplate       JDBC 模板
     * @param properties         事件配置属性
     * @param dataSourceProvider 数据源提供者（用于检测数据库方言）
     * @return Outbox 仓储实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate,
                                              EventProperties properties,
                                              ObjectProvider<DataSource> dataSourceProvider) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        DatabaseDialect dialect = dataSource != null
                ? DatabaseDialect.detect(dataSource)
                : DatabaseDialect.UNKNOWN;
        log.info("Outbox repository initialized: table={}, dialect={}", properties.getTableName(), dialect);
        return new OutboxRepository(jdbcTemplate, properties.getTableName(), dialect);
    }

    /**
     * 创建 Outbox 写入服务
     *
     * @param outboxRepository Outbox 仓储
     * @param properties       事件配置属性
     * @param gatewayProvider  投递网关提供者（用于同步投递模式）
     * @return Outbox 写入服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxRepository outboxRepository,
                                       EventProperties properties,
                                       ObjectProvider<EventPublishGateway> gatewayProvider) {
        EventPublishGateway syncGateway = properties.isEnableSyncPublish() ? gatewayProvider.getIfAvailable() : null;
        return new OutboxService(outboxRepository, properties, syncGateway);
    }

    /**
     * 创建领域事件存储适配器（实现 common-domain 的 EventStore SPI）
     *
     * <p>当容器中不存在其他 EventStore 实现时，自动注册基于 Outbox 的适配器。
     *
     * @param outboxService Outbox 写入服务
     * @return Outbox 事件存储适配器实例
     */
    @Bean
    @ConditionalOnMissingBean(EventStore.class)
    public OutboxEventStore outboxEventStore(OutboxService outboxService) {
        log.info("OutboxEventStore registered as default EventStore implementation");
        return new OutboxEventStore(outboxService);
    }

    /**
     * 创建事件投递网关降级实现
     *
     * <p>当容器中不存在其他 EventPublishGateway 实现且 RocketMQTemplate 不可用时使用 Noop 实现。
     *
     * @param properties 事件配置属性
     * @return Noop 事件投递网关实例
     */
    @Bean
    @ConditionalOnMissingBean(EventPublishGateway.class)
    public EventPublishGateway noopEventPublishGateway(EventProperties properties) {
        log.warn("No EventPublishGateway found, using NoopEventPublishGateway. "
                + "Messages will not be actually published to any message queue.");
        return new NoopEventPublishGateway();
    }

    /**
     * 创建 Outbox 后台处理器
     *
     * @param outboxRepository    Outbox 仓储
     * @param publishGateway      投递网关
     * @param properties          事件配置属性
     * @param meterRegistryProvider Micrometer 指标注册器提供者（可选）
     * @return Outbox 后台处理器实例
     */
    @Bean(initMethod = "start")
    public OutboxProcessor outboxProcessor(OutboxRepository outboxRepository,
                                           EventPublishGateway publishGateway,
                                           EventProperties properties,
                                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.activeGateway = publishGateway;
        this.activeProperties = properties;
        OutboxProcessor processor = new OutboxProcessor(
                outboxRepository,
                publishGateway,
                properties,
                meterRegistryProvider.getIfAvailable()
        );
        this.outboxProcessor = processor;
        return processor;
    }

    /**
     * 创建 Outbox 健康检查指标
     *
     * @param outboxRepository Outbox 仓储
     * @param properties       事件配置属性
     * @return Outbox 健康指标实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public OutboxHealthIndicator outboxHealthIndicator(OutboxRepository outboxRepository,
                                                        EventProperties properties) {
        return new OutboxHealthIndicator(outboxRepository, properties);
    }

    /**
     * 启动后校验投递网关
     *
     * <p>如果使用 NoopEventPublishGateway 且 fail-on-noop=true，抛出异常阻止应用启动，
     * 避免生产环境消息丢失。
     */
    @PostConstruct
    public void validateGateway() {
        if (activeProperties != null && activeProperties.isFailOnNoop()
                && activeGateway instanceof NoopEventPublishGateway) {
            throw new IllegalStateException(
                    "NoopEventPublishGateway is in use and remi.event.outbox.fail-on-noop=true. "
                            + "Please provide an EventPublishGateway implementation (e.g. RocketMQ) "
                            + "or set remi.event.outbox.fail-on-noop=false to suppress this check.");
        }
    }

    /**
     * 销毁时停止 Outbox 处理器
     */
    @PreDestroy
    public void destroy() {
        if (outboxProcessor != null) {
            outboxProcessor.stop();
        }
    }
}
