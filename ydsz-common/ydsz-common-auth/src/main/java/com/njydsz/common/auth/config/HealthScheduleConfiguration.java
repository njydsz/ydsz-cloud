package com.njydsz.common.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.auth.event.PermissionChangeCacheInvalidator;
import com.njydsz.common.auth.listener.PermissionKeyspaceNotificationListener;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 定时任务与缓存失效配置。
 *
 * <p>负责装配：
 *
 * <ul>
 *   <li>Redis 健康检查定时任务（每分钟执行）
 *   <li>跨实例权限缓存失效总线（Pub/Sub + Keyspace Notification）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Configuration
@EnableScheduling
public class HealthScheduleConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(HealthScheduleConfiguration.class);

  /** 本地缓存健康检查间隔（秒） */
  private static final long HEALTH_CHECK_INTERVAL_SECONDS = 60;

  private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
  private final ObjectProvider<RbacPermissionEvaluator> evaluatorProvider;

  public HealthScheduleConfiguration(
      ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
      ObjectProvider<RbacPermissionEvaluator> evaluatorProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.evaluatorProvider = evaluatorProvider;
  }

  /**
   * 定时健康检查 Redis 连通性。
   *
   * <p>每分钟检查一次 Redis 连通状态，Redis 不可用时自动降级， 并通知 RbacPermissionEvaluator 切换降级策略（ALLOW/DENY）。
   */
  @Scheduled(fixedRateString = "${ydsz.auth.health-check-interval:60000}")
  public void checkRedisHealth() {
    boolean redisOk = true;
    RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
    RbacPermissionEvaluator evaluator = evaluatorProvider.getIfAvailable();
    if (redisTemplate == null) {
      LOG.debug("Redis 服务未配置，使用本地缓存兜底");
      redisOk = false;
    } else {
      try {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
          LOG.debug("Redis 连接工厂未初始化，降级到本地缓存");
          redisOk = false;
        } else {
          var connection = connectionFactory.getConnection();
          try {
            connection.ping();
            if (evaluator != null && !evaluator.isRedisAvailable()) {
              LOG.info("Redis 健康检查恢复，切换回 Redis 缓存");
            }
          } finally {
            connection.close();
          }
        }
      } catch (Exception e) {
        LOG.error("Redis 健康检查异常，降级到本地缓存: {}", e.getMessage());
        redisOk = false;
      }
    }
    // 通知权限评估器切换降级策略
    if (evaluator != null) {
      evaluator.setRedisAvailable(redisOk);
    }
  }

  /**
   * 创建 Redis Keyspace Notification 权限缓存失效监听器。
   *
   * <p>当权限数据在 Redis 中被修改/删除时，通过 Keyspace Notification 精确触发缓存失效， 替代原有的 Pub/Sub 广播模式，解决不保证送达的问题。
   *
   * @param evaluator 权限评估器
   * @param keyspaceProperties Keyspace Notification 配置
   * @return 监听器实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.auth.keyspace-notification",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public PermissionKeyspaceNotificationListener permissionKeyspaceNotificationListener(
      RbacPermissionEvaluator evaluator, KeyspaceNotificationProperties keyspaceProperties) {
    return new PermissionKeyspaceNotificationListener(evaluator);
  }

  /**
   * 创建权限缓存失效监听器 Bean（监听 Redis Pub/Sub 和 Spring 事件）。
   *
   * <p>仅当 {@code ydsz.auth.cross-instance-enabled=true} 时启用。
   *
   * @param rolePermissionLoader 角色权限加载器
   * @param dataPermissionResolver 数据权限解析器
   * @param columnPermissionResolver 列权限解析器
   * @param redisMessageListenerContainer Redis 消息监听容器
   * @return PermissionChangeCacheInvalidator 实例
   */
  @Bean
  @ConditionalOnMissingBean(PermissionChangeCacheInvalidator.class)
  @ConditionalOnBean({RedisStringOps.class, RedisMessageListenerContainer.class})
  @ConditionalOnProperty(
      prefix = "ydsz.auth",
      name = "is-cross-instance-enabled",
      havingValue = "true",
      matchIfMissing = false)
  public PermissionChangeCacheInvalidator permissionChangeCacheInvalidator(
      RolePermissionLoader rolePermissionLoader,
      RedisMessageListenerContainer redisMessageListenerContainer) {
    return new PermissionChangeCacheInvalidator(rolePermissionLoader, redisMessageListenerContainer);
  }
}
