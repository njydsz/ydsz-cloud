package com.njydsz.common.socket.push;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.common.json.YdszJson;

/**
 * SSE 通道的 Spring MVC 实现（基于 SseEmitter）。
 *
 * <p>封装 SseEmitter 的低级 API，提供：
 *
 * <ul>
 *   <li>YDSZ 标准 SSE 事件序列化（UTF-8 text/event-stream；Jackson 序列化 payload）
 *   <li>内置心跳保活（通过 {@link ScheduledExecutorService} 调度 comment frame）
 *   <li>断连检测与自动 cleanup（complete 后停止心跳）
 *   <li>会话隔离（通过 sessionId 标识连接）
 * </ul>
 *
 * <p>非线程安全：单次请求实例（ Scoped Bean 或 Factory 方法局部变量），不跨请求共享。
 *
 * @author ydsz-team
 * @since 26.09.24
 */
public class SsePushChannelMvcAdapter implements SsePushChannel {

  /** SSE 默认超时：120 秒 */
  private static final long DEFAULT_TIMEOUT = 120_000L;

  /** 心跳间隔：15 秒 */
  private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

  private final SseEmitter emitter;
  private final String sessionId;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final ScheduledExecutorService heartbeatScheduler;
  private final long heartbeatIntervalSeconds;
  private volatile ScheduledFuture<?> heartbeatFuture;

  /**
   * 创建 MVC SSE 通道适配器。
   *
   * @param sessionId 会话标识（用于日志/trace）
   * @param heartbeatScheduler 心跳调度共享线程池
   */
  public SsePushChannelMvcAdapter(String sessionId, ScheduledExecutorService heartbeatScheduler) {
    this(sessionId, DEFAULT_TIMEOUT, heartbeatScheduler, HEARTBEAT_INTERVAL_SECONDS);
  }

  /**
   * 创建 MVC SSE 通道适配器（全参数）。
   *
   * @param sessionId 会话标识
   * @param timeoutMillis 超时毫秒
   * @param heartbeatScheduler 心跳调度器
   * @param heartbeatIntervalSeconds 心跳间隔
   */
  public SsePushChannelMvcAdapter(String sessionId, long timeoutMillis,
      ScheduledExecutorService heartbeatScheduler, long heartbeatIntervalSeconds) {
    this.sessionId = sessionId;
    this.emitter = new SseEmitter(timeoutMillis);
    this.heartbeatScheduler = heartbeatScheduler;
    this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    registerCallbacks();
    startHeartbeat();
  }

  @Override
  public void sendEvent(String eventName, Object payload) throws IOException {
    if (closed.get()) {
      return;
    }
    try {
      String json = YdszJson.toJson(payload);
      emitter.send(SseEmitter.event().name(eventName).data(json, MediaType.APPLICATION_JSON));
    } catch (IllegalStateException e) {
      // SseEmitter 已关闭（客户端断连）— 静默标记并跳过
      closed.set(true);
      throw new IOException("SSE 通道已关闭（可能客户端断连）: sessionId=" + sessionId, e);
    } catch (IOException e) {
      closed.set(true);
      throw e;
    }
  }

  @Override
  public void sendHeartbeat() throws IOException {
    if (closed.get()) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().comment("keepalive"));
    } catch (IOException e) {
      closed.set(true);
      throw e;
    }
  }

  @Override
  public void complete() {
    if (closed.compareAndSet(false, true)) {
      stopHeartbeat();
      try {
        emitter.complete();
      } catch (IllegalStateException e) {
        // 已完成或被客户端关闭，静默忽略
      }
    }
  }

  @Override
  public void completeWithError(Throwable error) {
    if (closed.compareAndSet(false, true)) {
      stopHeartbeat();
      try {
        emitter.completeWithError(error);
      } catch (IllegalStateException e) {
        // 已完成或被客户端关闭，静默忽略
      }
    }
  }

  @Override
  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public String getSessionId() {
    return sessionId;
  }

  /**
   * 返回底层 SseEmitter（供 Controller 直接返回）。
   *
   * @return SseEmitter 实例
   */
  public SseEmitter getEmitter() {
    return emitter;
  }

  // ---------------------------------------------------------------------------
  // 内部方法
  // ---------------------------------------------------------------------------

  private void registerCallbacks() {
    emitter.onCompletion(() -> {
      closed.set(true);
      stopHeartbeat();
    });
    emitter.onTimeout(() -> {
      closed.set(true);
      stopHeartbeat();
    });
    emitter.onError(t -> {
      closed.set(true);
      stopHeartbeat();
    });
  }

  private void startHeartbeat() {
    if (heartbeatScheduler != null) {
      heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
        try {
          sendHeartbeat();
        } catch (RuntimeException e) {
          stopHeartbeat();
        }
      }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }
  }

  private void stopHeartbeat() {
    if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
      heartbeatFuture.cancel(false);
    }
  }
}
