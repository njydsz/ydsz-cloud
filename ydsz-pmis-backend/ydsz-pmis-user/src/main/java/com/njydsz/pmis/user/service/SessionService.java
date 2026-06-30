package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.entity.UserSessionDO;

import java.util.List;

/**
 * 用户会话管理
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SessionService {

    /**
     * 创建会话
     */
    UserSessionDO create(Long userId, String clientIp, String userAgent, String deviceType, int expireSeconds);

    /**
     * 更新最后活跃时间
     */
    void touch(String sessionId);

    /**
     * 失效指定会话
     */
    void invalidate(String sessionId, String reason);

    /**
     * 强踢该用户其他活跃会话（同账号单点登录）
     */
    int kickOthers(Long userId, String keepSessionId);

    /**
     * 查询用户活跃会话
     */
    List<UserSessionDO> listActive(Long userId);

    /**
     * 查询会话
     */
    UserSessionDO get(String sessionId);

    /**
     * 清理过期会话
     */
    int cleanExpired();
}
