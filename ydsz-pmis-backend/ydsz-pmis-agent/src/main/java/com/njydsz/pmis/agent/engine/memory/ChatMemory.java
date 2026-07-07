package com.njydsz.pmis.agent.engine.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对话记忆管理器（P1-3 落地；P1-8 修复内存泄漏）
 *
 * <p>对标 LangChain ConversationBufferMemory / Coze ChatMemory，
 * 按 sessionId 管理多轮对话历史，支持上下文窗口自动截断。
 *
 * <p>核心能力：
 * <ul>
 *   <li>按 sessionId 隔离不同会话的对话历史</li>
 *   <li>添加消息时自动计算 token 数</li>
 *   <li>支持按最大 token 数自动截断（滑动窗口）</li>
 *   <li>支持获取只读历史快照（防止外部修改）</li>
 *   <li>线程安全（基于 Caffeine Cache）</li>
 *   <li><b>P1-8</b>：会话级 LRU + TTL（maximumSize + expireAfterAccess），
 *       防止长期运行后积累大量会话历史导致 OOM</li>
 * </ul>
 *
 * <p>当前为内存实现（重启后丢失），后续可扩展为 Redis 持久化版本。
 *
 * <p>典型用法：
 * <pre>
 * // 添加用户消息
 * chatMemory.addMessage("session-001", ChatMessage.user("你好"));
 * // 添加助手回复
 * chatMemory.addMessage("session-001", ChatMessage.assistant("你好，我是助手"));
 * // 获取历史（已截断）
 * List&lt;ChatMessage&gt; history = chatMemory.getHistory("session-001");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@Slf4j
@Component
public class ChatMemory {

    /** 默认单会话最大 token 数（超出自动截断） */
    public static final int DEFAULT_MAX_TOKENS_PER_SESSION = ContextWindow.DEFAULT_MAX_TOKENS;

    /** 默认保留的最小轮数（截断时保证至少保留这么多轮） */
    public static final int DEFAULT_MIN_ROUNDS = ContextWindow.DEFAULT_MIN_ROUNDS;

    /** 默认最大会话数（LRU 容量上限，P1-8） */
    public static final int DEFAULT_MAX_SESSIONS = 1000;

    /** 默认会话过期时间（分钟，最后一次访问后过期，P1-8） */
    public static final long DEFAULT_SESSION_EXPIRE_MINUTES = 30;

    /** 按 sessionId 存储对话历史（Caffeine 提供 LRU 淘汰 + TTL 过期，P1-8） */
    private final Cache<String, List<ChatMessage>> sessions;

    /** 单会话最大 token 数 */
    private final int maxTokensPerSession;

    /** 截断时保留的最小轮数 */
    private final int minRounds;

    /**
     * 默认构造器：使用默认配置。
     */
    public ChatMemory() {
        this(DEFAULT_MAX_TOKENS_PER_SESSION, DEFAULT_MIN_ROUNDS,
                DEFAULT_MAX_SESSIONS, DEFAULT_SESSION_EXPIRE_MINUTES);
    }

    /**
     * 自定义配置构造器（保留兼容旧调用方，会话容量与过期使用默认值）。
     *
     * @param maxTokensPerSession 单会话最大 token 数
     * @param minRounds           截断时保留的最小轮数
     */
    public ChatMemory(int maxTokensPerSession, int minRounds) {
        this(maxTokensPerSession, minRounds,
                DEFAULT_MAX_SESSIONS, DEFAULT_SESSION_EXPIRE_MINUTES);
    }

    /**
     * 自定义配置构造器（P1-8：支持会话级 LRU 容量与 TTL 过期）。
     *
     * @param maxTokensPerSession 单会话最大 token 数
     * @param minRounds           截断时保留的最小轮数
     * @param maxSessions         最大会话数（LRU 容量上限）
     * @param expireMinutes       会话过期时间（分钟，最后一次访问后过期）
     */
    public ChatMemory(int maxTokensPerSession, int minRounds,
                      int maxSessions, long expireMinutes) {
        this.maxTokensPerSession = maxTokensPerSession > 0
                ? maxTokensPerSession : DEFAULT_MAX_TOKENS_PER_SESSION;
        this.minRounds = minRounds >= 0 ? minRounds : DEFAULT_MIN_ROUNDS;
        int sessions = maxSessions > 0 ? maxSessions : DEFAULT_MAX_SESSIONS;
        long minutes = expireMinutes > 0 ? expireMinutes : DEFAULT_SESSION_EXPIRE_MINUTES;
        // Caffeine：maximumSize 实现 LRU 淘汰，expireAfterAccess 实现 TTL 过期
        this.sessions = Caffeine.newBuilder()
                .maximumSize(sessions)
                .expireAfterAccess(minutes, TimeUnit.MINUTES)
                .build();
        log.info("[ChatMemory] 初始化完成, maxTokensPerSession={}, minRounds={}, maxSessions={}, expireMinutes={}",
                this.maxTokensPerSession, this.minRounds, sessions, minutes);
    }

    /**
     * 添加消息到指定会话。
     *
     * <p>自动计算 token 数并触发上下文窗口截断。
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    public void addMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank() || message == null) {
            return;
        }

        // 计算 token 数（若未设置）
        if (message.getTokenCount() <= 0) {
            message.setTokenCount(TokenCounter.estimate(message.getContent()));
        }

        sessions.asMap().compute(sessionId, (key, existing) -> {
            List<ChatMessage> list = existing == null
                    ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(message);
            // 触发上下文窗口截断
            return ContextWindow.truncate(list, maxTokensPerSession, minRounds);
        });
    }

    /**
     * 批量添加消息到指定会话。
     *
     * @param sessionId 会话 ID
     * @param messages  消息列表
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
     *
     * <p>读取会刷新该会话的访问时间（TTL 续期），符合"活跃会话不被过期"的语义。
     *
     * @param sessionId 会话 ID
     * @return 对话历史列表（若会话不存在返回空列表）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<ChatMessage> history = sessions.getIfPresent(sessionId);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * 获取指定会话的当前 token 总数。
     *
     * @param sessionId 会话 ID
     * @return token 总数（会话不存在返回 0）
     */
    public int getTokenCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ChatMessage> history = sessions.getIfPresent(sessionId);
        return ContextWindow.totalTokens(history);
    }

    /**
     * 获取指定会话的消息数。
     *
     * @param sessionId 会话 ID
     * @return 消息数（会话不存在返回 0）
     */
    public int getMessageCount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ChatMessage> history = sessions.getIfPresent(sessionId);
        return history == null ? 0 : history.size();
    }

    /**
     * 清除指定会话的对话历史。
     *
     * @param sessionId 会话 ID
     */
    public void clear(String sessionId) {
        if (sessionId == null) return;
        sessions.invalidate(sessionId);
        log.info("[ChatMemory] 清除会话历史: sessionId={}", sessionId);
    }

    /**
     * 清除所有会话历史。
     */
    public void clearAll() {
        sessions.invalidateAll();
        log.info("[ChatMemory] 清除所有会话历史");
    }

    /**
     * 获取当前活跃会话数。
     *
     * <p>会先触发一次清理（cleanUp）以回收已过期/已淘汰的会话，保证返回值尽量准确。
     *
     * @return 活跃会话数
     */
    public int getActiveSessionCount() {
        sessions.cleanUp();
        return (int) sessions.estimatedSize();
    }
}
