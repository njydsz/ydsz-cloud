package com.njydsz.common.socket.push;

import java.io.IOException;

/**
 * SSE 推送通道接口（WebFlux / Spring MVC SSE 抽象层）。
 *
 * <p>封装 SSE 连接的完整生命周期：创建、发送事件、心跳保活、异常处理、资源清理。
 * 业务模块通过请求域 Factory 获取通道实例，无需关心底层 SseEmitter/CloseableHttpOutputMessage 差异。
 *
 * <p><b>设计定位</b>：
 *
 * <ul>
 *   <li>request-scoped：一个 SSE 连接对应一个通道实例（与 WebSocket session-scoped 本质不同）
 *   <li>多态适配：Spring MVC {@code SseEmitter} 与 WebFlux {@code RawHttpOutputMessage} 各有实现
 *   <li>心跳内置：通道内置可配置心跳调度器，业务方仅需关注事件发送
 *   <li>优雅关闭：客户端断连或服务端主动关闭时自动清理心跳资源
 * </ul>
 *
 * <p><b>使用约定</b>：
 *
 * <ul>
 *   <li>业务 Controller 通过 {@code SsePushChannelFactory} 创建通道
 *   <li>业务逻辑通过 {@link #sendEvent(String, Object)} 发送业务事件
 *   <li>异常场景调用 {@link #completeWithError(Throwable)} 触发错误回写
 *   <li>正常结束时调用 {@link #complete()} 发送 SSE [DONE] 标记
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.24
 * @see SsePushChannelFactory 通道工厂
 */
public interface SsePushChannel {

  /**
   * 发送 SSE 业务事件。
   *
   * <p>事件自动包装为 SSE data frame + 可选 event name，序列化方式由实现决定
   * （MVC 实现默认使用 UTF-8 text/event-stream）。
   *
   * @param eventName SSE event 名称（如 "delta"/"tool_call"/"citation"）
   * @param payload 事件负载（任意可序列化对象）
   * @throws IOException 底层通道已关闭或写入失败时抛出
   */
  void sendEvent(String eventName, Object payload) throws IOException;

  /**
   * 发送 SSE 评论帧（:keepalive）：纯心跳数据，用于保持连接不被代理断开。
   *
   * <p>通常不需要业务方直接调用——通道内置自动心跳调度。仅在自定义心跳间隔场景覆盖。
   *
   * @throws IOException 底层通道已关闭或写入失败时抛出
   */
  void sendHeartbeat() throws IOException;

  /**
   * 正常完成通道：发送 [DONE] 标记并关闭底层连接。
   *
   * <p>幂等调用：重复调用 complete() 不产生副作用。
   */
  void complete();

  /**
   * 异常完成通道：发送错误事件并关闭连接。
   *
   * <p>错误事件格式：{@code event: error
   * data: {"code":"xxx","message":"yyy"}
   * }。幂等调用。
   *
   * @param error 业务异常（非 null）
   */
  void completeWithError(Throwable error);

  /**
   * 返回通道是否已关闭（心跳异常/客户端断连/主动 complete）。
   *
   * @return true = 已关闭，不再接受 sendEvent 调用
   */
  boolean isClosed();

  /**
   * 返回当前通道的会话标识（用于日志 trace / 并发冲突检测）。
   *
   * @return 会话 ID（非 null，通常来自请求指纹）
   */
  String getSessionId();
}
