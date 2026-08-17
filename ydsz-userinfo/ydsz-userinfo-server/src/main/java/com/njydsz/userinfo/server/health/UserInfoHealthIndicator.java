package com.njydsz.userinfo.server.health;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.userinfo.infra.entity.RoleDO;
import com.njydsz.userinfo.infra.entity.UserAccountDO;
import com.njydsz.userinfo.infra.repository.RoleRepository;
import com.njydsz.userinfo.infra.repository.UserAccountRepository;

/**
 * 用户信息中心健康检查指标。
 *
 * <p>检查项：Redis 连通性、JWT 配置、用户表连通性、角色表连通性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(
    prefix = "ydsz.userinfo",
    name = "health-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class UserInfoHealthIndicator extends AbstractModuleHealthIndicator {

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
    LambdaQueryWrapper<UserAccountDO> userWrapper = new LambdaQueryWrapper<>();
    userWrapper.eq(UserAccountDO::getDeleted, 0);
    checkTableProbeWithValue(
        builder, "userCount", () -> userAccountRepository.count(userWrapper));

    // 角色表探针
    LambdaQueryWrapper<RoleDO> roleWrapper = new LambdaQueryWrapper<>();
    roleWrapper.eq(RoleDO::getDeleted, 0);
    checkTableProbeWithValue(builder, "roleCount", () -> roleRepository.count(roleWrapper));
  }
}
