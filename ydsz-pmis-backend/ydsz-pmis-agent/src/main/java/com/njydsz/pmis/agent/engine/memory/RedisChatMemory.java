package com.njydsz.pmis.agent.engine.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Duration;

/**
 * Redis 持久化对话记忆（P4-3 落地）。
 *
 * <p>对标 Coze / Dify 的跨实例对话记忆持久化能力：
 * <ul>
 *   <li>对话历史存储在 Redis 中，服务重启不丢失</li>
 *   <li>支持多实例部署（不同 Pod 共享同一 Redis）</li>
 *   <li>按 sessionId 隔离，支持 TTL 自动过期</li>
 *   <li>上下文窗口自动截断（与内存版一致）</li>
 * </ul>
 *
 * <p>Redis Key 格式：{@code pmis:agent:chat:{sessionId}}
 * <p>Value：JSON 数组，每个元素为一条 {@link ChatMessage}
 * <p>TTL：默认 24 小时（可配置）
 *
 * <p>启用方式：配置 {@code pmis.agent.memory.type=redis}
 * <p>降级：Redis 不可用时自动降级为内存版 {@link ChatMemory}
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-3)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "pmis.agent.memory", name = "type", havingValue = "redis")
public class RedisChatMemory {

    /** Redis Key 前缀 */
    private static final String KEY_PREFIX = "pmis:agent:chat:";

    /** 默认 TTL（小时） */
    private static final long DEFAULT_TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final int maxTokensPerSession;
    private final int minRounds;
    private final long ttlHours;

    /**
     * 构造 Redis 对话记忆。
     *
     * @param redisTemplate        Redis 模板
     * @param maxTokensPerSession  单会话最大 token 数
     * @param minRounds            截断时保留的最小轮数
     * @param ttlHours             会话 TTL（小时）
     */
    public RedisChatMemory(
            StringRedisTemplate redisTemplate,
            @Value("${pmis.agent.memory.max-tokens-per-session:" + ChatMemory.DEFAULT_MAX_TOKENS_PER_SESSION + "}") int maxTokensPerSession,
            @Value("${pmis.agent.memory.min-rounds:" + ChatMemory.DEFAULT_MIN_ROUNDS + "}") int minRounds,
            @Value("${pmis.agent.memory.ttl-hours:" + DEFAULT_TTL_HOURS + "}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.maxTokensPerSession = maxTokensPerSession > 0 ? maxTokensPerSession : ChatMemory.DEFAULT_MAX_TOKENS_PER_SESSION;
        this.minRounds = minRounds >= 0 ? minRounds : ChatMemory.DEFAULT_MIN_ROUNDS;
        this.ttlHours = ttlHours > 0 ? ttlHours : DEFAULT_TTL_HOURS;
        log.info("[RedisChatMemory] 初始化完成, maxTokens={}, minRounds={}, ttlHours={}",
                this.maxTokensPerSession, this.minRounds, this.ttlHours);
    }

    /**
     * 添加消息到指定会话。
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    public void addMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank() || message == null) {
            return;
        }
        try {
            if (message.getTokenCount() <= 0) {
                message.setTokenCount(TokenCounter.estimate(message.getContent()));
            }
            String key = KEY_PREFIX + sessionId;
            List<ChatMessage> history = getHistoryInternal(sessionId);
            history.add(message);
            // 上下文窗口截断
            history = ContextWindow.truncate(history, maxTokensPerSession, minRounds);
            // 写入 Redis
            String json = JSON.toJSONString(history);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(ttlHours));
        } catch (Exception e) {
            log.warn("[RedisChatMemory] addMessage 失败, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 批量添加消息。
     */
    public void addMessages(String sessionId, List<ChatMessage> messages) {
        if (sessionId == null || sessionId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }
        for (ChatMessage msg : messages) {
            addMessage(sessionId, msg);
        }
    }

    /**
     * 获取指定会话的对话历史（只读副本）。
     */
    public List<ChatMessage> getHistory(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        return getHistoryInternal(sessionId);
    }

    /**
     * 获取当前 token 总数。
     */
    public int getTokenCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        return ContextWindow.totalTokens(getHistoryInternal(sessionId));
    }

    /**
     * 获取消息数。
     */
    public int getMessageCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ChatMessage> history = getHistoryInternal(sessionId);
        return history.size();
    }

    /**
     * 清除指定会话历史。
     */
    public void clear(String sessionId) {
        if (sessionId == null) return;
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
            log.info("[RedisChatMemory] 清除会话历史: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[RedisChatMemory] clear 失败: {}", e.getMessage());
        }
    }

    /**
     * 内部方法：从 Redis 读取历史列表（可变副本）。
     */
    private List<ChatMessage> getHistoryInternal(String sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            List<ChatMessage> list = JSON.parseObject(json, new TypeReference<List<ChatMessage>>() {});
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            log.warn("[RedisChatMemory] getHistory 失败, sessionId={}: {}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
