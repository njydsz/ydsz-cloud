package com.njydsz.pmis.common.socket.push;

import java.util.List;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.pmis.common.socket.ack.MessageAckService;
import com.njydsz.pmis.common.socket.audit.WebSocketAuditService;
import com.njydsz.pmis.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.pmis.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.pmis.common.socket.compress.MessageCompressor;
import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import com.njydsz.pmis.common.socket.enums.MessagePriority;
import com.njydsz.pmis.common.socket.filter.MessageFilter;
import com.njydsz.pmis.common.socket.metric.WebSocketMetrics;
import com.njydsz.pmis.common.socket.monitor.SlowConnectionDetector;
import com.njydsz.pmis.common.socket.offline.OfflineMessageStore;
import com.njydsz.pmis.common.socket.retry.MessageRetryQueue;
import com.njydsz.pmis.common.socket.retry.RedisMessageRetryQueue;
import com.njydsz.pmis.common.socket.retry.RetryableMessage;
import com.njydsz.pmis.common.socket.serialize.MessageSerializer;
import com.njydsz.pmis.common.socket.session.OnlineUserService;
import com.njydsz.pmis.common.socket.trace.WebSocketTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认实时推送模板实现（STOMP + Redis Pub/Sub 集群广播 + 降级 + 离线补偿 + 全链路增强）。
 *
 * <p>推送流程：
 * <ol>
 *   <li>消息过滤器链检查（P3-5）</li>
 *   <li>消息序列化（P3-5，支持自定义协议）</li>
 *   <li>消息压缩（P2-3，超过阈值自动 GZIP）</li>
 *   <li>注入 traceId（P1-1）</li>
 *   <li>通过集群广播发布（熔断保护 P0-2）</li>
 *   <li>Redis 发布失败时降级为本地直接推送</li>
 *   <li>本地推送失败时入重试队列（P0-4）</li>
 *   <li>注册 ACK 待确认记录（P1-2）</li>
 *   <li>记录审计日志 + 指标 + 慢连接检测（P2-2/P2-5）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public class DefaultRealtimePushTemplate implements RealtimePushTemplate {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketClusterPublisher clusterPublisher;
    private final OnlineUserService onlineUserService;
    private final OfflineMessageStore offlineMessageStore;
    private final WebSocketMetrics webSocketMetrics;
    private final MessageSerializer messageSerializer;
    private final MessageCompressor messageCompressor;
    private final WebSocketAuditService auditService;
    private final SlowConnectionDetector slowConnectionDetector;
    private final MessageAckService ackService;
    private final MessageRetryQueue retryQueue;
    private final List<MessageFilter> messageFilters;

    public DefaultRealtimePushTemplate(
            SimpMessagingTemplate messagingTemplate,
            WebSocketClusterPublisher clusterPublisher,
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            WebSocketMetrics webSocketMetrics,
            MessageSerializer messageSerializer,
            MessageCompressor messageCompressor,
            WebSocketAuditService auditService,
            SlowConnectionDetector slowConnectionDetector,
            MessageAckService ackService,
            MessageRetryQueue retryQueue,
            List<MessageFilter> messageFilters) {
        this.messagingTemplate = messagingTemplate;
        this.clusterPublisher = clusterPublisher;
        this.onlineUserService = onlineUserService;
        this.offlineMessageStore = offlineMessageStore;
        this.webSocketMetrics = webSocketMetrics;
        this.messageSerializer = messageSerializer;
        this.messageCompressor = messageCompressor;
        this.auditService = auditService;
        this.slowConnectionDetector = slowConnectionDetector;
        this.ackService = ackService;
        this.retryQueue = retryQueue;
        this.messageFilters = messageFilters != null ? messageFilters : List.of();
    }

    // ==================== 原有接口方法 ====================

    @Override
    public void pushToUser(String userId, String type, Object payload) {
        pushToUser(userId, type, payload, MessagePriority.NORMAL.name());
    }

    @Override
    public void pushToUser(String userId, String type, Object payload, String priority) {
        if (userId == null) {
            return;
        }
        String payloadJson = serializeAndCompress(payload);
        if (!applyFilters(userId, "USER", payloadJson)) {
            return;
        }

        String messageId = generateMessageId();
        String traceId = WebSocketTraceContext.getOrGenerateTraceId();

        WebSocketClusterMessage msg = WebSocketClusterMessage.forUser(userId, type, payloadJson);
        msg.setTraceId(traceId);
        msg.setPriority(priority);

        long start = System.currentTimeMillis();
        boolean success;
        if (!clusterPublisher.publish(msg)) {
            success = localPushToUser(userId, payloadJson);
            if (!success && retryQueue != null) {
                enqueueRetry(messageId, userId, type, payloadJson);
            }
        } else {
            success = true;
        }
        long duration = System.currentTimeMillis() - start;

        webSocketMetrics.recordPush("USER", success);
        if (slowConnectionDetector != null) {
            slowConnectionDetector.recordPushDuration(userId, duration);
        }
        if (auditService != null) {
            auditService.auditPush("USER", userId, null, success, duration,
                    success ? null : "local push failed");
        }
        if (ackService != null && success) {
            ackService.registerPendingAck(messageId, userId);
        }
    }

    @Override
    public void pushToUserWithOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            if (onlineUserService.isOnline(userId)) {
                pushToUser(userId, type, payload);
            } else {
                offlineMessageStore.cacheOffline(userId, type, payload);
                log.info("[WebSocket] 用户离线，消息已缓存: userId={}, type={}", userId, type);
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 在线检查异常，降级直接推送: userId={}, err={}", userId, e.getMessage());
            pushToUser(userId, type, payload);
        }
    }

    @Override
    public void broadcast(Object payload) {
        broadcast("BROADCAST", payload);
    }

    @Override
    public void broadcast(String type, Object payload) {
        String payloadJson = serializeAndCompress(payload);
        if (!applyFilters(null, "BROADCAST", payloadJson)) {
            return;
        }

        String traceId = WebSocketTraceContext.getOrGenerateTraceId();
        WebSocketClusterMessage msg = WebSocketClusterMessage.forBroadcast(type, payloadJson);
        msg.setTraceId(traceId);

        long start = System.currentTimeMillis();
        boolean success;
        if (!clusterPublisher.publish(msg)) {
            success = localBroadcast(payloadJson);
        } else {
            success = true;
        }
        long duration = System.currentTimeMillis() - start;

        webSocketMetrics.recordPush("BROADCAST", success);
        if (slowConnectionDetector != null) {
            slowConnectionDetector.recordPushDuration(null, duration);
        }
        if (auditService != null) {
            auditService.auditPush("BROADCAST", null, null, success, duration,
                    success ? null : "local broadcast failed");
        }
    }

    @Override
    public void pushToTopic(String topic, Object payload) {
        String payloadJson = serializeAndCompress(payload);
        if (!applyFilters(null, "TOPIC", payloadJson)) {
            return;
        }

        String traceId = WebSocketTraceContext.getOrGenerateTraceId();
        WebSocketClusterMessage msg = WebSocketClusterMessage.forTopic(topic, payloadJson);
        msg.setTraceId(traceId);

        long start = System.currentTimeMillis();
        boolean success;
        if (!clusterPublisher.publish(msg)) {
            success = localPushToTopic(topic, payloadJson);
        } else {
            success = true;
        }
        long duration = System.currentTimeMillis() - start;

        webSocketMetrics.recordPush("TOPIC", success);
        if (slowConnectionDetector != null) {
            slowConnectionDetector.recordPushDuration(null, duration);
        }
        if (auditService != null) {
            auditService.auditPush("TOPIC", null, topic, success, duration,
                    success ? null : "local topic push failed");
        }
    }

    // ==================== 新增接口方法 ====================

    @Override
    public void pushToUserWithTtl(String userId, String type, Object payload, long ttlSeconds) {
        if (userId == null) {
            return;
        }
        String payloadJson = serializeAndCompress(payload);
        if (!applyFilters(userId, "USER", payloadJson)) {
            return;
        }

        // 包装 payload 添加 TTL 信息
        String wrappedJson = wrapWithTtl(payloadJson, ttlSeconds);
        pushToUser(userId, type, wrappedJson);
    }

    @Override
    public void flushRetryMessages() {
        if (retryQueue == null) {
            return;
        }
        try {
            List<RetryableMessage> expired = retryQueue.dequeueExpired(100);
            if (expired.isEmpty()) {
                return;
            }
            log.info("[WebSocket] 刷新重试队列: count={}", expired.size());
            for (RetryableMessage msg : expired) {
                boolean success = retryPush(msg);
                if (success) {
                    retryQueue.markSuccess(msg.getMessageId());
                } else {
                    if (retryQueue instanceof RedisMessageRetryQueue redisQueue) {
                        redisQueue.requeueWithIncrement(msg);
                    } else {
                        retryQueue.markFailed(msg.getMessageId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 刷新重试队列异常: err={}", e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private String serializeAndCompress(Object payload) {
        String json = messageSerializer.serialize(payload);
        if (messageCompressor != null) {
            json = messageCompressor.compressIfNeeded(json);
        }
        return json;
    }

    private boolean applyFilters(String userId, String pushType, String payloadJson) {
        for (MessageFilter filter : messageFilters) {
            if (!filter.shouldSend(userId, pushType, payloadJson)) {
                log.info("[WebSocket] 消息被过滤器拦截: filter={}, userId={}, pushType={}",
                        filter.getName(), userId, pushType);
                return false;
            }
        }
        return true;
    }

    private boolean localPushToUser(String userId, String payloadJson) {
        try {
            String destination = WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, payloadJson);
            log.debug("[WebSocket] 本地降级推送: userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级推送失败: userId={}, error={}", userId, e.getMessage());
            webSocketMetrics.recordPush("USER", false);
            return false;
        }
    }

    private boolean localBroadcast(String payloadJson) {
        try {
            messagingTemplate.convertAndSend(WebSocketConstants.WS_BROADCAST_DESTINATION, payloadJson);
            return true;
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级广播失败: error={}", e.getMessage());
            webSocketMetrics.recordPush("BROADCAST", false);
            return false;
        }
    }

    private boolean localPushToTopic(String topic, String payloadJson) {
        try {
            messagingTemplate.convertAndSend(
                    WebSocketConstants.WS_TOPIC_DESTINATION_PREFIX + topic, payloadJson);
            return true;
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级主题推送失败: topic={}, error={}", topic, e.getMessage());
            webSocketMetrics.recordPush("TOPIC", false);
            return false;
        }
    }

    private void enqueueRetry(String messageId, String userId, String type, String payloadJson) {
        if (retryQueue == null) {
            return;
        }
        try {
            RetryableMessage retryMsg = RetryableMessage.forUser(
                    messageId, userId, type, payloadJson, 3, 5000);
            retryMsg.setTraceId(WebSocketTraceContext.getTraceId());
            retryQueue.enqueue(retryMsg);
        } catch (Exception e) {
            log.warn("[WebSocket] 重试入队失败: messageId={}, err={}", messageId, e.getMessage());
        }
    }

    private boolean retryPush(RetryableMessage msg) {
        try {
            if ("USER".equals(msg.getPushType()) && msg.getUserId() != null) {
                return localPushToUser(msg.getUserId(), msg.getPayloadJson());
            } else if ("BROADCAST".equals(msg.getPushType())) {
                return localBroadcast(msg.getPayloadJson());
            } else if ("TOPIC".equals(msg.getPushType()) && msg.getTopic() != null) {
                return localPushToTopic(msg.getTopic(), msg.getPayloadJson());
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 重试推送失败: messageId={}, err={}", msg.getMessageId(), e.getMessage());
        }
        return false;
    }

    private String wrapWithTtl(String payloadJson, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return payloadJson;
        }
        return "{\"_ttlSeconds\":" + ttlSeconds
                + ",\"_expireAt\":" + (System.currentTimeMillis() + ttlSeconds * 1000)
                + ",\"data\":" + payloadJson + "}";
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
