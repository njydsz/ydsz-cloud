package com.njydsz.message.server.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.batch.BatchSendResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-15: 并行批量发送 + 流控。
 *
 * <p>使用通道级线程池（Bulkhead）并行发送批量消息，同时通过 Semaphore 控制并发度，
 * 避免瞬时高峰压垮下游服务商（如 SMS 网关、SMTP 服务器）。
 *
 * <p>特性：
 * <ul>
 *   <li>并行发送：基于 {@link CompletableFuture} + 通道级线程池</li>
 *   <li>流控：Semaphore 限制单批次最大并发数</li>
 *   <li>超时控制：单条发送超时不影响整体批次</li>
 *   <li>结果聚合：统一收集成功/失败/跳过计数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParallelBatchSender {

    /** 通道级线程池 Map */
    private final Map<String, ExecutorService> channelExecutorMap;

    /** 单批次最大并发数 */
    private static final int MAX_CONCURRENCY = 20;

    /** 单条发送超时时间（秒） */
    private static final long SEND_TIMEOUT_SECONDS = 30L;

    /**
     * 并行发送批量消息。
     *
     * @param requests  消息请求列表
     * @param channel   发送通道
     * @param sender    实际发送函数
     * @return 批量发送结果
     */
    public BatchSendResult sendBatch(List<MessageRequest> requests, String channel,
                                     Function<MessageRequest, MessageResult> sender) {
        if (requests == null || requests.isEmpty()) {
            return new BatchSendResult();
        }
        ExecutorService executor = channelExecutorMap.get(channel);
        if (executor == null) {
            executor = channelExecutorMap.get("INAPP");
        }
        if (executor == null) {
            log.warn("[ParallelBatch] 未找到通道线程池,降级串行: channel={}", channel);
            return sendSequential(requests, sender);
        }
        Semaphore semaphore = new Semaphore(MAX_CONCURRENCY);
        List<CompletableFuture<MessageResult>> futures = new ArrayList<>(requests.size());
        for (MessageRequest request : requests) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    if (!semaphore.tryAcquire(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        log.warn("[ParallelBatch] 获取信号量超时: msgId={}", request.getMessageId());
                        return MessageResult.fail(channel, "流控超时");
                    }
                    try {
                        return sender.apply(request);
                    } finally {
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return MessageResult.fail(channel, "发送被中断");
                }
            }, executor));
        }
        // 等待所有发送完成
        int success = 0;
        int failure = 0;
        int skipped = 0;
        for (int i = 0; i < futures.size(); i++) {
            try {
                MessageResult result = futures.get(i).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result.isSuccess()) {
                    success++;
                } else {
                    failure++;
                }
            } catch (Exception e) {
                failure++;
                log.error("[ParallelBatch] 发送异常: msgId={} err={}",
                        requests.get(i).getMessageId(), e.getMessage());
            }
        }
        log.info("[ParallelBatch] 批量发送完成: channel={} total={} success={} failure={} skipped={}",
                channel, requests.size(), success, failure, skipped);
        return new BatchSendResult(null, requests.size(), success, failure, skipped);
    }

    /**
     * 串行发送降级方案。
     *
     * @param requests 消息请求列表
     * @param sender   发送函数
     * @return 批量发送结果
     */
    private BatchSendResult sendSequential(List<MessageRequest> requests,
                                           Function<MessageRequest, MessageResult> sender) {
        int success = 0;
        int failure = 0;
        for (MessageRequest request : requests) {
            try {
                MessageResult result = sender.apply(request);
                if (result.isSuccess()) {
                    success++;
                } else {
                    failure++;
                }
            } catch (Exception e) {
                failure++;
            }
        }
        return new BatchSendResult(null, requests.size(), success, failure, 0);
    }
}
