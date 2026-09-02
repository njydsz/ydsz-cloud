package com.njydsz.common.redis.service.multilevel;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.service.CacheProvider;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 多级缓存自动配置
 *
 * <p>当满足以下条件时自动装配 {@link MultiLevelCacheProvider}：
 *
 * <ul>
 *   <li>{@code ydsz.redis.multilevel.enabled=true}（默认 false）
 *   <li>容器中不存在其他 {@link CacheProvider} 实现
 * </ul>
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   redis:
 *     multilevel:
 *       enabled: true
 *       l1-max-size: 2000      # L1 最大条目数
 *       l1-ttl-seconds: 60     # L1 过期秒数（建议为 L2 TTL 的 1/5 ~ 1/10）
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(name = {"com.njydsz.common.redis.config.RedisConfiguration"})
@ConditionalOnProperty(prefix = "ydsz.redis.multilevel", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisProperties.class)
public class MultiLevelCacheAutoConfiguration {

  /**
   * 创建多级缓存提供者
   *
   * <p>仅在容器中不存在 {@link CacheProvider} Bean 时创建，避免与默认 Redis 缓存冲突。 L1 容量与 TTL 从 {@link
   * RedisProperties.MultiLevel} 配置读取。
   *
   * @param redisStringOps L2 Redis 操作组件
   * @param redisProperties Redis 配置属性
   * @return MultiLevelCacheProvider 实例
   */
  @Bean
  @ConditionalOnMissingBean(CacheProvider.class)
  public MultiLevelCacheProvider multiLevelCacheProvider(
      RedisStringOps redisStringOps, RedisProperties redisProperties) {
    Objects.requireNonNull(redisStringOps, "RedisStringOps 必须不为 null");
    RedisProperties.MultiLevel multilevel = redisProperties.getMultilevel();
    Objects.requireNonNull(multilevel, "多级缓存配置必须不为 null");

    long l1MaxSize = multilevel.getL1MaxSize();
    long l1TtlSeconds = multilevel.getL1TtlSeconds();

    log.info("[ydsz-redis] 多级缓存已启用 - l1MaxSize={}, l1TtlSeconds={}", l1MaxSize, l1TtlSeconds);
    return new MultiLevelCacheProvider(redisStringOps, redisProperties, l1MaxSize, l1TtlSeconds);
  }
}
