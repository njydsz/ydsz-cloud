package com.njydsz.common.netty.session;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 会话仓库 — 集中管理所有活跃连接会话。
 *
 * <p>提供会话的增删改查能力，支持按 sessionId、bizId 查找， 以及按自定义条件筛选。
 *
 * <p>此仓库通常由框架在 Channel 激活时自动注册会话， 在 Channel 关闭时自动移除。业务层也可在认证成功后 调用 {@link #updateBizId(String, String)} 更新会话的业务标识。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 按 sessionId 查找
 * ConnectionSession session = sessionRepository.getById("abc123@1695000000000");
 *
 * // 按 bizId 查找（支持同一业务 ID 多端登录）
 * List<ConnectionSession> sessions = sessionRepository.getByBizId("user-456");
 *
 * // 按条件筛选
 * List<ConnectionSession> adminSessions = sessionRepository.find(s ->
 *     "admin".equals(s.getAttr("role", null))
 * );
 *
 * // 向指定用户推送消息
 * sessionRepository.getByBizId("user-789").forEach(s -> s.send(notification));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ConnectionSession
 * @see DefaultConnectionSession
 */
public interface SessionRepository {

  /**
   * 注册会话。
   *
   * <p>通常在 Channel 激活时由框架调用，业务层极少需要直接调用。
   *
   * @param session 会话实例
   * @throws IllegalArgumentException session 为 null 或已存在相同 sessionId
   */
  void add(ConnectionSession session);

  /**
   * 移除会话。
   *
   * <p>通常在 Channel 关闭时由框架调用。
   *
   * @param sessionId 会话 ID
   * @return 被移除的会话，不存在时返回 null
   */
  ConnectionSession remove(String sessionId);

  /**
   * 按会话 ID 精确查找。
   *
   * @param sessionId 会话 ID
   * @return 会话实例，不存在时返回 null
   */
  ConnectionSession getById(String sessionId);

  /**
   * 按业务 ID 查找所有在线会话（支持同一 bizId 多端登录场景）。
   *
   * @param bizId 业务标识
   * @return 会话列表（永不返回 null，空时返回空列表）
   */
  List<ConnectionSession> getByBizId(String bizId);

  /**
   * 获取所有活跃会话。
   *
   * @return 不可修改的活跃会话集合
   */
  Collection<ConnectionSession> getAll();

  /**
   * 获取活跃会话总数。
   *
   * @return 活跃会话数
   */
  int size();

  /**
   * 按自定义条件筛选会话。
   *
   * @param predicate 筛选条件
   * @return 匹配的会话列表（永不返回 null）
   */
  List<ConnectionSession> find(Predicate<ConnectionSession> predicate);

  /**
   * 更新会话的业务 ID（认证成功后调用）。
   *
   * <p>触发 bizId 索引重建，确保 {@link #getByBizId(String)} 可查找到。
   *
   * @param sessionId 会话 ID
   * @param bizId 新的业务标识
   * @return 是否成功更新（sessionId 不存在时返回 false）
   */
  boolean updateBizId(String sessionId, String bizId);

  /**
   * 统计指定 bizId 的在线会话数。
   *
   * @param bizId 业务标识
   * @return 在线会话数
   */
  long countByBizId(String bizId);

  /**
   * 判断指定会话是否存在。
   *
   * @param sessionId 会话 ID
   * @return true 表示存在
   */
  boolean contains(String sessionId);
}
