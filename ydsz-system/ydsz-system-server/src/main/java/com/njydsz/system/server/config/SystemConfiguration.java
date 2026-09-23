package com.njydsz.system.server.config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.jdbc.health.DataSourceHealthIndicator;
import com.njydsz.common.redis.health.RedisHealthIndicator;
import com.njydsz.system.server.health.SystemHealthIndicator;



/**
 * 系统模块 Spring 配置
 *
 * <p>承担 ydsz-system 服务端的 Spring Bean 注册职责：
 *
 * <ul>
 *   <li>注册 {@link SystemProperties}（{@code @ConfigurationProperties(prefix = "ydsz.system")}）， 通过
 *       {@code @EnableConfigurationProperties} 激活
 *   <li>BCrypt {@code PasswordEncoder} 由 ydsz-common-auth 的 PasswordEncoderAutoConfiguration
 *       统一提供（P1-7 收敛，强度经 {@code ydsz.auth.bcrypt-strength} 配置），本模块不再重复注册
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
 * @since 26.09.01
 * @see SystemProperties 系统模块配置属性
 * @see com.njydsz.system.server.service.AppInfoService 应用注册服务（BCrypt 加密 appSecret）
 */
@Configuration
@EnableConfigurationProperties(SystemProperties.class)
public class SystemConfiguration {

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(SystemHealthIndicator.class)
  public SystemHealthIndicator systemHealthIndicator(
      ObjectProvider<RedisHealthIndicator> redisHealthIndicatorProvider,
      ObjectProvider<DataSourceHealthIndicator> dataSourceHealthIndicatorProvider) {
    return new SystemHealthIndicator(redisHealthIndicatorProvider, dataSourceHealthIndicatorProvider);
  }
}
