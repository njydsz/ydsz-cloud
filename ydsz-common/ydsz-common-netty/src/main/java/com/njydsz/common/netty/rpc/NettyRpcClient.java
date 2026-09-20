package com.njydsz.common.netty.rpc;

import com.njydsz.common.netty.client.AbstractNettyClient;
import com.njydsz.common.netty.config.NettyProperties;
import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支持 Request-Response RPC 语义的 Netty Client。
 *
 * <p>内置请求-响应匹配机制：
 *
 * <ul>
 *   <li>发送请求时生成唯一 requestId，将 {@link CompletableFuture} 注册到 pending 表
 *   <li>收到响应时按 requestId 查表并 complete 对应的 Future
 *   <li>超时未响应的 Future 自动异常完成并清理
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * NettyRpcClient client = new NettyRpcClient("127.0.0.1", 8080, properties);
 * client.connect();
 * CompletableFuture&lt;Response&gt; future = client.invoke(new LoginRequest("user", "pass"), 5000, Response.class);
 * Response response = future.get(); // 阻塞等待响应，或链式组合
 * }</pre>
 *
 * <p><b>线程安全：</b>invoke() 方法可被多线程同时调用，内部 pending 表使用 {@link ConcurrentHashMap} 保证线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RpcMessage
 */
@Slf4j
public abstract class NettyRpcClient extends AbstractNettyClient {

  /** 请求 ID 生成器 */
  private final AtomicLong requestIdGenerator = new AtomicLong(0);

  /** 等待中的请求：requestId → CompletableFuture */
  private final ConcurrentHashMap<Long, CompletableFuture<Object>> pendingRequests = new ConcurrentHashMap<>();

  /** 默认 RPC 超时（毫秒） */
  private final long defaultTimeoutMs;

  /**
   * 构造 Netty RPC Client（使用默认超时 10 秒）。
   *
   * @param host 目标主机
   * @param port 目标端口
   * @param properties Netty 配置
   */
  protected NettyRpcClient(String host, int port, NettyProperties properties) {
    this(host, port, properties, 10_000L);
  }

  /**
   * 构造 Netty RPC Client。
   *
   * @param host 目标主机
   * @param port 目标端口
   * @param properties Netty 配置
   * @param defaultTimeoutMs 默认 RPC 超时（毫秒）
   */
  protected NettyRpcClient(String host, int port, NettyProperties properties, long defaultTimeoutMs) {
    super(host, port, properties);
    this.defaultTimeoutMs = defaultTimeoutMs;
  }

  @Override
  protected final void initChannelPipeline(SocketChannel ch) {
    // 子类通过 initRpcPipeline 添加编解码器和业务 Handler
    initRpcPipeline(ch);
  }

  /**
   * 子类实现：初始化 RPC Pipeline（添加编解码器和响应 Handler）。
   *
   * <p>注意：子类也必须将 {@link RpcResponseHandler} 添加到 Pipeline， 并传入 {@code pendingRequests} 表用于响应匹配。
   *
   * @param ch SocketChannel
   */
  protected abstract void initRpcPipeline(SocketChannel ch);

  /**
   * 发起 RPC 调用，返回 CompletableFuture（使用默认超时）。
   *
   * @param request 请求对象
   * @param responseType 响应类型
   * @param <T> 响应类型泛型
   * @return CompletableFuture，响应成功完成，超时或失败时异常完成
   */
  public <T> CompletableFuture<T> invoke(Object request, Class<T> responseType) {
    return invoke(request, defaultTimeoutMs, responseType);
  }

  /**
   * 发起 RPC 调用，返回 CompletableFuture（自定义超时）。
   *
   * <p>注意：此方法仅保证写入 TCP 栈成功，响应匹配依赖对端正确返回带 requestId 的消息。
   *
   * @param request 请求对象
   * @param timeoutMs 超时毫秒数
   * @param responseType 响应类型
   * @param <T> 响应类型泛型
   * @return CompletableFuture，响应成功完成，超时或失败时异常完成
   */
  public <T> CompletableFuture<T> invoke(Object request, long timeoutMs, Class<T> responseType) {
    long requestId = generateRequestId();
    CompletableFuture<Object> future = new CompletableFuture<>();
    pendingRequests.put(requestId, future);

    // 超时自动完成失败并清理
    future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .whenComplete((result, error) -> pendingRequests.remove(requestId));

    // 发送带 requestId 的请求消息
    RpcMessage rpcMsg = RpcMessage.request(requestId, request);
    sendAsync(rpcMsg).exceptionally(e -> {
      pendingRequests.remove(requestId);
      future.completeExceptionally(e);
      return null;
    });

    log.debug("[Netty-RPC] 发起请求: requestId={}, type={}", requestId, request.getClass().getSimpleName());
    return future.thenApply(responseType::cast);
  }

  /**
   * 处理 RPC 响应消息。
   *
   * <p>此方法由 {@link RpcResponseHandler} 在收到匹配的响应时调用。
   *
   * @param requestId 请求 ID
   * @param response 响应对象
   */
  void handleResponse(long requestId, Object response) {
    CompletableFuture<Object> future = pendingRequests.remove(requestId);
    if (future != null) {
      future.complete(response);
      log.debug("[Netty-RPC] 收到响应: requestId={}, type={}", requestId, response.getClass().getSimpleName());
    } else {
      log.warn("[Netty-RPC] 收到过期的响应: requestId={}", requestId);
    }
  }

  /**
   * 处理 RPC 异常响应。
   *
   * @param requestId 请求 ID
   * @param exception 异常对象
   */
  void handleException(long requestId, Throwable exception) {
    CompletableFuture<Object> future = pendingRequests.remove(requestId);
    if (future != null) {
      future.completeExceptionally(exception);
      log.debug("[Netty-RPC] 响应异常: requestId={}", requestId, exception);
    }
  }

  /**
   * 生成分布式唯一请求 ID（自增序列）。
   *
   * @return 请求 ID
   */
  long generateRequestId() {
    return requestIdGenerator.incrementAndGet();
  }

  @Override
  public void disconnect() {
    // 断开连接时：立即失败所有 pending 请求
    pendingRequests.forEach((id, future) ->
        future.completeExceptionally(new TimeoutException("连接已断开，请求未完成: requestId=" + id)));
    pendingRequests.clear();
    super.disconnect();
  }

  /**
   * 获取等待中的请求数。
   *
   * @return pending 请求数
   */
  public int getPendingRequestCount() {
    return pendingRequests.size();
  }
}
