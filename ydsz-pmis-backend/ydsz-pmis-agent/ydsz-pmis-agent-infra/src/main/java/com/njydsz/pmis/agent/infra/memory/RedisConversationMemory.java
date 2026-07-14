package com.njydsz.pmis.agent.infra.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.njydsz.pmis.common.json.YdszJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.MessageRole;
import com.njydsz.pmis.agent.domain.model.TokenUsage;

/**
 * Redis 对话记忆实现
 *
 * <p>使用 Redis List 存储对话消息（RPUSH + LTRIM 实现滑动窗口）。
 * 每个对话的 key 格式：{@code pmis:agent:memory:{conversationId}}
 * 默认 TTL 24 小时，可通过配置调整。
 *
 * <h3>防无限增长</h3>
 * <p>每次 RPUSH 后执行 LTRIM，将 List 截断为 {@code maxListSize} 条，
 * 避免单对话消息数无限膨胀。{@code maxListSize} 默认为 {@code maxMessages * 2}，
 * 保留一定余量供滑动窗口检索。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class RedisConversationMemory implements ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationMemory.class);
    private static final String KEY_PREFIX = "pmis:agent:memory:";
    private static final int DEFAULT_TTL_HOURS = 24;
    private static final int DEFAULT_MAX_LIST_SIZE = 50;

    private final StringRedisTemplate redisTemplate;
    private final int ttlHours;
    private final int maxListSize;

    public RedisConversationMemory(StringRedisTemplate redisTemplate) {
        this(redisTemplate, DEFAULT_TTL_HOURS, DEFAULT_MAX_LIST_SIZE);
    }

    public RedisConversationMemory(StringRedisTemplate redisTemplate, int ttlHours) {
        this(redisTemplate, ttlHours, DEFAULT_MAX_LIST_SIZE);
    }

    public RedisConversationMemory(StringRedisTemplate redisTemplate, int ttlHours, int maxListSize) {
        this.redisTemplate = redisTemplate;
        this.ttlHours = ttlHours > 0 ? ttlHours : DEFAULT_TTL_HOURS;
        this.maxListSize = maxListSize > 0 ? maxListSize : DEFAULT_MAX_LIST_SIZE;
    }

    @Override
    public void save(String conversationId, ChatMessage message) {
        String key = KEY_PREFIX + conversationId;
        String json = serializeMessage(message);
        redisTemplate.opsForList().rightPush(key, json);
        redisTemplate.opsForList().trim(key, -maxListSize, -1);
        redisTemplate.expire(key, ttlHours, TimeUnit.HOURS);
    }

    @Override
    public List<ChatMessage> load(String conversationId, int maxMessages) {
        String key = KEY_PREFIX + conversationId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }
        long start = Math.max(0, size - maxMessages);
        List<String> rawList = redisTemplate.opsForList().range(key, start, size - 1);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = new ArrayList<>(rawList.size());
        for (String raw : rawList) {
            ChatMessage msg = deserializeMessage(raw);
            if (msg != null) {
                messages.add(msg);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    @Override
    public long count(String conversationId) {
        Long size = redisTemplate.opsForList().size(KEY_PREFIX + conversationId);
        return size != null ? size : 0;
    }

    /**
     * 检查 Redis 连接是否可用
     *
     * @return true 表示连接正常
     */
    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + "health-check"));
        } catch (Exception e) {
            log.warn("[Memory] Redis 连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    private String serializeMessage(ChatMessage message) {
        SerializedMessage sm = new SerializedMessage();
        sm.id = message.getId();
        sm.role = message.getRole().name();
        sm.content = message.getContent();
        sm.conversationId = message.getConversationId();
        sm.createdAt = message.getCreatedAt() != null ? message.getCreatedAt().toString() : null;
        if (message.getTokenUsage() != null) {
            sm.promptTokens = message.getTokenUsage().getPromptTokens();
            sm.completionTokens = message.getTokenUsage().getCompletionTokens();
        }
        return YdszJson.toJson(sm);
    }

    private ChatMessage deserializeMessage(String json) {
        try {
            SerializedMessage sm = YdszJson.toObject(json, SerializedMessage.class);
            MessageRole role = MessageRole.valueOf(sm.role);
            TokenUsage usage = null;
            if (sm.promptTokens > 0 || sm.completionTokens > 0) {
                usage = new TokenUsage(sm.promptTokens, sm.completionTokens);
            }
            LocalDateTime createdAt = sm.createdAt != null
                    ? LocalDateTime.parse(sm.createdAt) : LocalDateTime.now();
            return new ChatMessage(sm.id, role, sm.content, sm.conversationId,
                    createdAt, null, null, usage);
        } catch (Exception e) {
            log.warn("[Memory] 反序列化消息失败: {}", e.getMessage());
            return null;
        }
    }

    private static class SerializedMessage {
        public String id;
        public String role;
        public String content;
        public String conversationId;
        public String createdAt;
        public int promptTokens;
        public int completionTokens;
    }
}
