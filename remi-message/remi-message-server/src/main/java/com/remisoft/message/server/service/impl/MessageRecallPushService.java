package com.remisoft.message.server.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.remisoft.message.server.realtime.RealtimePushService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-19: 消息撤回实时推送。
 *
 * <p>当消息被撤回时，通过 WebSocket 实时推送撤回通知到客户端，
 * 客户端收到后从消息列表中移除或标记为已撤回。
 *
 * <p>推送内容：
 * <ul>
 *   <li>type: RECALL</li>
 *   <li>messageId: 被撤回的消息 ID</li>
 *   <li>recallReason: 撤回原因</li>
 *   <li>recallTime: 撤回时间戳</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRecallPushService {

    private final RealtimePushService realtimePushService;

    /**
     * 推送消息撤回通知。
     *
     * @param userId       用户 ID
     * @param messageId    被撤回的消息 ID
     * @param recallReason 撤回原因
     */
    public void pushRecall(String userId, String messageId, String recallReason) {
        try {
            Map<String, Object> recallData = new HashMap<>(4);
            recallData.put("type", "RECALL");
            recallData.put("messageId", messageId);
            recallData.put("recallReason", recallReason);
            recallData.put("recallTime", System.currentTimeMillis());
            realtimePushService.pushToUser(userId, "RECALL", recallData);
            log.info("[RecallPush] 撤回推送已发送: userId={} messageId={}", userId, messageId);
        } catch (Exception e) {
            log.error("[RecallPush] 撤回推送失败: userId={} messageId={} err={}",
                    userId, messageId, e.getMessage());
        }
    }

    /**
     * 批量推送撤回通知。
     * <p>D-2: 使用 CompletableFuture 并行推送，替代串行 for 循环。
     *
     * @param userIds    用户 ID 列表
     * @param messageId  被撤回的消息 ID
     * @param recallReason 撤回原因
     */
    public void pushRecallBatch(List<String> userIds, String messageId, String recallReason) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        // D-2: 并行推送，所有 CompletableFuture 完成后统一等待
        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> CompletableFuture.runAsync(
                        () -> pushRecall(userId, messageId, recallReason)))
                .toList();
        // 等待所有推送完成，最多 10s 超时
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
    }
}
