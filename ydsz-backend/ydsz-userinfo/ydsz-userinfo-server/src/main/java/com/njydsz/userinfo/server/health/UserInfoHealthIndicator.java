package com.njydsz.userinfo.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
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

    private final RedisService redisService;
    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Redis 连通性检查（使用 execute 确保连接释放）
        try {
            String ping = redisService.execute(conn -> conn.ping(), true);
            details.put("redis", "UP - " + ping);
        } catch (Exception e) {
            details.put("redis", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // JWT 配置检查
        try {
            if (tokenService != null) {
                details.put("jwt", "UP - TokenService configured");
            } else {
                details.put("jwt", "DOWN - TokenService not injected");
                return Health.down().withDetails(details).build();
            }
        } catch (Exception e) {
            details.put("jwt", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // 数据库连通性 - 用户计数
        try {
            LambdaQueryWrapper<UserAccountDO> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.eq(UserAccountDO::getDeleted, 0);
            long userCount = userAccountMapper.selectCount(userWrapper);
            details.put("userCount", userCount);
        } catch (Exception e) {
            details.put("database", "DOWN - " + e.getMessage());
            return Health.down().withDetails(details).build();
        }

        // 数据库连通性 - 角色计数
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
