package com.njydsz.common.socket.graceful;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.njydsz.common.socket.push.RealtimePushTemplate;
import com.njydsz.common.socket.session.LocalSessionRegistry;

/**
 * WebSocket 优雅停机处理器（ARCH-003）。
 *
 * <p>实现 {@link SmartLifecycle} 接口，在 Spring 容器停机时自动触发排空流程：
 *
 * <ol>
 *   <li>准入终止：拒绝新握手（{@link #isAcceptingNewConnections()=false}）
 *   <li>异步投递排空：调用 {@link RealtimePushTemplate#flushRetryMessages()} 强制投净重试队列
 *   <li>等待 in-flight：最多等待 {@code drainTimeoutMs}，让已发布消息在集群内流转
 *   <li>关闭现存会话：遍历 {@link LocalSessionRegistry#getAllSessions()} 逐条断开
 * </ol>
 *
 * <p>所有子阶段的异常均只记录 warn 日志并继续，避免因部分失败阻塞整个停机流程。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class WebSocketGracefulShutdown implements SmartLifecycle {

  /** 默认最大排空等待时间：30 秒 */
  private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(30);

  /** 默认重试队列排空最大周期数 */
  private static final int DEFAULT_DRAIN_CYCLES = 10;

  /** 每个周期的基础休眠时间（毫秒） */
  private static final long DRAIN_CYCLE_SLEEP_MS = 200L;

  private final AtomicBoolean isRunning = new AtomicBoolean(false);

  private final RealtimePushTemplate pushTemplate;
  private final LocalSessionRegistry sessionRegistry;

  /** 是否接受新连接（停机时置 false 以拒绝新握手） */
  private volatile boolean isAcceptingNewConnections = true;

  /** 排空等待最大时长 */
  private final Duration drainTimeout;

  /**
   * 构造优雅停机处理器。
   *
   * @param pushTemplate 推送模板（用于强制排空重试队列）
   * @param session注册表 本地会话注册表（用于关闭现存连接）
   */
  public WebSocketGracefulShutdown(
      RealtimePushTemplate pushTemplate, LocalSessionRegistry sessionRegistry) {
    this(pushTemplate, sessionRegistry, DEFAULT_DRAIN_TIMEOUT);
  }

  /**
   * 构造优雅停机处理器（自定义排空等待时间）。
   *
   * @param pushTemplate 推送模板
   * @param session注册表 本地会话注册表
   * @param drainTimeout 排空最大等待时长
   */
  public WebSocketGracefulShutdown(
      RealtimePushTemplate pushTemplate,
      LocalSessionRegistry sessionRegistry,
      Duration drainTimeout) {
    this.pushTemplate = pushTemplate;
    this.sessionRegistry = sessionRegistry;
    this.drainTimeout = drainTimeout != null ? drainTimeout : DEFAULT_DRAIN_TIMEOUT;
  }

  /**
   * 准入检查：用于 {@code WebSocketAuthInterceptor.beforeHandshake()} 拒绝新握手。
   *
   * @return true 表示当前允许新连接
   */
  public boolean isAcceptingNewConnections() {
    return isAcceptingNewConnections;
  }

  // ==================== SmartLifecycle ====================

  /** 启动：设置运行状态并允许新连接。 */
  @Override
  public void start() {
    isRunning.set(true);
    isAcceptingNewConnections = true;
    log.info("[WS-GracefulShutdown] 启动，允许新 WebSocket 连接");
  }

  /**
   * 停机流程：排空队列 → 等待 in-flight → 关闭现存会话。
   *
   * <p>Spring 容器 stop() 时自动调用，该方法阻塞直到排空流程结束或超时。
   */
  @Override
  public void stop() {
    if (!isRunning.compareAndSet(true, false)) {
      log.info("[WS-GracefulShutdown] 已处于停机状态，跳过重复停机流程");
      return;
    }
    log.info("[WS-GracefulShutdown] 开始优雅停机...");
    try {
      // 第 1 步：停止接受新连接
      isAcceptingNewConnections = false;
      // 第 2 步：排空重试队列
      drainRetryQueue();
      // 第 3 步：等待 in-flight 消息在集群内流转
      waitForInFlightDelivery();
      // 第 4 步：关闭现存会话
      closeExistingSessions();
    } catch (Exception e) {
      log.warn("[WS-GracefulShutdown] 优雅停机流程异常: err={}", e.getMessage());
    }
    log.info("[WS-GracefulShutdown] 优雅停机完成");
  }

  /** Spring 7+ 版本的 stop(Runnable callback)：停机完成后回调通知容器。 */
  @Override
  public void stop(Runnable callback) {
    try {
      stop();
    } finally {
      if (callback != null) {
        callback.run();
      }
    }
  }

  /**
   * 是否正在运行。
   *
   * @return true 表示当前运行中
   */
  @Override
  public boolean isRunning() {
    return isRunning.get();
  }

  /**
   * 单例 bean 自动启动。
   *
   * @return true 表示自动启动
   */
  @Override
  public boolean isAutoStartup() {
    return true;
  }

  /**
   * 阶段值：使用较高整数值以在大多数业务 bean 之后停止。
   *
   * <p>SmartLifecycle bean 按 phase 降序停止；设置较大值可确保推送服务仍可用期间先停止新握手准入，
   * 然后最终再下线推送模板、在线用户服务等依赖组件。
   *
   * @return 阶段值 (Integer.MAX_VALUE - 1000)
   */
  @Override
  public int getPhase() {
    return Integer.MAX_VALUE - 1000;
  }

  // ==================== 子流程 ====================

  /**
   * 排空重试队列。
   *
   * <p>同步调用 flushRetryMessages() 多个周期，直到排空或超时；每个周期之间
   * 短暂停顿让集群 Pub/Sub 有足够时间在节点间流转未完成的消息。
   */
  private void drainRetryQueue() {
    log.info("[WS-GracefulShutdown] 开始排空推送重试队列...");
    if (pushTemplate == null) {
      return;
    }
    try {
      for (int i = 0; i < DEFAULT_DRAIN_CYCLES; i++) {
        pushTemplate.flushRetryMessages();
        Thread.sleep(DRAIN_CYCLE_SLEEP_MS);
      }
      log.info("[WS-GracefulShutdown] 重试队列排空完成");
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      log.warn("[WS-GracefulShutdown] 重试队列排空被中断");
    } catch (Exception e) {
      log.warn("[WS-GracefulShutdown] 重试队列排空异常: err={}", e.getMessage());
    }
  }

  /**
   * 等待 in-flight 消息。
   *
   * <p>给集群 Pub/Sub 足够时间在节点间流转未确认的消息；超时后强制继续停机流程。
   */
  private void waitForInFlightDelivery() {
    long waitMs = Math.min(drainTimeout.toMillis() / 3, 5000L);
    if (waitMs <= 0) {
      return;
    }
    log.info("[WS-GracefulShutdown] 等待 in-flight 投递, waitMs={}", waitMs);
    try {
      Thread.sleep(waitMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      log.warn("[WS-GracefulShutdown] in-flight 等待被中断");
    }
  }

  /**
   * 关闭本地 SessionRegistry 中的所有现存 WebSocket 会话。
   *
   * <p>使用 {@link CloseStatus#SERVER_SHUTDOWN} 通知客户端服务端正在下线，
   * 客户端可据此触发服务端主动断连的重连退避策略（避免瞬时重连风暴）。
   */
  private void closeExistingSessions() {
    if (sessionRegistry == null) {
      return;
    }
    log.info("[WS-GracefulShutdown] 开始关闭现存 WebSocket 会话...");
    int count = 0;
    try {
      Map<String, WebSocketSession> allSessions = sessionRegistry.getAllSessions();
      for (Map.Entry<String, WebSocketSession> entry : allSessions.entrySet()) {
        String sessionId = entry.getKey();
        WebSocketSession session = entry.getValue();
        if (session != null && session.isOpen()) {
          try {
            session.close(new CloseStatus(1001, "Server shutdown"));
            count++;
          } catch (Exception e) {
            log.warn(
                "[WS-GracefulShutdown] 关闭 session 失败: sessionId={}, err={}",
                sessionId,
                e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      log.warn("[WS-GracefulShutdown] 关闭现存会话异常: err={}", e.getMessage());
    }
    log.info("[WS-GracefulShutdown] 现存会话关闭完成, count={}", count);
  }
}
