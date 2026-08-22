package com.njydsz.userinfo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.userinfo.server.auth.CasService;

/**
 * CAS 协议自动配置。
 *
 * <p>注册 CAS 相关的 Spring Bean，条件激活：当 {@code ydsz.userinfo.cas.enabled=true} 时生效。
 *
 * <p><b>依赖关系：</b>
 *
 * <ul>
 *   <li>{@link CasProperties} — CAS 配置属性</li>
 *   <li>{@link CasService} — CAS 核心服务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(CasProperties.class)
@ConditionalOnProperty(prefix = "ydsz.userinfo.cas", name = "enabled", havingValue = "true", matchIfMissing = false)
public class CasConfiguration {

  /**
   * 注册 CAS 核心服务 Bean。
   *
   * @param redisStringOps Redis 操作接口
   * @param casProperties CAS 配置属性
   * @return CAS 服务实例
   */
  @Bean
  public CasService casService(
      com.njydsz.common.redis.service.ops.RedisStringOps redisStringOps,
      CasProperties casProperties) {
    return new CasService(redisStringOps, casProperties);
  }
}
