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
 * 通知频率控制器（P2-5, GAP-39）
 *
 * <p>对高频通知（任务创建/催办/超时提醒）做限流，避免同一接收人短时间重复收到同类通知。
 *
 * <p>实现：Redis 原子 Lua 脚本 SET NX EX，key 维度 = {@code notify:biz:<bizType>:<receiverId>:<targetId>}。
 * 冷却窗口内重复通知被丢弃（返回 false），窗口过后放行并重置。
 *
 * <p>Redis 不可用时降级放行，避免拖垮通知主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNotifyFrequencyLimiter {

    private static final String LUA =
            "if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 默认冷却窗口：5 分钟 */
    public static final long DEFAULT_COOLDOWN_SECONDS = 5 * 60L;

    /**
     * 尝试获取通知配额
     *
     * @param bizType    业务类型（TASK_CREATED / URGE / TIMEOUT ...）
     * @param receiverId 接收人 ID
     * @param targetId   目标 ID（任务 ID 或实例 ID）
     * @return true=可发送；false=冷却中（应丢弃）
     */
    public boolean tryAcquire(String bizType, String receiverId, String targetId) {
        return tryAcquire(bizType, receiverId, targetId, DEFAULT_COOLDOWN_SECONDS);
    }

    /**
     * 尝试获取通知配额（自定义冷却窗口）
     */
    public boolean tryAcquire(String bizType, String receiverId, String targetId, long cooldownSeconds) {
        if (bizType == null || receiverId == null || targetId == null) {
            return true; // 缺参数不阻断
        }
        String key = "pmis:flow:notify:" + bizType + ":" + receiverId + ":" + targetId;
        try {
            Long ok = redisTemplate.execute(SCRIPT, Collections.singletonList(key),
                    String.valueOf(System.currentTimeMillis()), String.valueOf(cooldownSeconds));
            return ok != null && "1".equals(String.valueOf(ok));
        } catch (Exception e) {
            log.warn("[FlowNotifyFreq] Redis 不可用，降级放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 主动释放冷却（管理员强制通知后允许立即再发）
     */
    public void release(String bizType, String receiverId, String targetId) {
        if (bizType == null || receiverId == null || targetId == null) {
            return;
        }
        try {
            redisTemplate.delete("pmis:flow:notify:" + bizType + ":" + receiverId + ":" + targetId);
        } catch (Exception e) {
            log.warn("[FlowNotifyFreq] 释放冷却失败: {}", e.getMessage());
        }
    }
}
