package com.njydsz.pmis.userinfo.server.service.auth;

import java.util.List;

import com.njydsz.pmis.userinfo.domain.entity.user.UserSessionDO;

/**
 * 用户会话管理
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface SessionService {

    /** 默认最大并发会话数 */
    int DEFAULT_MAX_CONCURRENT_SESSIONS = 5;

    /**
     * 创建会话
     *
     * @param userId        用户 ID
     * @param clientIp      客户端 IP
     * @param userAgent     User-Agent 头
     * @param deviceType    设备类型：PC/APP/H5
     * @param expireSeconds 会话有效期（秒）
     * @return 创建的会话实体
     */
    UserSessionDO create(String userId, String clientIp, String userAgent, String deviceType, int expireSeconds);

    /**
     * 更新最后活跃时间
     *
     * @param sessionId 会话 ID
     */
    void touch(String sessionId);

    /**
     * 失效指定会话
     *
     * @param sessionId 会话 ID
     * @param reason    失效原因
     */
    void invalidate(String sessionId, String reason);

    /**
     * 强踢该用户其他活跃会话（同账号单点登录）
     *
     * @param userId        用户 ID
     * @param keepSessionId 保留的会话 ID
     * @return 被踢下线的会话数
     */
    int kickOthers(String userId, String keepSessionId);

    /**
     * 查询用户活跃会话
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    List<UserSessionDO> listActive(String userId);

    /**
     * 查询会话
     *
     * @param sessionId 会话 ID
     * @return 会话实体，不存在时返回 null
     */
    UserSessionDO get(String sessionId);

    /**
     * 清理过期会话
     *
     * @return 清理的会话数
     */
    int cleanExpired();

    /**
     * 强制执行最大并发会话数限制（P2-11 安全闭环）
     *
     * <p>当用户活跃会话数超过 maxSessions 时，自动踢出最早的会话。
     * 在 create 方法内部调用，也可在外部（如登录流程）手动调用。
     *
     * @param userId     用户 ID
     * @param maxSessions 最大并发会话数
     * @return 被踢出的会话数
     */
    int enforceMaxSessions(String userId, int maxSessions);
}
