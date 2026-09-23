package com.njydsz.userinfo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.server.health.UserInfoHealthIndicator;

/**
 * 用户信息中心模块配置
 *
 * <p>统一管理 ydsz-userinfo 微服务的横切关注点：异步任务、缓存、配置属性注册。 该 Configuration 由 ydsz-userinfo-server 的
 * Spring Boot 启动类通过 @Import 或 组件扫描自动加载。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>密码编码器（{@code PasswordEncoder}，BCrypt）由 ydsz-common-auth 的
 *       PasswordEncoderAutoConfiguration 统一提供（P1-7 收敛，强度经 {@code ydsz.auth.bcrypt-strength} 配置），
 *       本模块不再重复注册
 *   <li>启用 {@code @EnableAsync}：支撑登录历史异步落库（P1-2，{@code LoginHistoryServiceImpl#recordLoginAttempt}）
 *   <li>启用 {@code @EnableCaching}：为部门树、角色权限等热点数据预留声明式缓存能力（P2-2，
 *       当前热点缓存由编程式缓存实现，遵循 18.5 缓存规范：统一走 {@code ydsz-common-cache} 且 TTL 外部化）
 *   <li>注册 {@link UserInfoProperties}、{@link LdapProperties}、{@link LdapSyncProperties} 等
 *       配置属性（{@code ApiSignatureProperties} 由 common-safe 的自动配置接管）
 * </ul>
 *
 * <p><b>Bean 清单：</b>密码编码器等公共 Bean 见 ydsz-common-auth 自动配置（P1-7 收敛后本类仅保留健康检查 Bean）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see UserInfoProperties 用户中心配置属性
 * @see LdapProperties LDAP 配置属性
 * @see com.njydsz.userinfo.UserInfoApplication ydsz-userinfo 启动类
 */
@Configuration
@EnableAsync
@EnableCaching
@EnableConfigurationProperties({
  UserInfoProperties.class,
  LdapProperties.class,
  LdapSyncProperties.class,
  InternalCallProperties.class,
  CrossDomainSsoProperties.class,
  GeoIpProperties.class,
  UserTokenProperties.class,
  UserSecurityProperties.class,
  UserLoginRiskProperties.class,
  JdbcProvisionProperties.class
})
public class UserInfoConfiguration {

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(UserInfoHealthIndicator.class)
  public UserInfoHealthIndicator userInfoHealthIndicator(
      RedisStringOps redisStringOps,
      TokenService tokenService,
      UserAccountRepository userAccountRepository,
      RoleRepository roleRepository) {
    return new UserInfoHealthIndicator(redisStringOps, tokenService, userAccountRepository, roleRepository);
  }
}
