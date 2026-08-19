package com.njydsz.agent.server.chat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.njydsz.agent.domain.model.ChatChunk;

/**
 * SSE 流式执行器（统一封装心跳保活、虚拟线程、断连检测、cleanup 逻辑）
 *
 * <p>消除 {@link com.njydsz.agent.web.controller.ChatController} 与
 * {@link com.njydsz.agent.web.controller.AgentController} 中重复的
 * 心跳调度、虚拟线程启动、客户端断连检测、超时 cleanup 等样板代码。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * SseExecutor executor = new SseExecutor(emitter, 15);
 * executor.execute(chunk -> {
 *     // 业务逻辑：调用 LLM 流式接口，chunk 为每个流式片段
 *     llmClient.stream(request, chunkConsumer);
 * });
 * }</pre>
 *
 * <p><b>线程安全</b>：本类为单次请求实例，不跨请求共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SseExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(SseExecutor.class);

  /** SSE 超时时间（毫秒）：2 分钟，避免长连接占用 Web 容器资源 */
  private static final long SSE_TIMEOUT = 120_000L;

  /** 心跳间隔（秒）：15 秒，防止中间代理（Nginx/CDN）静默断开空闲连接 */
  private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

  /** 心跳调度线程数：2 个足够覆盖常规 SSE 连接 */
  private static final int HEARTBEAT_POOL_SIZE = 2;

  private final SseEmitter emitter;
  private final long heartbeatIntervalSeconds;
  private final ScheduledExecutorService heartbeatScheduler;
  private final AtomicBoolean active;

  /**
   * 创建 SSE 执行器。
   *
   * @param emitter Spring MVC SSE 发送句柄
   */
  public SseExecutor(SseEmitter emitter) {
    this(emitter, HEARTBEAT_INTERVAL_SECONDS);
  }

  /**
   * 创建 SSE 执行器（自定义心跳间隔）。
   *
   * @param emitter Spring MVC SSE 发送句柄
   * @param heartbeatIntervalSeconds 心跳间隔（秒）
   */
  public SseExecutor(SseEmitter emitter, long heartbeatIntervalSeconds) {
    this.emitter = emitter;
    this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    this.active = new AtomicBoolean(true);
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 短生命周期临时池，cleanup 中 shutdown
    this.heartbeatScheduler =
        new ScheduledThreadPoolExecutor(
            HEARTBEAT_POOL_SIZE,
            Thread.ofVirtual().name("agent-sse-heartbeat-", 0).factory(),
            new ThreadPoolExecutor.CallerRunsPolicy());
    // CHECKSTYLE.ON: RegexpSinglelineJava
  }

  /**
   * 执行流式任务。
   *
   * <p>启动心跳线程 → 在虚拟线程中执行业务回调 → 完成后自动 cleanup。 业务回调中通过 {@code chunkConsumer} 推送增量数据。
   *
   * @param task 业务回调，入参为 chunk 消费者
   */
  public void execute(Consumer<Consumer<SseChunk>> task) {
    var heartbeatFuture =
        heartbeatScheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            heartbeatIntervalSeconds,
            heartbeatIntervalSeconds,
            TimeUnit.SECONDS);

    Thread virtualThread = Thread.startVirtualThread(() -> doExecute(task, heartbeatFuture));
    virtualThread.setName("agent-sse-execute-" + virtualThread.threadId());

    Runnable cleanup = () -> cleanup(heartbeatFuture, virtualThread);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    emitter.onCompletion(cleanup);
  }

  /** 执行流式任务核心逻辑 */
  private void doExecute(Consumer<Consumer<SseChunk>> task, ScheduledFuture<?> heartbeatFuture) {
    try {
      task.accept(chunk -> sendChunk(chunk));
      if (active.get()) {
        sendDone();
        emitter.complete();
      }
    } catch (Exception e) {
      LOG.error("[SseExecutor] 流式执行异常: {}", e.getMessage(), e);
      if (active.get()) {
        sendError(e);
        emitter.completeWithError(e);
      }
    } finally {
      // 确保 cleanup 被执行（即使 onCompletion 回调未触发）
      cleanup(heartbeatFuture, Thread.currentThread());
    }
  }

  /** 发送心跳注释帧 */
  private void sendHeartbeat() {
    if (!active.get()) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().comment("keep-alive"));
    } catch (IOException e) {
      active.set(false);
      LOG.debug("[SseExecutor] 心跳发送失败，标记连接断开: {}", e.getMessage());
    }
  }

  /** 推送增量 chunk */
  private void sendChunk(SseChunk chunk) {
    if (!active.get()) {
      throw new RuntimeException("SSE 连接已断开，终止 LLM 调用");
    }
    try {
      emitter.send(
          SseEmitter.event()
              .data(chunk.toMap())
              .name("chunk"));
    } catch (IOException e) {
      active.set(false);
      LOG.warn("[SseExecutor] SSE chunk 发送失败，标记连接断开: {}", e.getMessage());
      throw new RuntimeException("SSE 连接已断开", e);
    }
  }

  /** 推送完成事件 */
  private void sendDone() {
    if (!active.get()) {
      return;
    }
    try {
      emitter.send(SseEmitter.event().data(Map.of("content", "", "finished", true)).name("done"));
    } catch (IOException e) {
      LOG.warn("[SseExecutor] SSE done 发送失败: {}", e.getMessage());
    }
  }

  /** 推送错误事件 */
  private void sendError(Exception e) {
    if (!active.get()) {
      return;
    }
    try {
      emitter.send(
          SseEmitter.event()
              .data(
                  Map.of(
                      "error",
                      e.getMessage() != null ? e.getMessage() : "未知错误",
                      "finished",
                      true))
              .name("error"));
    } catch (IOException ex) {
      // 客户端已断开，忽略（P1 修复：catch 变量与方法参数 e 重名，编译冲突）
      LOG.debug("[SseExecutor] 错误事件发送失败（客户端已断开）: {}", ex.getMessage());
    }
  }

  /** 清理资源：停止心跳 + 中断执行线程 */
  private void cleanup(ScheduledFuture<?> heartbeatFuture, Thread executionThread) {
    active.set(false);
    heartbeatFuture.cancel(true);
    if (executionThread != null && executionThread.isAlive() && !executionThread.equals(Thread.currentThread())) {
      executionThread.interrupt();
    }
    if (!heartbeatScheduler.isShutdown()) {
      heartbeatScheduler.shutdownNow();
    }
  }

  /**
   * SSE 传输的 chunk 值对象（不可变 record）。
   *
   * <p>封装流式片段数据，统一 ChatController 和 AgentController 的数据格式。
   *
   * @param content 增量文本内容
   * @param finished 是否已完成
   * @param finishReason 结束原因（stop / length / tool_calls）
   * @param toolCalls 工具调用列表
   */
  public record SseChunk(String content, boolean finished, String finishReason, Object toolCalls) {

    /** 创建增量内容 chunk */
    public static SseChunk content(String content) {
      return new SseChunk(content, false, null, null);
    }

    /** 创建带完成标记的 chunk */
    public static SseChunk content(String content, String finishReason, Object toolCalls) {
      return new SseChunk(content, false, finishReason, toolCalls);
    }

    /** 创建完成 chunk */
    public static SseChunk finish(String finishReason) {
      return new SseChunk(null, true, finishReason, null);
    }

    /** 转换为 Map（用于 SseEmitter.event().data()） */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("content", content != null ? content : "");
      map.put("finished", finished);
      if (finishReason != null) {
        map.put("finishReason", finishReason);
      }
      if (toolCalls != null) {
        map.put("toolCalls", toolCalls);
      }
      return map;
    }
  }
}
