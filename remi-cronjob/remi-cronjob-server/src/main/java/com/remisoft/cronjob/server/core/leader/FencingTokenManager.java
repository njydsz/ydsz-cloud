package com.remisoft.cronjob.server.core.leader;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import com.remisoft.common.redis.service.RedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fencing Token 管理器（P0-3 防脑裂增强）。
 *
 * <p>每次 Leader 切换时递增 Fencing Token，旧 Leader 残留的写操作会因 Token 过期被拒绝。
 * 通过 Redis INCR 原子递增保证 Token 的全局单调递增。
 *
 * <h3>脑裂场景</h3>
 * <ol>
 *   <li>Leader A 因网络分区与 Redis 断连，但本地仍认为自己是 Leader</li>
 *   <li>Leader B 抢占成功，获得新的 Fencing Token (N+1)</li>
 *   <li>Leader A 恢复连接后尝试写操作，附带旧 Token (N)</li>
 *   <li>写操作前检查 Token：N &lt; N+1，拒绝 Leader A 的写操作</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // Leader 切换时获取新 Token
 * long token = fencingTokenManager.acquireNewToken("remi-job-scheduler");
 *
 * // 写操作前校验 Token
 * if (!fencingTokenManager.validateToken("remi-job-scheduler", currentToken)) {
 *     log.warn("Fencing Token 过期，当前节点可能已不是 Leader");
 *     return;
 * }
 * }</pre>
 *
 * <p>对标 SchedulerX 的 Fencing 机制和 PowerJob 的 Leader 选举安全设计。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class FencingTokenManager {

    private final RedisService redisService;
    private final ObjectProvider<LeaderElector> leaderElectorProvider;

    /** Redis key 前缀：存储当前 Leader 的 Fencing Token */
    private static final String FENCING_TOKEN_PREFIX = "remi:job:fencing:token:";

    /** 当前节点持有的 Fencing Token（-1 表示未持有） */
    private final AtomicLong currentToken = new AtomicLong(-1);

    /**
     * 获取新的 Fencing Token（Leader 抢占成功后调用）。
     *
     * <p>通过 Redis INCR 原子递增，保证 Token 全局单调递增。
     * 获取成功后更新本地缓存。
     *
     * @param role Leader 角色
     * @return 新的 Fencing Token（正整数）
     */
    public long acquireNewToken(String role) {
        String key = FENCING_TOKEN_PREFIX + role;
        try {
            Long token = redisService.incr(key, 1);
            if (token == null || token <= 0) {
                log.error("[FencingToken] INCR 返回非法值: role={} token={}", role, token);
                return -1;
            }
            currentToken.set(token);
            log.info("[FencingToken] 获取新 Token: role={} token={}", role, token);
            return token;
        } catch (Exception e) {
            log.error("[FencingToken] 获取 Token 失败: role={} reason={}", role, e.getMessage());
            return -1;
        }
    }

    /**
     * 校验当前节点持有的 Fencing Token 是否仍然有效。
     *
     * <p>对比本地 Token 与 Redis 中的最新 Token，若本地 Token 小于 Redis Token，
     * 说明已发生 Leader 切换，当前节点的写操作应被拒绝。
     *
     * @param role       Leader 角色
     * @param localToken 本地持有的 Fencing Token
     * @return true Token 有效（当前节点仍是 Leader）；false Token 过期
     */
    public boolean validateToken(String role, long localToken) {
        if (localToken <= 0) {
            return false;
        }
        String key = FENCING_TOKEN_PREFIX + role;
        try {
            String redisValue = redisService.get(key, String.class);
            if (redisValue == null) {
                // Redis 中无 Token（被清理或过期），保守拒绝
                log.warn("[FencingToken] Redis 中无 Token, 拒绝操作: role={} localToken={}", role, localToken);
                return false;
            }
            long redisToken = Long.parseLong(redisValue);
            if (localToken < redisToken) {
                log.warn("[FencingToken] Token 过期, 拒绝操作: role={} localToken={} redisToken={} (脑裂防护)",
                        role, localToken, redisToken);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            log.error("[FencingToken] Redis Token 格式异常: role={} value={}", role,
                    redisService.get(key, String.class));
            return false;
        } catch (Exception e) {
            // Redis 异常时放行（避免 Redis 故障导致整个系统不可用）
            log.warn("[FencingToken] 校验异常, 放行: role={} localToken={} reason={}",
                    role, localToken, e.getMessage());
            return true;
        }
    }

    /**
     * 校验当前节点持有的 Token 是否有效（便捷方法）。
     *
     * @param role Leader 角色
     * @return true 有效
     */
    public boolean isCurrentTokenValid(String role) {
        return validateToken(role, currentToken.get());
    }

    /**
     * 获取当前节点持有的 Fencing Token。
     *
     * @return Fencing Token（-1 表示未持有）
     */
    public long getCurrentToken() {
        return currentToken.get();
    }

    /**
     * 清除本地 Token（Leader 释放时调用）。
     */
    public void clearToken() {
        currentToken.set(-1);
    }
}
