package com.njydsz.common.socket.push;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * SSE 通道工厂的 Spring MVC 实现（创建 {@link SsePushChannelMvcAdapter} 实例）。
 *
 * <p>自动管理心跳共享调度器（daemon 线程，corePoolSize=2）。业务模块注入此 Factory
 * 后每次 HTTP SSE 请求调用 {@link #create()} 创建新通道。
 *
 * <p><b>连接限流</b>：工厂级全局连接计数 + 配置上限校验，超限快速失败返回
 * {@link SseLimitExceededChannel}。连接在通道关闭时自动递减计数。
 *
 * <p><b>装配条件</b>：classpath 中存在 {@code SseEmitter}（即 spring-webmvc），
 * 通过 {@code @ConditionalOnClass(SseEmitter.class)} 控制自动配置。
 *
 * @author ydsz-team
 * @since 26.09.24
 */
@Slf4j
public class SsePushChannelMvcFactory implements SsePushChannelFactory, DisposableBean {

  /** SSE 默认超时（毫秒）：120 秒 */
  private static final long DEFAULT_TIMEOUT_MS = 120_000L;

  /** 心跳共享调度器（P1-1: 使用 InternalExecutorFactory 统一管理，纳入 ThreadPoolRegistry 监控） */
  private final ScheduledExecutorService heartbeatScheduler =
      InternalExecutorFactory.newScheduledThreadPool("sse-heartbeat", 2);

  /** SSE 配置属性（连接限流参数） */
  private final WebSocketProperties properties;

  /** 当前活跃 SSE 连接数（全工厂共享） */
  private final AtomicInteger activeConnections = new AtomicInteger(0);

  /**
   * 创建 SSE 通道工厂。
   *
   * @param properties WebSocket 配置属性（含 SSE 连接限流参数）
   */
  public SsePushChannelMvcFactory(WebSocketProperties properties) {
    this.properties = properties;
  }

  @Override
  public SsePushChannel create() {
    return create(DEFAULT_TIMEOUT_MS);
  }

  @Override
  public SsePushChannel create(long timeoutMillis) {
    // 连接数限流校验
    int maxTotal = properties.getSse() != null
        ? properties.getSse().getMaxTotalConnections()
        : 500;
    if (activeConnections.get() >= maxTotal) {
      log.warn("[SSE-Factory] 全局连接数超限,拒绝创建: active={}, max={}",
          activeConnections.get(), maxTotal);
      return new SseLimitExceededChannel();
    }
    SsePushChannelMvcAdapter channel =
        new SsePushChannelMvcAdapter(generateSessionId(), timeoutMillis, heartbeatScheduler, 15L);
    activeConnections.incrementAndGet();
    // 连接关闭时递减计数
    decrementOnClose(channel);
    return channel;
  }

  @Override
  public void destroy() {
    heartbeatScheduler.shutdown();
    try {
      if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        heartbeatScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      heartbeatScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** 注册通道关闭回调（用于递减连接计数） */
  private void decrementOnClose(SsePushChannelMvcAdapter channel) {
    SseEmitter emitter = channel.getEmitter();
    emitter.onCompletion(() -> activeConnections.decrementAndGet());
    emitter.onTimeout(() -> activeConnections.decrementAndGet());
    emitter.onError(e -> activeConnections.decrementAndGet());
  }

  /**
   * 获取当前活跃 SSE 连接数（供监控）。
   *
   * @return 活跃连接数
   */
  public int getActiveConnections() {
    return activeConnections.get();
  }

  private String generateSessionId() {
    return "sse-" + UUID.randomUUID().toString().substring(0, 12);
  }
}
