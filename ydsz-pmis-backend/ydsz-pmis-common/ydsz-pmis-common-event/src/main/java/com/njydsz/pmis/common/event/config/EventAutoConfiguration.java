package com.njydsz.pmis.common.event.config;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.gateway.NoopEventPublishGateway;
import com.njydsz.pmis.common.event.health.OutboxHealthIndicator;
import com.njydsz.pmis.common.event.model.DatabaseDialect;
import com.njydsz.pmis.common.event.processor.OutboxProcessor;
import com.njydsz.pmis.common.event.repository.OutboxRepository;
import com.njydsz.pmis.common.event.service.OutboxService;

import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;

/**
 * Outbox 事件模块自动配置
 *
 * <p>当 {@code pmis.event.outbox.enabled=true}（默认）且容器中存在
 * {@link JdbcTemplate} 时自动装配。
 *
 * <p>自动检测数据库方言（PostgreSQL/MySQL/Oracle），适配 LIMIT 语法和 JSON 列处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(prefix = "pmis.event.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
@EnableScheduling
public class EventAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventAutoConfiguration.class);

    private OutboxProcessor outboxProcessor;

    /**
     * Outbox 仓储
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
     * Outbox 写入服务
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
     * 事件投递网关（降级实现）
     *
     * <p>当容器中不存在其他 EventPublishGateway 实现时使用 Noop 实现。
     */
    @Bean
    @ConditionalOnMissingBean(EventPublishGateway.class)
    public EventPublishGateway eventPublishGateway() {
        log.warn("No EventPublishGateway found, using NoopEventPublishGateway. "
                + "Messages will not be actually published to any message queue.");
        return new NoopEventPublishGateway();
    }

    /**
     * Outbox 后台处理器
     */
    @Bean(initMethod = "start")
    public OutboxProcessor outboxProcessor(OutboxRepository outboxRepository,
                                           EventPublishGateway publishGateway,
                                           EventProperties properties,
                                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
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
     * Outbox 健康指标
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public OutboxHealthIndicator outboxHealthIndicator(OutboxRepository outboxRepository,
                                                        EventProperties properties) {
        return new OutboxHealthIndicator(outboxRepository, properties);
    }

    @PreDestroy
    public void destroy() {
        if (outboxProcessor != null) {
            outboxProcessor.stop();
        }
    }
}
