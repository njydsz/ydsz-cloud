package com.njydsz.message.server.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.health.MessageHealthIndicator;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.template.TemplateEngine;
import com.njydsz.message.server.template.cache.CachedTemplateEngine;

/**
 * 消息模块自动装配。
 *
 * <p>P0-3: 通过 {@code @EnableConfigurationProperties} 注册 {@link MessageProperties} 和 {@link
 * ChannelProperties}， 不再依赖 {@code @Component} 注解。
 *
 * <p>P1.3.0 重构：RealtimePushService 已改为委托 common-socket 的 RealtimePushTemplate，不再需要在此手动注册。
 *
 * <p>P1-4: message 模块原 {@code WebSocketConfig} 已删除， {@code @EnableWebSocketMessageBroker}
 * 与端点/Broker 配置统一由 common-socket 的 {@code WebSocketConfigurer} 提供（组件扫描自动覆盖），消除重复配置。
 *
 * <p>ChannelRouter 为 {@code @Component}，由组件扫描自动注册，无需在此 @Bean。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@EnableConfigurationProperties({MessageProperties.class, ChannelProperties.class})
public class MessageAutoConfiguration {

  /**
   * P2-4: 注册消息服务可观测性指标 Bean。
   *
   * <p>从 @Component 改为 @Bean 注册，与项目其他模块的 Metrics 注册模式一致。 当 classpath 中不存在 MeterRegistry 时不注册。
   *
   * @return MessageMetrics 实例
   */
  @Bean
  @ConditionalOnMissingBean(MessageMetrics.class)
  @ConditionalOnClass(MeterRegistry.class)
  public MessageMetrics messageMetrics() {
    return new MessageMetrics();
  }

  /**
   * 模板 AST 缓存引擎 Bean 注册。
   *
   * <p>从 {@code @Component} 改为 {@code @Bean} 注册，与项目其他模块的组件注册模式一致。
   * 当 classpath 中不存在 {@link MeterRegistry} 时不注册。
   *
   * <p>监控指标通过 {@link com.njydsz.common.sentry.adapter.SentryMetricsAdapter} 桥接注册，
   * 不再直接注入 {@link MeterRegistry}，符合《云顶编码规范》第 27.2.1 节要求。
   *
   * @param messageProperties 消息模块配置属性
   * @return CachedTemplateEngine 实例
   */
  @Bean
  @ConditionalOnMissingBean(TemplateEngine.class)
  @ConditionalOnClass(MeterRegistry.class)
  public CachedTemplateEngine cachedTemplateEngine(MessageProperties messageProperties) {
    int maxCacheSize = DEFAULT_TEMPLATE_CACHE_MAX_SIZE;
    long expireAfterWriteMinutes = DEFAULT_TEMPLATE_CACHE_EXPIRE_MINUTES;
    return new CachedTemplateEngine(maxCacheSize, expireAfterWriteMinutes);
  }

  /** 默认模板 AST 缓存最大容量 */
  private static final int DEFAULT_TEMPLATE_CACHE_MAX_SIZE = 1000;

  /** 默认模板 AST 缓存写入后过期时间（分钟） */
  private static final long DEFAULT_TEMPLATE_CACHE_EXPIRE_MINUTES = 30L;

  /**
   * P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component）
   *
   * @param redisStringOps Redis 底层 API（用于健康检查 Sentinel/PING 探测）
   * @param msgLogRepository 消息日志 Repository（队列积压轻量探针 LIMIT 1）
   * @param channelRouter 通道路由器（获取已注册表通道列表与通道数量）
   * @return MessageHealthIndicator Bean
   */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(MessageHealthIndicator.class)
  public MessageHealthIndicator messageHealthIndicator(
      RedisStringOps redisStringOps, MsgLogRepository msgLogRepository, ChannelRouter channelRouter) {
    return new MessageHealthIndicator(redisStringOps, msgLogRepository, channelRouter);
  }
}
