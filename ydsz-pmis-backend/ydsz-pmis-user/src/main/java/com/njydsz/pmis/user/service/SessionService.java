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
     *
     * @param userId        用户 ID
     * @param clientIp      客户端 IP
     * @param userAgent     User-Agent 头
     * @param deviceType    设备类型：PC/APP/H5
     * @param expireSeconds 会话有效期（秒）
     * @return 创建的会话实体
     */
    UserSessionDO create(Long userId, String clientIp, String userAgent, String deviceType, int expireSeconds);

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
    int kickOthers(Long userId, String keepSessionId);

    /**
     * 查询用户活跃会话
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    List<UserSessionDO> listActive(Long userId);

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
}
