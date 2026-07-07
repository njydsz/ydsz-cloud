package com.njydsz.pmis.workflow.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 催办限流器
 *
 * <p>P0-2: 同一催办人对同一任务/实例在冷却窗口内只允许一次催办，防止恶意刷催办。
 *
 * <p>实现：Redis 原子 Lua 脚本 SET NX EX，30 分钟冷却。
 * <pre>
 *   if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
 *     return 1
 *   else
 *     return 0
 *   end
 * </pre>
 *
 * <p>设计要点：
 * <ul>
 *   <li>key 维度：催办人 userId + 任务/实例 id，避免不同催办人之间互锁</li>
 *   <li>窗口可配置（默认 30 分钟）</li>
 *   <li>限流失败时通过 {@link com.njydsz.pmis.common.exception.BizException}
 *       + {@code RATE_LIMIT} 错误码抛回前端</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowUrgeLimiter {

    /** Redis SETNX 原子脚本（与 IdempotentAspect 同款，保证并发安全） */
    private static final String LUA = "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 默认冷却窗口 30 分钟（与对标用友/钉钉审批的 30 分钟冷却一致） */
    public static final long DEFAULT_COOLDOWN_SECONDS = 30 * 60L;

    /**
     * 校验催办是否在冷却窗口内
     *
     * @param userId         催办人 ID
     * @param targetId       目标（任务 ID 或实例 ID）
     * @param targetType     目标类型（TASK/INSTANCE）
     * @return true=可催办；false=冷却中
     */
    public boolean tryAcquire(String userId, Long targetId, String targetType) {
        return tryAcquire(userId, targetId, targetType, DEFAULT_COOLDOWN_SECONDS);
    }

    /**
     * 校验催办是否在冷却窗口内（自定义窗口）
     *
     * @param userId         催办人 ID
     * @param targetId       目标 ID
     * @param targetType     目标类型
     * @param cooldownSeconds 冷却秒数
     * @return true=可催办；false=冷却中
     */
    public boolean tryAcquire(String userId, Long targetId, String targetType, long cooldownSeconds) {
        if (userId == null || targetId == null) {
            return true; // 缺参数不阻断主流程
        }
        String key = buildKey(userId, targetId, targetType);
        try {
            Long ok = redisTemplate.execute(
                    SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(cooldownSeconds)
            );
            boolean acquired = ok != null && "1".equals(String.valueOf(ok));
            if (!acquired) {
                log.info("[FlowUrgeLimiter] 催办冷却中 userId={} targetId={} type={} key={}",
                        userId, targetId, targetType, key);
            }
            return acquired;
        } catch (Exception e) {
            // Redis 不可用时降级放行，避免拖垮催办主流程
            log.warn("[FlowUrgeLimiter] Redis 不可用，降级放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 主动释放催办冷却（管理员强制操作后允许立即再次催办）
     *
     * @param userId     催办人
     * @param targetId   目标
     * @param targetType 类型
     */
    public void release(String userId, Long targetId, String targetType) {
        if (userId == null || targetId == null) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(userId, targetId, targetType));
        } catch (Exception e) {
            log.warn("[FlowUrgeLimiter] 释放冷却失败: {}", e.getMessage());
        }
    }

    /**
     * 批量查询指定催办人对多个目标的冷却剩余时间
     *
     * @param userId    催办人
     * @param targetIds 目标 ID 列表
     * @param type      目标类型
     * @return 剩余秒数列表（0=可催办，>0=冷却中）
     */
    public List<Long> getCooldownSeconds(String userId, List<Long> targetIds, String type) {
        if (userId == null || targetIds == null || targetIds.isEmpty()) {
            return Collections.emptyList();
        }
        return targetIds.stream()
                .map(targetId -> {
                    try {
                        Long ttl = redisTemplate.getExpire(buildKey(userId, targetId, type));
                        return ttl == null ? 0L : ttl;
                    } catch (Exception e) {
                        log.warn("[FlowUrgeLimiter] 获取催办剩余 TTL 失败 targetId={}: {}", targetId, e.getMessage());
                        return 0L;
                    }
                })
                .toList();
    }

    // ============================== 私有 ==============================

    private static String buildKey(String userId, Long targetId, String targetType) {
        return "pmis:flow:urge:" + targetType + ":" + targetId + ":by:" + userId;
    }
}
