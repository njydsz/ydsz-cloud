package com.njydsz.userinfo.server.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户信息中心健康检查指标。
 *
 * <p>检查项：Redis 连通性、JWT 配置、用户表连通性、角色表连通性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.userinfo", name = "health-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserInfoHealthIndicator extends AbstractModuleHealthIndicator {

    private final RedisService redisService;
    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;

    @Override
    protected void doHealthCheck(org.springframework.boot.health.contributor.Health.Builder builder) {
        // Redis 连通性
        checkRedis(builder, () -> {
            redisService.hasKey("ydsz:userinfo:health:probe");
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
        LambdaQueryWrapper<UserAccount> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(UserAccount::getDeleted, 0);
        checkTableProbeWithValue(builder, "userCount", () -> userAccountMapper.selectCount(userWrapper));

        // 角色表探针
        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(Role::getDeleted, 0);
        checkTableProbeWithValue(builder, "roleCount", () -> roleMapper.selectCount(roleWrapper));
    }
}
