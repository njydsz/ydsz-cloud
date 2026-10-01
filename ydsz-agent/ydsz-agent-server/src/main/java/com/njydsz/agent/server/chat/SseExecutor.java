package com.njydsz.agent.server.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.socket.push.SsePushChannel;

/**
 * SSE 流式执行器（统一封装心跳保活、虚拟线程、断连检测、cleanup 逻辑）。
 *
 * <p>使用 {@link SsePushChannel} 作为底层 SSE 传输抽象，心跳保活由 {@code ydsz-common-socket}
 * 的 {@code SsePushChannelMvcAdapter} 内置调度（共享调度器，无需独立心跳线程）。
 *
 * <p>消除 {@link com.njydsz.agent.web.controller.ChatController} 与
 * {@link com.njydsz.agent.web.controller.AgentController} 中重复的
 * 心跳调度、虚拟线程启动、客户端断连检测、超时 cleanup 等样板代码。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * SsePushChannel channel = ssePushChannelFactory.create();
 * SseExecutor executor = new SseExecutor(channel);
 * executor.execute(chunk -> {
 *     // 业务逻辑：调用 LLM 流式接口，chunk 为每个流式片段
 *     llmClient.stream(request, chunkConsumer);
 * });
 * }</pre>
 *
 * <p><b>线程安全</b>：本类为单次请求实例，不跨请求共享。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
public class SseExecutor {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** SSE 通道（统一生命周期：心跳保活 + cleanup） */
  private final SsePushChannel channel;
  private final AtomicBoolean active;

  /**
   * 创建 SSE 执行器。
   *
   * <p>心跳间隔由 SsePushChannel 内部默认处理（15 秒）；如需自定义，可使用
   * {@link com.njydsz.common.socket.push.SsePushChannelFactory#create(long)} 创建通道时指定超时。
   *
   * @param channel SSE 通道（由 SsePushChannelMvcFactory 创建）
   */
  public SseExecutor(SsePushChannel channel) {
    this.channel = channel;
    this.active = new AtomicBoolean(true);
  }

  /**
   * 获取底层 SSE 通道（供 Controller 直接发送非 chunk 事件，如 progress）。
   *
   * @return SsePushChannel
   */
  public SsePushChannel getChannel() {
    return channel;
  }

  /**
   * 执行流式任务。
   *
   * <p>在虚拟线程中执行业务回调，完成后自动 cleanup（complete / completeWithError）。
   * 业务回调中通过 {@code chunkConsumer} 推送增量数据。
   *
   * @param task 业务回调，入参为 chunk 消费者
   */
  public void execute(Consumer<Consumer<SseChunk>> task) {
    Thread virtualThread = Thread.startVirtualThread(() -> doExecute(task));
    virtualThread.setName("agent-sse-execute-" + virtualThread.threadId());
  }

  /** 执行流式任务核心逻辑 */
  private void doExecute(Consumer<Consumer<SseChunk>> task) {
    try {
      task.accept(this::sendChunk);
      if (active.get()) {
        sendDone();
        channel.complete();
      }
    } catch (Exception e) {
      log.error("[SseExecutor] 流式执行异常", e);
      if (active.get()) {
        sendError(e);
        channel.completeWithError(e);
      }
    }
  }

  /** 推送增量 chunk */
  private void sendChunk(SseChunk chunk) {
    if (!active.get()) {
      throw new IllegalStateException(I18n.message("agent.error.chat.sse_disconnected"));
    }
    try {
      channel.sendEvent("chunk", chunk.toMap());
    } catch (Exception e) {
      active.set(false);
      log.warn("[SseExecutor] SSE chunk 发送失败，标记连接断开", e);
      throw new IllegalStateException(I18n.message("agent.error.chat.sse_disconnected"), e);
    }
  }

  /** 推送完成事件 */
  private void sendDone() {
    if (!active.get()) {
      return;
    }
    try {
      Map<String, Object> data = new LinkedHashMap<>(COLLECTION_CAPACITY);
      data.put("content", "");
      data.put("finished", true);
      channel.sendEvent("done", data);
    } catch (Exception e) {
      log.warn("[SseExecutor] SSE done 发送失败", e);
    }
  }

  /** 推送错误事件 */
  private void sendError(Exception e) {
    if (!active.get()) {
      return;
    }
    try {
      Map<String, Object> data = new LinkedHashMap<>(COLLECTION_CAPACITY);
      data.put("error", e.getMessage() != null ? e.getMessage() : "未知错误");
      data.put("finished", true);
      channel.sendEvent("error", data);
    } catch (Exception ex) {
      // 客户端已断开，忽略
      log.debug("[SseExecutor] 错误事件发送失败（客户端已断开）", ex);
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

    /**
     * 创建增量内容 chunk。
     *
     * @param content 增量文本内容
     * @return SSE 分块
     */
    public static SseChunk content(String content) {
      return new SseChunk(content, false, null, null);
    }

    /**
     * 创建带完成标记的 chunk。
     *
     * @param content 增量文本内容
     * @param finishReason 结束原因
     * @param toolCalls 工具调用列表
     * @return SSE 分块
     */
    public static SseChunk content(String content, String finishReason, Object toolCalls) {
      return new SseChunk(content, false, finishReason, toolCalls);
    }

    /**
     * 创建完成 chunk。
     *
     * @param finishReason 结束原因
     * @return SSE 分块
     */
    public static SseChunk finish(String finishReason) {
      return new SseChunk(null, true, finishReason, null);
    }

    /**
     * 转换为 Map（用于 SsePushChannel.sendEvent()）。
     *
     * @return 事件数据 Map
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>(COLLECTION_CAPACITY);
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
