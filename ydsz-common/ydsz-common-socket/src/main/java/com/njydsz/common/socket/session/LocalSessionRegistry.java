package com.njydsz.common.socket.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.WebSocketSession;

/**
 * 本地 WebSocket Session 注册表。
 *
 * <p>维护本节点内 sessionId → WebSocketSession 的映射，以及
 * userId → sessionIds 的索引，用于多端登录策略执行。
 *
 * <p>该注册表由 {@code WebSocketHandlerDecorator} 在连接建立/关闭时自动维护，
 * 业务层仅需读取，不应直接操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LocalSessionRegistry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** sessionId → WebSocketSession */
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    /** userId → sessionIds */
    private final Map<String, Set<String>> userSessionsMap = new ConcurrentHashMap<>();

    /**
     * 注册 Session。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     * @param session   WebSocket 会话
     */
    public void register(String userId, String sessionId, WebSocketSession session) {
        sessionMap.put(sessionId, session);
        userSessionsMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /**
     * 注销 Session。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     */
    public void unregister(String userId, String sessionId) {
        sessionMap.remove(sessionId);
        Set<String> sessionIds = userSessionsMap.get(userId);
        if (sessionIds != null) {
            sessionIds.remove(sessionId);
            if (sessionIds.isEmpty()) {
                userSessionsMap.remove(userId);
            }
        }
    }

    /**
     * 获取指定用户在本节点的所有 Session ID。
     *
     * @param userId 用户 ID
     * @return Session ID 列表（不可修改）
     */
    public List<String> getSessionIds(String userId) {
        Set<String> sessionIds = userSessionsMap.get(userId);
        return sessionIds != null ? new ArrayList<>(sessionIds) : new ArrayList<>();
    }

    /**
     * 获取指定 Session。
     *
     * @param sessionId Session ID
     * @return WebSocketSession，不存在时返回 null
     */
    public WebSocketSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * 获取指定用户在本节点的 Session 数量。
     *
     * @param userId 用户 ID
     * @return Session 数量
     */
    public int getSessionCount(String userId) {
        Set<String> sessionIds = userSessionsMap.get(userId);
        return sessionIds != null ? sessionIds.size() : 0;
    }

    /**
     * 清空所有注册信息（仅供测试使用）。
     */
    public void clear() {
        sessionMap.clear();
        userSessionsMap.clear();
    }
}
