package com.njydsz.userinfo.app.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.userinfo.app.config.ConditionalOnPlatform;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;

/**
 * App 端健康检查指标（P1-2 双入口架构）。
 *
 * <p>仅在 {@code ydsz.userinfo.platform=app} 时激活，检查项：Redis 连通性、JWT 配置、用户表连通性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnPlatform("app")
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.userinfo",
    name = "health-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class AppHealthIndicator extends AbstractModuleHealthIndicator {

  private final RedisStringOps redisStringOps;
  private final TokenService tokenService;
  private final UserAccountRepository userAccountRepository;
  private final RoleRepository roleRepository;

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    // Redis 连通性
    checkRedis(
        builder,
        () -> {
          redisStringOps.hasKey("ydsz:userinfo:health:probe");
          return "PONG";
        });

    // JWT 配置
    if (tokenService != null) {
      builder.withDetail("jwt", "UP - TokenService configured");
    } else {
      builder.withDetail("jwt", "DOWN - TokenService not injected");
      builder.down();
    }

    // 用户表探针
    checkTableProbeWithValue(
        builder, "userCount", () -> userAccountRepository.count(new UserAccountPageQuery()));

    // 角色表探针
    checkTableProbeWithValue(
        builder, "roleCount", () -> roleRepository.countByQuery(new RolePageQuery()));
  }
}
