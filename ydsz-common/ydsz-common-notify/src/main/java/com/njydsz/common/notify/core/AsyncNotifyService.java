package com.njydsz.common.notify.core;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.notify.enums.NotifyChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 异步通知发送服务
 *
 * <p>使用共享的虚拟线程池异步发送通知消息，避免大附件等场景阻塞 HTTP 线程。 发送失败自动进入重试队列，由定时任务按指数退避重试。
 *
 * <p>支持 {@link NotifyTraceContext} traceId 上下文传播，确保异步线程中链路追踪不丢失。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AsyncNotifyService {

  private static final Logger log = LoggerFactory.getLogger(AsyncNotifyService.class);

  private final ExecutorService executor;
  private final NotifyService notifyService;
  private final NotifyRetryQueue retryQueue;

  /**
   * 构造异步通知发送服务（无重试队列）
   *
   * @param notifyService 通知服务
   * @param executor 虚拟线程池
   */
  public AsyncNotifyService(
      NotifyService notifyService,
      @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
    this(notifyService, null, executor);
  }

  /**
   * 构造异步通知发送服务
   *
   * @param notifyService 通知服务
   * @param retryQueue 重试队列（可为 null）
   * @param executor 虚拟线程池
   */
  public AsyncNotifyService(
      NotifyService notifyService,
      NotifyRetryQueue retryQueue,
      @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
    this.notifyService = notifyService;
    this.retryQueue = retryQueue;
    this.executor = executor;
  }

  /**
   * 异步发送通知
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param title 标题
   * @param content 内容
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> sendAsync(
      NotifyChannel channel, String receiver, String title, String content) {
    String traceId = resolveTraceId();
    return CompletableFuture.supplyAsync(
            () ->
                NotifyTraceContext.runWithTraceResult(
                    traceId, () -> doSend(channel, receiver, title, content)),
            executor)
        .exceptionally(
            ex -> {
              log.error(
                  "[AsyncNotify] 异步发送异常, channel={}, receiver={}: {}",
                  channel.getName(),
                  receiver,
                  ex.getMessage(),
                  ex);
              return NotifySendResult.failure("异步发送异常: " + ex.getMessage(), channel.getName());
            });
  }

  /**
   * 异步发送完整通知请求（P1-2：保留模板、优先级、userId 等完整上下文）
   *
   * @param request 通知请求
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> sendAsync(NotifyRequest request) {
    if (request == null) {
      return CompletableFuture.completedFuture(NotifySendResult.failure("通知请求为空", "unknown"));
    }
    String traceId = request.getTraceId() != null ? request.getTraceId() : resolveTraceId();
    return CompletableFuture.supplyAsync(
            () -> NotifyTraceContext.runWithTraceResult(traceId, () -> doSendRequest(request)),
            executor)
        .exceptionally(
            ex -> {
              log.error(
                  "[AsyncNotify] 异步发送异常, channel={}, receiver={}: {}",
                  request.getChannel().getName(),
                  request.getReceiver(),
                  ex.getMessage(),
                  ex);
              return NotifySendResult.failure(
                  "异步发送异常: " + ex.getMessage(), request.getChannel().getName());
            });
  }

  /**
   * 异步批量发送通知
   *
   * @param channel 通知渠道
   * @param receivers 接收者列表
   * @param title 标题
   * @param content 内容
   * @return 异步发送结果
   */
  public CompletableFuture<NotifySendResult> batchSendAsync(
      NotifyChannel channel, List<String> receivers, String title, String content) {
    String traceId = resolveTraceId();
    return CompletableFuture.supplyAsync(
            () ->
                NotifyTraceContext.runWithTraceResult(
                    traceId,
                    () -> {
                      int successCount = 0;
                      for (String receiver : receivers) {
                        NotifySendResult result = doSend(channel, receiver, title, content);
                        if (result.isSuccess()) {
                          successCount++;
                        }
                      }
                      return successCount == receivers.size()
                          ? NotifySendResult.success(null, channel.getName())
                          : NotifySendResult.failure(
                              successCount + "/" + receivers.size() + " 发送成功", channel.getName());
                    }),
            executor)
        .exceptionally(
            ex -> {
              log.error("[AsyncNotify] 异步批量发送异常: {}", ex.getMessage(), ex);
              return NotifySendResult.failure("异步批量发送异常: " + ex.getMessage(), channel.getName());
            });
  }

  /** 执行单条发送，失败后进入重试队列（P2-3：不再在此层重试，统一由 RetryQueue 管理） */
  private NotifySendResult doSend(
      NotifyChannel channel, String receiver, String title, String content) {
    try {
      NotifySendResult result = notifyService.send(channel, receiver, title, content);
      if (!result.isSuccess() && retryQueue != null) {
        retryQueue.offer(channel, receiver, title, content, result.getErrorMessage());
        log.warn(
            "[AsyncNotify] 发送失败，已加入重试队列: channel={}, receiver={}, error={}",
            channel.getName(),
            receiver,
            result.getErrorMessage());
      }
      return result;
    } catch (Exception e) {
      log.error(
          "[AsyncNotify] 发送异常: channel={}, receiver={}, error={}",
          channel.getName(),
          receiver,
          e.getMessage(),
          e);
      if (retryQueue != null) {
        retryQueue.offer(channel, receiver, title, content, e.getMessage());
      }
      return NotifySendResult.failure("发送异常: " + e.getMessage(), channel.getName());
    }
  }

  /** 执行完整请求发送（P1-2：保留模板、优先级等上下文） */
  private NotifySendResult doSendRequest(NotifyRequest request) {
    try {
      NotifySendResult result = notifyService.send(request);
      if (!result.isSuccess() && retryQueue != null) {
        String error = result.getErrorMessage() != null ? result.getErrorMessage() : "unknown";
        retryQueue.offer(
            request.getChannel(),
            request.getReceiver(),
            request.getTitle() != null ? request.getTitle() : "",
            request.getContent() != null ? request.getContent() : "",
            error);
        log.warn(
            "[AsyncNotify] 请求发送失败，已加入重试队列: channel={}, receiver={}, error={}",
            request.getChannel().getName(),
            request.getReceiver(),
            error);
      }
      return result;
    } catch (Exception e) {
      log.error(
          "[AsyncNotify] 请求发送异常: channel={}, receiver={}, error={}",
          request.getChannel().getName(),
          request.getReceiver(),
          e.getMessage(),
          e);
      if (retryQueue != null) {
        retryQueue.offer(
            request.getChannel(),
            request.getReceiver(),
            request.getTitle() != null ? request.getTitle() : "",
            request.getContent() != null ? request.getContent() : "",
            e.getMessage());
      }
      return NotifySendResult.failure("发送异常: " + e.getMessage(), request.getChannel().getName());
    }
  }

  /**
   * 解析当前链路 traceId：优先 {@link RequestContext}（统一上下文主源），回退 MDC。
   *
   * @return 当前 traceId；均不存在时返回 null
   */
  private String resolveTraceId() {
    String traceId = RequestContext.getTraceId();
    if (traceId != null && !traceId.isEmpty()) {
      return traceId;
    }
    return MDC.get(NotifyTraceContext.TRACE_ID_KEY);
  }

  /** 关闭线程池 */
  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
  }
}
