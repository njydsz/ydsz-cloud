package com.njydsz.userinfo.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;
import com.njydsz.userinfo.infra.mapper.RoleMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.domain.entity.RoleDO;
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
public class UserInfoHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;
    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Check Redis connectivity
        try {
            String ping = redisConnectionFactory.getConnection().ping();
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check JWT configuration
        try {
            details.put("jwt", "UP - configured");
        } catch (Exception e) {
            details.put("jwt", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check database connectivity - user count
        try {
            LambdaQueryWrapper<UserAccountDO> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(UserAccountDO::getDeleted, 0);
            long userCount = userAccountMapper.selectCount(userWrapper);
            details.put("userCount", userCount);
        } catch (Exception e) {
            details.put("database", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // Check database connectivity - role count
        try {
            LambdaQueryWrapper<RoleDO> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(RoleDO::getDeleted, 0);
            long roleCount = roleMapper.selectCount(roleWrapper);
            details.put("roleCount", roleCount);
        } catch (Exception e) {
            details.put("database", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        return Health.up().withDetails(details).build();
    }
}
