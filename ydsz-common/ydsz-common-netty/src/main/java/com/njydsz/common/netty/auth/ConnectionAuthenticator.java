package com.njydsz.common.netty.auth;

import com.njydsz.common.netty.session.ConnectionSession;

/**
 * 连接认证器 — 定义连接建立后执行认证的 SPI。
 *
 * <p>业务方实现此接口，在 AbstractNettyServer 的子类中配置。 Channel 建立后（CONNECT 阶段）自动触发认证流程。
 *
 * <p>认证流程：
 *
 * <ol>
 *   <li>框架在 Channel 激活后创建 {@link ConnectionSession}（状态为 {@link com.njydsz.common.netty.session.ChannelState#CONNECTED}）
 *   <li>等待客户端发送认证消息（通常为第一条消息）
 *   <li>调用 {@link #authenticate(ConnectionSession, Object)} 方法
 *   <li>认证通过 → 流转到 {@link com.njydsz.common.netty.session.ChannelState#AUTHENTICATED}
 *   <li>认证失败 → 关闭连接
 * </ol>
 *
 * <p>使用示例（Token 认证）：
 *
 * <pre>{@code
 * &#64;Component
 * public class TokenAuthenticator implements ConnectionAuthenticator {
 *     &#64;Override
 *     public AuthenticationResult authenticate(ConnectionSession session, Object authPayload) {
 *         if (authPayload instanceof AuthMessage auth) {
 *             String userId = tokenService.validate(auth.getToken());
 *             if (userId != null) {
 *                 return AuthenticationResult.success(userId);
 *             }
 *             return AuthenticationResult.failed("Token 无效或已过期");
 *         }
 *         return AuthenticationResult.failed("认证消息格式错误");
 *     }
 *
 *     &#64;Override
 *     public long authTimeoutMs() {
 *         return 30_000L; // 30 秒认证超时
 *     }
 * }
 * }</pre>
 *
 * <p><b>线程安全：</b>此接口的实现必须线程安全，同一时刻可能有多个 Channel 并发认证。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AuthenticationResult
 * @see com.njydsz.common.netty.session.ChannelState
 */
public interface ConnectionAuthenticator {

  /**
   * 对连接进行认证。
   *
   * <p>实现类在此方法中验证客户端身份，返回认证结果。 认证通过后可通过 {@link ConnectionSession#setBizId(String)} 设置业务标识。
   *
   * <p>实现应遵循：
   *
   * <ul>
   *   <li>耗时操作应异步处理（返回 CompletableFuture 或单独线程池），避免阻塞 EventLoop
   *   <li>认证失败应返回明确的失败原因（用于响应客户端）
   *   <li>不可抛出未捕获异常 — 异常时视为认证失败
   * </ul>
   *
   * @param session 连接会话（状态为 AUTHENTICATING）
   * @param authPayload 认证载荷（通常是客户端发送的首条消息）
   * @return 认证结果（包含成功标识、业务标识或失败原因）
   */
  AuthenticationResult authenticate(ConnectionSession session, Object authPayload);

  /**
   * 认证超时时间（毫秒）。
   *
   * <p>在 {@link com.njydsz.common.netty.session.ChannelState#CONNECTED} 阶段， 如果超过此时间仍未完成认证，框架将自动关闭连接。
   *
   * <p>默认 10 秒。业务方可根据实际认证耗时调整（如需要远程 RPC 校验时延长）。
   *
   * @return 认证超时毫秒数（必须大于 0）
   */
  default long authTimeoutMs() {
    return 10_000L;
  }

  /**
   * 是否需要认证。
   *
   * <p>如果返回 false，则跳过认证阶段，连接直接进入 AUTHENTICATED 状态。 适用于内部服务间通信（已通过 mTLS 等底层认证）的场景。
   *
   * @return true 表示需要执行认证
   */
  default boolean isAuthRequired() {
    return true;
  }
}
