package com.njydsz.common.auth.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.auth.service.TokenBlacklistService;
import com.njydsz.common.auth.token.JwtTokenService;
import com.njydsz.common.auth.token.TokenProperties;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * JWT Token 相关配置。
 *
 * <p>负责装配 Token 生命周期相关的核心 Bean：
 *
 * <ul>
 *   <li>{@link TokenService}（基于 jjwt 的 {@link JwtTokenService} 实现）
 *   <li>{@link TokenBlacklistService}（Token 黑名单，依赖 Redis + 可选分布式锁）
 * </ul>
 *
 * <p>装配条件：
 *
 * <ul>
 *   <li>{@code ydsz.auth.token.enabled=true}（默认启用）
 *   <li>classpath 中存在 {@code io.jsonwebtoken.Jwts}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Configuration
@EnableConfigurationProperties(TokenProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.auth.token",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnClass(name = "io.jsonwebtoken.Jwts")
public class JwtConfiguration {

  /**
   * 创建 Token 黑名单服务。
   *
   * <p>当 {@link DistributedLocker} 可用时（ydsz-common-lock 在 classpath 上）， 使用其 {@code tryLock}/{@code
   * unlock} 实现刷新锁，享有 Lua 原子释放与 WatchDog 续期能力； 否则降级为原生 {@code setIfAbsent} 操作。
   *
   * @param redisStringOps Redis String 操作
   * @param authProperties 认证配置属性
   * @param lockerProvider 分布式锁提供者（可选）
   * @return Token 黑名单服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisStringOps.class)
  public TokenBlacklistService tokenBlacklistService(
      RedisStringOps redisStringOps,
      AuthProperties authProperties,
      ObjectProvider<DistributedLocker> lockerProvider) {
    return new TokenBlacklistService(
        lockerProvider.getIfAvailable(), redisStringOps, authProperties);
  }

  /**
   * 创建 JWT Token 服务。
   *
   * @param tokenProperties Token 配置属性
   * @param tokenBlacklistServiceProvider Token 黑名单服务（可选）
   * @param snowflakeIdGeneratorProvider 分布式 ID 生成器（用于 jti；缺失时构造器会给出明确报错）
   * @return Token 服务实例
   */
  @Bean
  @ConditionalOnMissingBean(TokenService.class)
  public TokenService jwtTokenService(
      TokenProperties tokenProperties,
      ObjectProvider<TokenBlacklistService> tokenBlacklistServiceProvider,
      ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider) {
    return new JwtTokenService(
        tokenProperties,
        tokenBlacklistServiceProvider.getIfAvailable(),
        snowflakeIdGeneratorProvider.getIfAvailable());
  }
}
