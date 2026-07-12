package com.njydsz.pmis.common.security.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Token 黑名单组件 —— 支持主动注销和封禁用户。
 * <p>
 * 对标 remi-comm TokenBlacklist，基于 Redis 实现高效的 Token 失效。
 * 支持：
 * <ul>
 *   <li>单 Token 注销（用户主动退出）</li>
 *   <li>用户级 Token 封禁（管理员强制下线）</li>
 *   <li>自动过期（与 JWT 过期时间同步）</li>
 * </ul>
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class TokenBlacklist {

    private static final String TOKEN_BLACKLIST_PREFIX = "pmis:token:blacklist:";
    private static final String USER_BLACKLIST_PREFIX = "pmis:token:user-bl:";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 将单个 Token 加入黑名单（用于注销）。
     *
     * @param tokenId    Token 唯一标识（JTI）
     * @param ttl        剩余过期时间
     */
    public void revokeToken(String tokenId, Duration ttl) {
        redis.opsForValue().set(TOKEN_BLACKLIST_PREFIX + tokenId, "1", ttl);
    }

    /**
     * 检查 Token 是否在黑名单中。
     *
     * @param tokenId Token 唯一标识
     * @return true 如果已注销
     */
    public boolean isRevoked(String tokenId) {
        return Boolean.TRUE.equals(redis.hasKey(TOKEN_BLACKLIST_PREFIX + tokenId));
    }

    /**
     * 封禁用户所有 Token（管理员强制下线）。
     *
     * @param userId 用户 ID
     * @param ttl    封禁持续时间
     */
    public void blockUser(String userId, Duration ttl) {
        redis.opsForValue().set(USER_BLACKLIST_PREFIX + userId, "1", ttl);
    }

    /**
     * 检查用户是否被封禁。
     *
     * @param userId 用户 ID
     * @return true 如果用户被封禁
     */
    public boolean isUserBlocked(String userId) {
        return Boolean.TRUE.equals(redis.hasKey(USER_BLACKLIST_PREFIX + userId));
    }

    /**
     * 解封用户。
     *
     * @param userId 用户 ID
     */
    public void unblockUser(String userId) {
        redis.delete(USER_BLACKLIST_PREFIX + userId);
    }
}
