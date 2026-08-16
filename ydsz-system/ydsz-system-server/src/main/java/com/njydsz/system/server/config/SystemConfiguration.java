package com.njydsz.system.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.server.health.SystemHealthIndicator;

/**
 * 系统模块 Spring 配置
 *
 * <p>承担 ydsz-system 服务端的 Spring Bean 注册职责：
 *
 * <ul>
 *   <li>注册 {@link SystemProperties}（{@code @ConfigurationProperties(prefix = "ydsz.system")}）， 通过
 *       {@code @EnableConfigurationProperties} 激活
 *   <li>注册 {@link BCryptPasswordEncoder} Bean，强度由 {@code ydsz.system.app.bcrypt-strength} 配置（合法范围
 *       4-31，默认 10）
 * </ul>
 *
 * <p><b>BCrypt 强度建议：</b>
 *
 * <ul>
 *   <li>4-9：开发 / 测试环境，验证速度快
 *   <li>10-12：生产环境，安全性与性能平衡（<b>推荐 10</b>）
 *   <li>13-31：金融级安全场景，CPU 开销显著（每登录 < 200ms 可接受）
 * </ul>
 *
 * <p><b>配置变更：</b>{@link SystemProperties} 通过 Nacos 实现热加载；BCrypt 强度变更需重启生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SystemProperties 系统模块配置属性
 * @see com.njydsz.system.server.service.AppInfoService 应用注册服务（BCrypt 加密 appSecret）
 */
@Configuration
@EnableConfigurationProperties(SystemProperties.class)
public class SystemConfiguration {

  /**
   * BCrypt 密码编码器 Bean
   *
   * <p>用于 {@link com.njydsz.system.server.service.AppInfoService} 加密 {@code appSecret} 字段。 BCrypt
   * 是<b>单向</b>哈希函数，不可逆；同一明文每次加密结果不同（盐值随机）。
   *
   * @param properties 系统配置
   * @return {@link BCryptPasswordEncoder} 实例，强度取自 {@code ydsz.system.app.bcrypt-strength}（合法
   *     4-31，越界回退 10）
   */
  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder(SystemProperties properties) {
    int strength = properties.getApp().getBcryptStrength();
    if (strength < 4 || strength > 31) {
      strength = 10;
    }
    return new BCryptPasswordEncoder(strength);
  }

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(SystemHealthIndicator.class)
  public SystemHealthIndicator systemHealthIndicator(
      RedisTemplate<String, Object> redisTemplate,
      ConfigMapper configMapper,
      DictItemMapper dictItemMapper) {
    return new SystemHealthIndicator(redisTemplate, configMapper, dictItemMapper);
  }
}
