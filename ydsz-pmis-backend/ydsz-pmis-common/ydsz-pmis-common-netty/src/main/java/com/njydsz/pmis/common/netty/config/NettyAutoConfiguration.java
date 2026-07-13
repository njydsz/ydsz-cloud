package com.njydsz.pmis.common.netty.config;

import com.njydsz.pmis.common.netty.metric.NettyChannelMetrics;
import com.njydsz.pmis.common.netty.server.NettyServerLifecycle;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

/**
 * Netty 自动装配配置。
 *
 * <p>当 classpath 存在 Netty {@code io.netty.channel.EventLoopGroup} 且
 * {@code pmis.netty.enabled=true} 时自动生效。
 *
 * <p>自动注册以下 Bean：
 * <ul>
 *   <li>{@link NettyChannelMetrics} — Netty 指标收集器</li>
 *   <li>{@link NettyServerLifecycle} — Server 生命周期管理（当容器中存在 AbstractNettyServer Bean 时）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NettyProperties.class)
@ConditionalOnClass(name = "io.netty.channel.EventLoopGroup")
@ConditionalOnProperty(prefix = "pmis.netty", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NettyAutoConfiguration {

    /**
     * Netty Channel 指标收集器。
     *
     * @param meterRegistry MeterRegistry（可为 null）
     * @return Netty 指标收集器
     */
    @Bean
    @ConditionalOnMissingBean(NettyChannelMetrics.class)
    public NettyChannelMetrics nettyChannelMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        log.info("[Netty] 注册 NettyChannelMetrics");
        return new NettyChannelMetrics(meterRegistry);
    }

    /**
     * Netty Server 生命周期管理器。
     *
     * <p>当 Spring 容器中存在 {@code AbstractNettyServer} Bean 时自动注册，
     * 随容器启动/停止自动管理 Netty Server 生命周期。
     *
     * @param servers 容器中所有 AbstractNettyServer Bean（可为空列表）
     * @return Netty Server 生命周期管理器
     */
    @Bean
    @ConditionalOnMissingBean(NettyServerLifecycle.class)
    public NettyServerLifecycle nettyServerLifecycle(
            @Autowired(required = false) java.util.List<com.njydsz.pmis.common.netty.server.AbstractNettyServer> servers) {
        java.util.List<com.njydsz.pmis.common.netty.server.AbstractNettyServer> serverList =
                servers != null ? servers : Collections.emptyList();
        log.info("[Netty] 注册 NettyServerLifecycle, servers={}", serverList.size());
        return new NettyServerLifecycle(serverList);
    }
}
