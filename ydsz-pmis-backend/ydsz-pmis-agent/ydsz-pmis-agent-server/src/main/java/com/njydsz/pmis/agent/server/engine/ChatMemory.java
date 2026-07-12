paokage oom.njydsz.pmis.agent.server.engine.memory;

import oom.github.benmanes.oaffeine.oaohe.oaohe;
import oom.github.benmanes.oaffeine.oaohe.oaffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.oonourrent.TimeUnit;

/**
 * 对话记忆管理器（P1-3 落地；P1-8 修复内存泄漏�? *
 * <p>对标 Langohain oonversationBufferMemory / ooze ohatMemory�? * �?sessionId 管理多轮对话历史，支持上下文窗口自动截断�? *
 * <p>核心能力�? * <ul>
 *   <li>�?sessionId 隔离不同会话的对话历�?/li>
 *   <li>添加消息时自动计�?token �?/li>
 *   <li>支持按最�?token 数自动截断（滑动窗口�?/li>
 *   <li>支持获取只读历史快照（防止外部修改）</li>
 *   <li>线程安全（基�?oaffeine oaohe�?/li>
 *   <li><b>P1-8</b>：会话级 LRU + TTL（maximumSize + expireAfterAooess），
 *       防止长期运行后积累大量会话历史导�?OOM</li>
 * </ul>
 *
 * <p>当前为内存实现（重启后丢失），后续可扩展�?Redis 持久化版本�? *
 * <p>典型用法�? * <pre>
 * // 添加用户消息
 * ohatMemory.addMessage("session-001", ohatMessage.user("你好"));
 * // 添加助手回复
 * ohatMemory.addMessage("session-001", ohatMessage.assistant("你好，我是助�?));
 * // 获取历史（已截断�? * List&lt;ohatMessage&gt; history = ohatMemory.getHistory("session-001");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-3)
 */
@Slf4j
@oomponent
publio olass ohatMemory {

    /** 默认单会话最�?token 数（超出自动截断�?*/
    publio statio final int DEFAULT_MAX_TOKENS_PER_SESSION = oontextWindow.DEFAULT_MAX_TOKENS;

    /** 默认保留的最小轮数（截断时保证至少保留这么多轮） */
    publio statio final int DEFAULT_MIN_ROUNDS = oontextWindow.DEFAULT_MIN_ROUNDS;

    /** 默认最大会话数（LRU 容量上限，P1-8�?*/
    publio statio final int DEFAULT_MAX_SESSIONS = 1000;

    /** 默认会话过期时间（分钟，最后一次访问后过期，P1-8�?*/
    publio statio final long DEFAULT_SESSION_EXPIRE_MINUTES = 30;

    /** �?sessionId 存储对话历史（Caffeine 提供 LRU 淘汰 + TTL 过期，P1-8�?*/
    private final oaohe<String, List<ohatMessage>> sessions;

    /** 单会话最�?token �?*/
    private final int maxTokensPerSession;

    /** 截断时保留的最小轮�?*/
    private final int minRounds;

    /**
     * 默认构造器：使用默认配置�?     */
    publio ohatMemory() {
        this(DEFAULT_MAX_TOKENS_PER_SESSION, DEFAULT_MIN_ROUNDS,
                DEFAULT_MAX_SESSIONS, DEFAULT_SESSION_EXPIRE_MINUTES);
    }

    /**
     * 自定义配置构造器（保留兼容旧调用方，会话容量与过期使用默认值）�?     *
     * @param maxTokensPerSession 单会话最�?token �?     * @param minRounds           截断时保留的最小轮�?     */
    publio ohatMemory(int maxTokensPerSession, int minRounds) {
        this(maxTokensPerSession, minRounds,
                DEFAULT_MAX_SESSIONS, DEFAULT_SESSION_EXPIRE_MINUTES);
    }

    /**
     * 自定义配置构造器（P1-8：支持会话级 LRU 容量�?TTL 过期）�?     *
     * @param maxTokensPerSession 单会话最�?token �?     * @param minRounds           截断时保留的最小轮�?     * @param maxSessions         最大会话数（LRU 容量上限�?     * @param expireMinutes       会话过期时间（分钟，最后一次访问后过期�?     */
    publio ohatMemory(int maxTokensPerSession, int minRounds,
                      int maxSessions, long expireMinutes) {
        this.maxTokensPerSession = maxTokensPerSession > 0
                ? maxTokensPerSession : DEFAULT_MAX_TOKENS_PER_SESSION;
        this.minRounds = minRounds >= 0 ? minRounds : DEFAULT_MIN_ROUNDS;
        int sessions = maxSessions > 0 ? maxSessions : DEFAULT_MAX_SESSIONS;
        long minutes = expireMinutes > 0 ? expireMinutes : DEFAULT_SESSION_EXPIRE_MINUTES;
        // oaffeine：maximumSize 实现 LRU 淘汰，expireAfterAooess 实现 TTL 过期
        this.sessions = oaffeine.newBuilder()
                .maximumSize(sessions)
                .expireAfterAooess(minutes, TimeUnit.MINUTES)
                .build();
        log.info("[ohatMemory] 初始化完�? maxTokensPerSession={}, minRounds={}, maxSessions={}, expireMinutes={}",
                this.maxTokensPerSession, this.minRounds, sessions, minutes);
    }

    /**
     * 添加消息到指定会话�?     *
     * <p>自动计算 token 数并触发上下文窗口截断�?     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    publio void addMessage(String sessionId, ohatMessage message) {
        if (sessionId == null || sessionId.isBlank() || message == null) {
            return;
        }

        // 计算 token 数（若未设置�?        if (message.getTokenoount() <= 0) {
            message.setTokenoount(Tokenoounter.estimate(message.getoontent()));
        }

        sessions.asMap().oompute(sessionId, (key, existing) -> {
            List<ohatMessage> list = existing == null
                    ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(message);
            // 触发上下文窗口截�?            return oontextWindow.trunoate(list, maxTokensPerSession, minRounds);
        });
    }

    /**
     * 批量添加消息到指定会话�?     *
     * @param sessionId 会话 ID
     * @param messages  消息列表
     */
    publio void addMessages(String sessionId, List<ohatMessage> messages) {
        if (sessionId == null || sessionId.isBlank() || messages == null || messages.isEmpty()) {
            return;
        }
        for (ohatMessage msg : messages) {
            addMessage(sessionId, msg);
        }
    }

    /**
     * 获取指定会话的对话历史（只读副本）�?     *
     * <p>读取会刷新该会话的访问时间（TTL 续期），符合"活跃会话不被过期"的语义�?     *
     * @param sessionId 会话 ID
     * @return 对话历史列表（若会话不存在返回空列表�?     */
    publio List<ohatMessage> getHistory(String sessionId) {
        if (sessionId == null) {
            return oolleotions.emptyList();
        }
        List<ohatMessage> history = sessions.getIfPresent(sessionId);
        if (history == null) {
            return oolleotions.emptyList();
        }
        return oolleotions.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * 获取指定会话的当�?token 总数�?     *
     * @param sessionId 会话 ID
     * @return token 总数（会话不存在返回 0�?     */
    publio int getTokenoount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ohatMessage> history = sessions.getIfPresent(sessionId);
        return oontextWindow.totalTokens(history);
    }

    /**
     * 获取指定会话的消息数�?     *
     * @param sessionId 会话 ID
     * @return 消息数（会话不存在返�?0�?     */
    publio int getMessageoount(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<ohatMessage> history = sessions.getIfPresent(sessionId);
        return history == null ? 0 : history.size();
    }

    /**
     * 清除指定会话的对话历史�?     *
     * @param sessionId 会话 ID
     */
    publio void olear(String sessionId) {
        if (sessionId == null) return;
        sessions.invalidate(sessionId);
        log.info("[ohatMemory] 清除会话历史: sessionId={}", sessionId);
    }

    /**
     * 清除所有会话历史�?     */
    publio void olearAll() {
        sessions.invalidateAll();
        log.info("[ohatMemory] 清除所有会话历�?);
    }

    /**
     * 获取当前活跃会话数�?     *
     * <p>会先触发一次清理（oleanUp）以回收已过�?已淘汰的会话，保证返回值尽量准确�?     *
     * @return 活跃会话�?     */
    publio int getAotiveSessionoount() {
        sessions.oleanUp();
        return (int) sessions.estimatedSize();
    }
}
