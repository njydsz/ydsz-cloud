package com.njydsz.common.netty.session;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.util.concurrent.CompletableFuture;

/**
 * 连接会话 — Channel 的业务抽象层。
 *
 * <p>封装 Channel 与业务标识（sessionId / bizId）的映射关系， 提供属性附件（attr）、生命周期状态追踪、最后交互时间记录等能力。
 *
 * <p>每个 TCP 连接对应一个 ConnectionSession 实例，由框架在 Channel 激活时自动创建， 在 Channel 关闭时自动清理。业务层通过 {@link SessionRepository} 查询和遍历活跃会话。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 通过 SessionRepository 查找会话
 * ConnectionSession session = sessionRepository.getByBizId("user-123").get(0);
 * session.setAttr("role", "admin");
 * session.send(new Notification("系统公告"));
 * }</pre>
 *
 * <p><b>线程安全：</b>此接口的实现类必须确保所有方法在 Netty EventLoop 线程和其他 调用线程中都能安全使用。attr 操作使用 {@link java.util.concurrent.ConcurrentHashMap} 实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SessionRepository
 * @see ChannelState
 */
public interface ConnectionSession {

  /**
   * 获取全局唯一会话 ID。
   *
   * <p>格式为 {@code channelId@timestamp}，保证集群内唯一。
   *
   * @return 会话 ID（永不为 {@code null}）
   */
  String getSessionId();

  /**
   * 获取底层 Netty Channel。
   *
   * @return Channel 实例
   */
  Channel getChannel();

  /**
   * 获取业务标识（用户 ID / 设备 ID / 租户 ID 等）。
   *
   * <p>业务标识在认证成功后由 {@link com.njydsz.common.netty.auth.ConnectionAuthenticator} 填充， 认证前返回 {@code null}。
   *
   * @return 业务标识，认证前返回 {@code null}
   */
  String getBizId();

  /**
   * 设置业务标识。
   *
   * <p>仅允许设置一次（幂等）。重复设置时若值与原值相同则静默忽略， 若不同则抛出 {@link IllegalStateException}。
   *
   * @param bizId 业务标识（不可为 {@code null} 或空字符串）
   */
  void setBizId(String bizId);

  /**
   * 获取当前连接状态。
   *
   * @return Channel 生命周期状态
   */
  ChannelState getState();

  /**
   * 转换到新的状态（状态机流转）。
   *
   * <p>状态流转规则：
   *
   * <ul>
   *   <li>允许：AUTHENTICATED → DRAINING → CLOSED
   *   <li>允许：任意状态 → CLOSED
   *   <li>其他非法流转将抛出 {@link IllegalStateException}
   * </ul>
   *
   * @param newState 目标状态
   * @return 是否成功流转（已在目标状态时返回 false）
   */
  boolean transitionState(ChannelState newState);

  /**
   * 获取连接建立时间（毫秒时间戳）。
   *
   * @return 连接建立时间
   */
  long getConnectedTime();

  /**
   * 获取最后交互时间（毫秒时间戳）。
   *
   * <p>每次发送、接收消息或调用 {@link #touch()} 时更新。
   *
   * @return 最后交互时间
   */
  long getLastInteractionTime();

  /**
   * 更新最后交互时间为当前时间。
   *
   * <p>通常在收到业务消息或发送重要数据后调用。
   */
  void touch();

  /**
   * 设置业务属性附件（线程安全）。
   *
   * <p>用于存储会话级别的临时数据，如：权限角色、房间 ID、协议版本等。
   *
   * @param key 属性 key
   * @param value 属性值
   * @param <T> 值类型
   */
  <T> void setAttr(String key, T value);

  /**
   * 获取业务属性附件。
   *
   * @param key 属性 key
   * @param value 默认值（key 不存在时返回）
   * @param <T> 值类型
   * @return 属性值或默认值
   */
  <T> T getAttr(String key, T defaultValue);

  /**
   * 移除业务属性附件。
   *
   * @param key 属性 key
   * @param <T> 值类型
   * @return 被移除的属性值，不存在时返回 {@code null}
   */
  <T> T removeAttr(String key);

  /**
   * 检查是否含有指定属性。
   *
   * @param key 属性 key
   * @return true 表示存在
   */
  boolean hasAttr(String key);

  /**
   * 判断连接是否活跃（AUTHENTICATED 或 CONNECTED 状态）。
   *
   * @return true 表示连接活跃且已认证
   */
  default boolean isActive() {
    ChannelState state = getState();
    return state == ChannelState.AUTHENTICATED || state == ChannelState.CONNECTED;
  }

  /**
   * 判断连接是否已认证（AUTHENTICATED 状态）。
   *
   * @return true 表示已认证
   */
  default boolean isAuthenticated() {
    return getState() == ChannelState.AUTHENTICATED;
  }

  /**
   * 同步发送消息到对端。
   *
   * @param message 消息对象
   * @return ChannelFuture
   * @throws IllegalStateException 连接未活跃时抛出
   */
  ChannelFuture send(Object message);

  /**
   * 异步发送消息到对端，返回 CompletableFuture。
   *
   * @param message 消息对象
   * @return CompletableFuture
   * @throws IllegalStateException 连接未活跃时抛出
   */
  CompletableFuture<Void> sendAsync(Object message);

  /**
   * 关闭连接。
   *
   * <p>触发 DRAINING 状态（在途消息继续处理），最终流转到 CLOSED。
   *
   * @return ChannelFuture
   */
  ChannelFuture close();

  /**
   * 强制关闭连接（不等在途消息）。
   *
   * <p>直接流转到 CLOSED 状态。
   */
  void closeImmediately();
}
