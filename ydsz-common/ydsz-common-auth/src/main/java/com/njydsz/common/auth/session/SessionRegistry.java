package com.njydsz.common.auth.session;

import java.util.Collection;
import java.util.List;

import com.njydsz.common.auth.model.SessionInfo;

/**
 * 会话注册表接口。
 *
 * <p>管理用户活跃会话的全生命周期，支持：
 *
 * <ul>
 *   <li>注册/撤销会话</li>
 *   <li>查询用户活跃会话列表</li>
 *   <li>限制最大并发会话</li>
 *   <li>远程踢出用户（全部或部分会话）</li>
 * </ul>
 *
 * <p>默认提供两种实现：
 *
 * <ul>
 * <li>RedisSessionRegistry — 基于 Redis Hash 的生产实现（集群共享）</li>
 *   <li>LocalSessionRegistry — 基于 ConcurrentHashMap 的本地实现（降级/开发模式）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 * @see SessionInfo
 */
public interface SessionRegistry {

  /**
   * 注册新会话。
   *
   * <p>当用户成功登录、颁发新 Access Token 时调用。 若超过最大并发会话数，按策略自动踢出最早的会话。
   *
   * @param sessionInfo 会话信息（非 null，sessionId 唯一）
   */
  void register(SessionInfo sessionInfo);

  /**
   * 撤销指定会话。
   *
   * <p>当用户登出特定设备或 Token 被撤销时调用。
   *
   * @param sessionId 会话 ID（jti）
   */
  void revoke(String sessionId);

  /**
   * 撤销用户全部会话。
   *
   * <p>当管理员强制下线用户、用户修改密码或全局安全事件时调用。
   *
   * @param userId 用户 ID
   */
  void revokeAll(String userId);

  /**
   * 判断会话是否有效（已注册且未撤销）。
   *
   * @param sessionId 会话 ID
   * @return 有效返回 true
   */
  boolean isSessionValid(String sessionId);

  /**
   * 获取用户当前活跃会话列表。
   *
   * @param userId 用户 ID
   * @return 会话列表（按签发时间升序）；无会话时返回空列表
   */
  List<SessionInfo> listSessions(String userId);

  /**
   * 获取用户当前活跃会话数。
   *
   * @param userId 用户 ID
   * @return 会话数
   */
  int getSessionCount(String userId);

  /**
   * 获取指定会话信息。
   *
   * @param sessionId 会话 ID
   * @return 会话信息；未找到时返回 null
   */
  SessionInfo getSession(String sessionId);

  /**
   * 获取所有已注册会话 ID（主要用于跨节点同步）。
   *
   * @return 不可变会话 ID 集合
   */
  default Collection<String> getAllSessionIds() {
    return List.of();
  }
}
