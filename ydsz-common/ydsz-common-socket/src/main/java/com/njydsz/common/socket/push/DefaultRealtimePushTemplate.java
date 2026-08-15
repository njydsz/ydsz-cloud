package com.njydsz.common.socket.push;

import java.util.List;
import java.util.Map;
import com.njydsz.common.json.YdszJson;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.common.socket.ack.MessageAckService;
import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.common.socket.compress.MessageCompressor;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.enums.MessagePriority;
import com.njydsz.common.socket.filter.MessageFilter;
import com.njydsz.common.socket.metric.WebSocketMetrics;
import com.njydsz.common.socket.monitor.SlowConnectionDetector;
import com.njydsz.common.socket.offline.OfflineMessageStore;
import com.njydsz.common.socket.retry.MessageRetryQueue;
import com.njydsz.common.socket.retry.RedisMessageRetryQueue;
import com.njydsz.common.socket.retry.RetryableMessage;
import com.njydsz.common.socket.serialize.MessageSerializer;
import com.njydsz.common.socket.session.OnlineUserService;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.util.id.IdGenerator;

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
 * @author ydsz-team
 * @since 1.0.0
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

    // ==================== 接口方法实现 ====================

    /**
     * 推送消息到指定用户（默认优先级）。
     *
     * @param userId  用户 ID
     * @param type    消息类型
     * @param payload 消息负载（将被序列化为 JSON）
     */
    @Override
    public void pushToUser(String userId, String type, Object payload) {
        pushToUser(userId, type, payload, MessagePriority.NORMAL.name());
    }

    /**
     * 推送消息到指定用户（指定优先级）。
     *
     * <p>推送流程：
     * <ol>
     *   <li>消息过滤器链检查（拦截黑名单/敏感内容）</li>
     *   <li>序列化 + 压缩（超过阈值自动 GZIP）</li>
     *   <li>注入 traceId（链路追踪）</li>
     *   <li>通过集群广播发布（熔断保护）</li>
     *   <li>Redis 发布失败时降级为本地直接推送</li>
     *   <li>本地推送失败时入重试队列</li>
     *   <li>注册 ACK 待确认记录</li>
     *   <li>记录审计日志 + 指标 + 慢连接检测</li>
     * </ol>
     *
     * @param userId   用户 ID（为 null 时直接返回）
     * @param type     消息类型
     * @param payload  消息负载
     * @param priority 消息优先级（影响重试队列排序）
     */
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

    /**
     * 推送消息到指定用户（用户离线时缓存到离线存储）。
     *
     * <p>若用户在线则直接推送，否则缓存到离线存储（用户上线后拉取）。
     * 在线检查失败时降级为直接推送。
     *
     * @param userId   用户 ID（为 null 时直接返回）
     * @param type     消息类型
     * @param payload  消息负载
     */
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

    /**
     * 广播消息到所有在线用户（默认类型）。
     *
     * @param payload 消息负载（将被序列化为 JSON）
     */
    @Override
    public void broadcast(Object payload) {
        broadcast("BROADCAST", payload);
    }

    /**
     * 广播指定类型的消息到所有在线用户。
     *
     * <p>推送流程与 {@link #pushToUser} 类似，但不指定用户 ID，
     * 目标为所有订阅了广播主题的连接。
     *
     * @param type     消息类型
     * @param payload  消息负载
     */
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

    /**
     * 推送消息到指定主题（Topic）。
     *
     * <p>适用于发布-订阅模式，消息推送到指定 Topic，
     * 只有订阅了该 Topic 的连接才会收到。
     *
     * @param topic    主题名称（如 {@code "order.created"}）
     * @param payload  消息负载
     */
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

    /**
     * 推送带 TTL 的消息到指定用户。
     *
     * <p>消息在客户端有有效期，超过 TTL 后客户端应忽略该消息。
     * TTL 信息包装在 payload 外层，格式为 {@code {"_ttlSeconds": N, "_expireAt": timestamp, "data": originalPayload}}。
     *
     * @param userId     用户 ID
     * @param type       消息类型
     * @param payload    消息负载
     * @param ttlSeconds 有效期（秒），≤ 0 时不包装 TTL
     */
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

    /**
     * 刷新重试队列（由定时任务调用）。
     *
     * <p>从重试队列中取出到期的重试消息，最多 100 条，
     * 重新尝试推送。重试成功后标记为成功，失败后重试次数+1（最大 3 次），
     * 超过最大重试次数则标记为失败。
     *
     * @return 本次刷新处理的消息数量
     */
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

    // ==================== 内部辅助方法 ====================

    /**
     * 序列化并压缩消息负载。
     *
     * <p>先使用 {@link MessageSerializer} 序列化为 JSON，
     * 然后使用 {@link MessageCompressor} 判断是否需要 GZIP 压缩。
     *
     * @param payload 原始消息对象
     * @return 序列化并（可选）压缩后的 JSON 字符串
     */
    private String serializeAndCompress(Object payload) {
        String json = messageSerializer.serialize(payload);
        if (messageCompressor != null) {
            json = messageCompressor.compressIfNeeded(json);
        }
        return json;
    }

    /**
     * 应用消息过滤器链。
     *
     * <p>按顺序调用所有过滤器，任一过滤器返回 false 则拦截消息。
     *
     * @param userId      用户 ID（可能为 null）
     * @param pushType    推送类型（USER/BROADCAST/TOPIC）
     * @param payloadJson 序列化后的消息 JSON
     * @return 是否通过所有过滤器
     */
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

    /**
     * 本地降级推送到指定用户。
     *
     * <p>当集群广播失败时降级为本地 STOMP 推送，
     * 仅能推送到当前实例的 WebSocket 连接。
     *
     * @param userId      用户 ID
     * @param payloadJson 序列化后的消息 JSON
     * @return 是否推送成功
     */
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

    /**
     * 本地降级广播消息。
     *
     * <p>当集群广播失败时降级为本地 STOMP 广播，
     * 仅能推送到当前实例的 WebSocket 连接。
     *
     * @param payloadJson 序列化后的消息 JSON
     * @return 是否推送成功
     */
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

    /**
     * 本地降级推送到指定主题。
     *
     * <p>当集群广播失败时降级为本地 STOMP 主题推送，
     * 仅能推送到当前实例的 WebSocket 连接。
     *
     * @param topic       主题名称
     * @param payloadJson 序列化后的消息 JSON
     * @return 是否推送成功
     */
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

    /**
     * 将消息加入重试队列。
     *
     * @param messageId   消息 ID
     * @param userId      用户 ID
     * @param type        消息类型
     * @param payloadJson 序列化后的消息 JSON
     */
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

    /**
     * 重试推送消息。
     *
     * <p>根据消息的推送类型（USER/BROADCAST/TOPIC）选择对应的重试方法。
     *
     * @param msg 重试消息
     * @return 是否推送成功
     */
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

    /**
     * 包装消息添加 TTL 信息。
     *
     * <p>在原始 payload 外层包裹 TTL 字段：
     * <pre>
     * {
     *   "_ttlSeconds": N,
     *   "_expireAt": timestamp,
     *   "data": originalPayloadJson
     * }
     * </pre>
     *
     * @param payloadJson 原始序列化后的消息 JSON
     * @param ttlSeconds  有效期（秒）
     * @return 包装后的 JSON 字符串（ttlSeconds ≤ 0 时返回原始值）
     */
    private String wrapWithTtl(String payloadJson, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return payloadJson;
        }
        return YdszJson.toJson(Map.of("_ttlSeconds", ttlSeconds,
                "_expireAt", System.currentTimeMillis() + ttlSeconds * 1000,
                "data", payloadJson));
    }

    /**
     * 生成唯一消息 ID。
     *
     * @return 去除连字符的 UUID 字符串（32 位十六进制）
     */
    private String generateMessageId() {
        return IdGenerator.nextIdStr();
    }
}
