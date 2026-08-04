package com.remisoft.common.socket.ratelimit;

import java.util.concurrent.atomic.AtomicLong;


import com.remisoft.common.socket.config.WebSocketProperties;
import com.remisoft.common.socket.session.OnlineUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 连接数限制器（P2-1）。
 *
 * <p>在握手阶段检查：
 * <ul>
 *   <li>全局连接数上限：防止连接耗尽攻击</li>
 *   <li>每用户连接数上限：防止单用户多连接刷量</li>
 * </ul>
 *
 * <p>全局连接数使用本地 {@link AtomicLong} 计数（配合 HealthIndicator），
 * 每用户连接数通过 {@link OnlineUserService#getSessionCount} 查询 Redis。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ConnectionLimiter {

    private final OnlineUserService onlineUserService;
    private final WebSocketProperties properties;
    private final AtomicLong activeConnections;

    /**
     * 检查是否允许新连接。
     *
     * @param userId 用户 ID
     * @return true 表示允许连接
     */
    public boolean allowConnection(String userId) {
        int maxGlobal = properties.getConnectionLimit().getMaxGlobalConnections();
        int maxPerUser = properties.getConnectionLimit().getMaxPerUserConnections();

        if (activeConnections.get() >= maxGlobal) {
            log.warn("[WS-ConnLimit] 全局连接数超限: active={}, max={}",
                    activeConnections.get(), maxGlobal);
            return false;
        }

        if (userId != null) {
            long userCount = onlineUserService.getSessionCount(userId);
            if (userCount >= maxPerUser) {
                log.warn("[WS-ConnLimit] 用户连接数超限: userId={}, count={}, max={}",
                        userId, userCount, maxPerUser);
                return false;
            }
        }

        return true;
    }
}
