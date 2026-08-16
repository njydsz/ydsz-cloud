package com.njydsz.common.socket.lifecycle;

/**
 * WebSocket 连接生命周期监听器接口（P3-5）。
 *
 * <p>业务方可实现此接口，监听 WebSocket 连接建立和断开事件， 执行自定义处理逻辑（如更新用户最后在线时间、清理资源等）。
 *
 * <p>实现类注册为 Spring Bean 后，{@code WebSocketSessionEventListener} 会在连接/断开时自动调用所有注册的 Listener。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface WebSocketConnectionListener {

  /**
   * 连接建立时调用。
   *
   * @param userId 用户 ID
   * @param sessionId Session ID
   */
  void onConnected(String userId, String sessionId);

  /**
   * 连接断开时调用。
   *
   * @param userId 用户 ID
   * @param sessionId Session ID
   */
  void onDisconnected(String userId, String sessionId);
}
