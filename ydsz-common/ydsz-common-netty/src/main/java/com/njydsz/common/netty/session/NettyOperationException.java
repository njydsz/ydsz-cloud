package com.njydsz.common.netty.session;

/**
 * Netty 操作异常（unchecked）。
 *
 * <p>当 Netty 客户端/服务端在消息发送、RPC 响应处理、连接管理过程中遇到不可恢复错误时抛出。
 *
 * @author ydsz-team
 * @since 26.10.03
 */
public class NettyOperationException extends RuntimeException {

  /** 序列化版本号。 */
  private static final long serialVersionUID = 1L;

  /**
   * 构造 Netty 操作异常。
   *
   * @param message 异常描述信息
   * @param cause 根因异常（可为 null）
   */
  public NettyOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造 Netty 操作异常（无根因）。
   *
   * @param message 异常描述信息
   */
  public NettyOperationException(String message) {
    super(message);
  }
}
