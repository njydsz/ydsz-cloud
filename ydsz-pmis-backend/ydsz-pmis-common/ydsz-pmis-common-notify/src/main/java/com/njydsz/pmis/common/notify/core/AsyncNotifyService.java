package com.njydsz.pmis.common.notify.core;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 异步通知发送服务
 *
 * <p>使用共享的虚拟线程池异步发送通知消息，避免大附件等场景阻塞 HTTP 线程。
 *
 * <p>发送失败自动重试 3 次，指数退避
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class AsyncNotifyService {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotifyService.class);

    private static final int MAX_RETRIES = 3;
    private static final int BASE_BACKOFF_MS = 1000;

    private final ExecutorService executor;
    private final NotifyService notifyService;
    private final NotifyRetryQueue retryQueue;

    public AsyncNotifyService(NotifyService notifyService,
                              @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
        this(notifyService, null, executor);
    }

    public AsyncNotifyService(NotifyService notifyService, NotifyRetryQueue retryQueue,
                              @Qualifier("notifyVirtualThreadExecutor") ExecutorService executor) {
        this.notifyService = notifyService;
        this.retryQueue = retryQueue;
        this.executor = executor;
    }

    /**
     * 异步发送通知
     */
    public CompletableFuture<NotifySendResult> sendAsync(NotifyChannel channel, String receiver,
                                                          String title, String content) {
        return CompletableFuture.supplyAsync(() -> sendWithRetry(channel, receiver, title, content), executor)
                .exceptionally(ex -> {
                    log.error("[AsyncNotify] 异步发送异常, channel={}, receiver={}: {}",
                            channel.getName(), receiver, ex.getMessage(), ex);
                    return NotifySendResult.failure("异步发送异常: " + ex.getMessage(), channel.getName());
                });
    }

    /**
     * 异步批量发送通知
     */
    public CompletableFuture<NotifySendResult> batchSendAsync(NotifyChannel channel,
                                                               List<String> receivers, String title, String content) {
        return CompletableFuture.supplyAsync(() -> {
            int successCount = 0;
            for (String receiver : receivers) {
                NotifySendResult result = sendWithRetry(channel, receiver, title, content);
                if (result.isSuccess()) {
                    successCount++;
                }
            }
            return successCount == receivers.size()
                    ? NotifySendResult.success(null, channel.getName())
                    : NotifySendResult.failure(successCount + "/" + receivers.size() + " 发送成功", channel.getName());
        }, executor).exceptionally(ex -> {
            log.error("[AsyncNotify] 异步批量发送异常: {}", ex.getMessage(), ex);
            return NotifySendResult.failure("异步批量发送异常: " + ex.getMessage(), channel.getName());
        });
    }

    private NotifySendResult sendWithRetry(NotifyChannel channel, String receiver,
                                            String title, String content) {
        int attempts = 0;
        while (true) {
            try {
                NotifySendResult result = notifyService.send(channel, receiver, title, content);
                if (result.isSuccess()) {
                    return result;
                }
                // 发送失败，进入重试逻辑
                throw new InfrastructureException(result.getErrorMessage());
            } catch (Exception e) {
                int nextAttempt = attempts + 1;
                if (nextAttempt > MAX_RETRIES) {
                    String errorMsg = extractMessage(e);
                    log.error("[AsyncNotify] 发送失败已达最大重试次数，channel={}, receiver={}, error={}",
                            channel.getName(), receiver, errorMsg);
                    if (retryQueue != null) {
                        retryQueue.offer(channel, receiver, title, content, errorMsg);
                    }
                    return NotifySendResult.failure(
                            "发送失败，已重试 " + MAX_RETRIES + " 次: " + errorMsg, channel.getName());
                }

                long backoffMs = BASE_BACKOFF_MS * (1L << (nextAttempt - 1));
                log.warn("[AsyncNotify] 发送失败，将在 {}ms 后进行第 {} 次重试 ({}/{}), channel={}, receiver={}",
                        backoffMs, nextAttempt, nextAttempt, MAX_RETRIES, channel.getName(), receiver);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    // 中断时设置中断标志，并将消息放入 retryQueue 等待后续处理，避免消息丢失
                    Thread.currentThread().interrupt();
                    String errorMsg = extractMessage(e);
                    if (retryQueue != null) {
                        retryQueue.offer(channel, receiver, title, content, "重试被中断: " + errorMsg);
                        log.warn("[AsyncNotify] 重试被中断，消息已放入重试队列: channel={}, receiver={}",
                                channel.getName(), receiver);
                    }
                    return NotifySendResult.failure("重试被中断: " + errorMsg, channel.getName());
                }
                attempts = nextAttempt;
            }
        }
    }

    private String extractMessage(Throwable ex) {
        if (ex == null) {
            return "未知错误";
        }
        Throwable cause = ex.getCause();
        if (cause != null) {
            return cause.getMessage() != null ? cause.getMessage() : "未知错误";
        }
        return ex.getMessage() != null ? ex.getMessage() : "未知错误";
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
