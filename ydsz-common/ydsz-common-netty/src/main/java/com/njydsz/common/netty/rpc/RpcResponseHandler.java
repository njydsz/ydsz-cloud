package com.njydsz.common.netty.rpc;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 响应 Handler — 接收对端返回的 Response 并匹配到对应的 pending 请求。
 *
 * <p>此 Handler 为 {@code @ChannelHandler.Sharable}，可安全地在多个 Channel 间共享， 因为它不持有 Channel 特有的状态，仅通过构造参数访问 {@link NettyRpcClient}。
 *
 * <p>典型用法（在子类的 initRpcPipeline 中添加）：
 *
 * <pre>{@code
 * &#64;Override
 * protected void initRpcPipeline(SocketChannel ch) {
 *     // 添加 LengthFieldFrame 编解码器
 *     LengthFieldCodec.addToPipeline(ch.pipeline());
 *     // 添加 RPC 消息编解码器（序列化/反序列化 RpcMessage）
 *     ch.pipeline().addLast(new RpcMessageCodec());
 *     // 添加 RPC 响应 Handler（必须在业务 Handler 之前）
 *     ch.pipeline().addLast(new RpcResponseHandler(this));
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@ChannelHandler.Sharable
public class RpcResponseHandler extends SimpleChannelInboundHandler<RpcMessage> {

  /** 关联的 RPC Client 实例（用于回调 handleResponse / handleException） */
  private final NettyRpcClient rpcClient;

  /**
   * 构造 RPC 响应 Handler。
   *
   * @param rpcClient 关联的 NettyRpcClient 实例
   */
  public RpcResponseHandler(NettyRpcClient rpcClient) {
    this.rpcClient = rpcClient;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) throws Exception {
    if (msg == null) {
      return;
    }
    long requestId = msg.getRequestId();
    if (msg.isResponse()) {
      // 正常响应：回调完成对应的 Future
      rpcClient.handleResponse(requestId, msg.getBody());
    } else if (msg.isException()) {
      // 异常响应：回调异常完成
      rpcClient.handleException(requestId,
          new RuntimeException(msg.getErrorCode() + ": " + msg.getErrorMessage()));
    } else if (msg.isRequest()) {
      // 可能是对端反向调用（双工 RPC），忽略或处理
      log.debug("[Netty-RPC] 收到对端请求（双工 RPC），暂不处理: requestId={}", requestId);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    log.error("[Netty-RPC] 通道异常，关闭连接", cause);
    rpcClient.disconnect();
  }
}
