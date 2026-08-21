package com.njydsz.common.netty.config;

import java.util.Collections;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.netty.client.AbstractNettyClient;
import com.njydsz.common.netty.endpoint.NettyActuatorEndpoint;
import com.njydsz.common.netty.event.ChannelEventDispatcher;
import com.njydsz.common.netty.event.ChannelEventListener;
import com.njydsz.common.netty.health.NettyHealthIndicator;
import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.server.AbstractNettyServer;
import com.njydsz.common.netty.server.NettyServerLifecycle;

/**
 * Netty 自动装配配置。
 *
 * <p>当 classpath 存在 Netty {@code io.netty.channel.EventLoopGroup} 且 {@code ydsz.netty.enabled=true}
 * 时自动生效。
 *
 * <p>自动注册以下 Bean：
 *
 * <ul>
 *   <li>{@link NettyEventLoopPool} — EventLoop 线程池管理器
 *   <li>{@link NettyChannelMetrics} — Netty 指标收集器
 *   <li>{@link NettyServerLifecycle} — Server 生命周期管理（当容器中存在 AbstractNettyServer Bean 时）
 *   <li>{@link NettyHealthIndicator} — 健康检查（当 Actuator 在 classpath 时）
 * </ul>
 *
 * <p>同时通过 {@link BeanPostProcessor} 自动将 {@link NettyChannelMetrics} 和 {@link NettyEventLoopPool}
 * 注入到所有 {@link AbstractNettyServer} 和 {@link AbstractNettyClient} Bean 中，业务方无需手动处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NettyProperties.class)
@ConditionalOnClass(name = "io.netty.channel.EventLoopGroup")
@ConditionalOnProperty(
    prefix = "ydsz.netty",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NettyAutoConfiguration {

  /**
   * Netty EventLoop 线程池管理器。
   *
   * @param properties Netty 配置
   * @return EventLoop 池实例
   */
  @Bean
  @ConditionalOnMissingBean(NettyEventLoopPool.class)
  public NettyEventLoopPool nettyEventLoopPool(NettyProperties properties) {
    NettyEventLoopPool pool =
        new NettyEventLoopPool(
            properties.getShutdownQuietPeriodSeconds(),
            properties.getShutdownTimeoutSeconds(),
            properties.getNativeTransport());
    log.info(
        "[Netty] 注册 NettyEventLoopPool, shared={}, transport={}, quietPeriod={}s, timeout={}s",
        properties.isSharedEventLoop(),
        pool.getTransportType(),
        properties.getShutdownQuietPeriodSeconds(),
        properties.getShutdownTimeoutSeconds());
    return pool;
  }

  /**
   * Netty Channel 指标收集器。
   *
   * @param meterRegistry MeterRegistry（可为 null）
   * @return Netty 指标收集器
   */
  @Bean
  @ConditionalOnMissingBean(NettyChannelMetrics.class)
  public NettyChannelMetrics nettyChannelMetrics(
      @Autowired(required = false) MeterRegistry meterRegistry) {
    log.info("[Netty] 注册 NettyChannelMetrics");
    return new NettyChannelMetrics(meterRegistry);
  }

  /**
   * Netty Server 生命周期管理器。
   *
   * <p>当 Spring 容器中存在 {@code AbstractNettyServer} Bean 时自动注册， 随容器启动/停止自动管理 Netty Server 生命周期。 支持
   * fail-fast 模式：任一 Server 启动失败时终止应用。
   *
   * @param servers 容器中所有 AbstractNettyServer Bean（可为空列表）
   * @param properties Netty 配置
   * @return Netty Server 生命周期管理器
   */
  @Bean
  @ConditionalOnMissingBean(NettyServerLifecycle.class)
  public NettyServerLifecycle nettyServerLifecycle(
      @Autowired(required = false) List<AbstractNettyServer> servers, NettyProperties properties) {
    List<AbstractNettyServer> serverList = servers != null ? servers : Collections.emptyList();
    log.info(
        "[Netty] 注册 NettyServerLifecycle, servers={}, failFast={}",
        serverList.size(),
        properties.isFailFast());
    return new NettyServerLifecycle(serverList, properties.isFailFast());
  }

  /**
   * Netty 健康检查指标（当 Actuator/Health 在 classpath 时自动注册）。
   *
   * @param servers Netty Server 列表
   * @param eventLoopPool EventLoop 池
   * @param metrics 指标收集器
   * @return Netty 健康检查指标
   */
  @Bean
  @ConditionalOnMissingBean(NettyHealthIndicator.class)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public NettyHealthIndicator nettyHealthIndicator(
      @Autowired(required = false) List<AbstractNettyServer> servers,
      NettyEventLoopPool eventLoopPool,
      NettyChannelMetrics metrics) {
    List<AbstractNettyServer> serverList = servers != null ? servers : Collections.emptyList();
    log.info("[Netty] 注册 NettyHealthIndicator, servers={}", serverList.size());
    return new NettyHealthIndicator(serverList, eventLoopPool, metrics);
  }

  /**
   * Channel 事件分发器（当容器中存在 ChannelEventListener Bean 时自动注册）。
   *
   * @param listeners Channel 事件监听器列表
   * @return Channel 事件分发器
   */
  @Bean
  @ConditionalOnMissingBean(ChannelEventDispatcher.class)
  public ChannelEventDispatcher channelEventDispatcher(
      @Autowired(required = false) List<ChannelEventListener> listeners) {
    List<ChannelEventListener> list = listeners != null ? listeners : Collections.emptyList();
    log.info("[Netty] 注册 ChannelEventDispatcher, listeners={}", list.size());
    return new ChannelEventDispatcher(list);
  }

  /**
   * Netty Actuator 端点（当 Actuator 在 classpath 时自动注册）。
   *
   * <p>暴露 {@code /actuator/netty} 端点，提供 Server 状态、EventLoop 池、指标摘要查询。
   *
   * @param servers Netty Server 列表
   * @param eventLoopPool EventLoop 池
   * @param metrics 指标收集器
   * @return Netty 端点
   */
  @Bean
  @ConditionalOnMissingBean(NettyActuatorEndpoint.class)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public NettyActuatorEndpoint nettyActuatorEndpoint(
      @Autowired(required = false) List<AbstractNettyServer> servers,
      NettyEventLoopPool eventLoopPool,
      NettyChannelMetrics metrics) {
    List<AbstractNettyServer> serverList = servers != null ? servers : Collections.emptyList();
    log.info("[Netty] 注册 NettyActuatorEndpoint, servers={}", serverList.size());
    return new NettyActuatorEndpoint(serverList, eventLoopPool, metrics);
  }

  /**
   * BeanPostProcessor — 自动将 NettyChannelMetrics 和 NettyEventLoopPool 注入到所有 AbstractNettyServer 和
   * AbstractNettyClient Bean。
   *
   * @param metrics 指标收集器
   * @param eventLoopPool EventLoop 池
   * @return BeanPostProcessor
   */
  @Bean
  public BeanPostProcessor nettyDependencyInjector(
      NettyChannelMetrics metrics,
      NettyEventLoopPool eventLoopPool,
      @Autowired(required = false) ChannelEventDispatcher channelEventDispatcher) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName)
          throws BeansException {
        if (bean instanceof AbstractNettyServer server) {
          server.setMetrics(metrics);
          server.setEventLoopPool(eventLoopPool);
          log.debug("[Netty] 注入 metrics + eventLoopPool 到 Server: {}", beanName);
        } else if (bean instanceof AbstractNettyClient client) {
          client.setMetrics(metrics);
          client.setEventLoopPool(eventLoopPool);
          log.debug("[Netty] 注入 metrics + eventLoopPool 到 Client: {}", beanName);
        }
        return bean;
      }
    };
  }
}
