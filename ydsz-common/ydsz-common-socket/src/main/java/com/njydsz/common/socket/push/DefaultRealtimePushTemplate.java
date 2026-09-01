package com.njydsz.common.socket.push;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.enums.MessagePriority;
import com.njydsz.common.socket.filter.MessageFilter;
import com.njydsz.common.socket.metric.WebSocketMetrics;
import com.njydsz.common.socket.offline.OfflineMessageStore;
import com.njydsz.common.socket.retry.MessageRetryQueue;
import com.njydsz.common.socket.retry.RedisMessageRetryQueue;
import com.njydsz.common.socket.retry.RetryableMessage;
import com.njydsz.common.socket.serialize.MessageSerializer;
import com.njydsz.common.socket.session.OnlineUserService;
import com.njydsz.common.socket.trace.WebSocketTraceContext;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 默认实时推送模板实现（STOMP + Redis Pub/Sub 集群广播 + 降级 + 离线补偿 + 全链路增强）。
 *
 * <p>推送流程：
 *
 * <ol>
 *   <li>消息过滤器链检查（P3-5）
 *   <li>消息序列化（支持自定义协议）
 *   <li>注入 traceId
 *   <li>通过集群广播发布（熔断保护 P0-2）
 *   <li>Redis 发布失败时降级为本地直接推送
 *   <li>本地推送失败时入重试队列（P0-4）
 *   <li>记录审计日志 + 指标（P2-5）
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DefaultRealtimePushTemplate implements RealtimePushTemplate {

  private final SimpMessagingTemplate messagingTemplate;
  private final WebSocketClusterPublisher clusterPublisher;
  private final OnlineUserService onlineUserService;
  private final OfflineMessageStore offlineMessageStore;
  private final WebSocketMetrics webSocketMetrics;
  private final MessageSerializer messageSerializer;
  private final WebSocketAuditService auditService;
  private final MessageRetryQueue retryQueue;
  private final List<MessageFilter> messageFilters;

  public DefaultRealtimePushTemplate(
      SimpMessagingTemplate messagingTemplate,
      WebSocketClusterPublisher clusterPublisher,
      OnlineUserService onlineUserService,
      OfflineMessageStore offlineMessageStore,
      WebSocketMetrics webSocketMetrics,
      MessageSerializer messageSerializer,
      WebSocketAuditService auditService,
      MessageRetryQueue retryQueue,
      List<MessageFilter> messageFilters) {
    this.messagingTemplate = messagingTemplate;
    this.clusterPublisher = clusterPublisher;
    this.onlineUserService = onlineUserService;
    this.offlineMessageStore = offlineMessageStore;
    this.webSocketMetrics = webSocketMetrics;
    this.messageSerializer = messageSerializer;
    this.auditService = auditService;
    this.retryQueue = retryQueue;
    this.messageFilters = messageFilters != null ? messageFilters : List.of();
  }

  // ==================== 接口方法实现 ====================

  /**
   * 推送消息到指定用户（默认优先级）。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载（将被序列化为 JSON）
   */
  @Override
  public void pushToUser(String userId, String type, Object payload) {
    pushToUserWithMessageId(userId, type, payload, null);
  }

  /**
   * 推送消息到指定用户（带业务级消息 ID，用于幂等去重）。
   *
   * <p>当 {@code messageId} 非空时，使用该值替代随机生成的 UUID， 便于业务方基于业务 ID（如订单号）实现幂等去重。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载
   * @param messageId 业务级消息唯一 ID
   */
  @Override
  public void pushToUserWithMessageId(
      String userId, String type, Object payload, String messageId) {
    pushToUserInternal(userId, type, payload, MessagePriority.NORMAL.name(), messageId);
  }

  /**
   * 推送消息到指定用户（指定优先级）。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载
   * @param priority 消息优先级
   */
  @Override
  public void pushToUser(String userId, String type, Object payload, String priority) {
    pushToUserInternal(userId, type, payload, priority, null);
  }

  /**
   * 推送消息到指定用户（带业务级消息 ID + 离线补偿）。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载
   * @param messageId 业务级消息唯一 ID
   */
  @Override
  public void pushToUserWithOffline(String userId, String type, Object payload, String messageId) {
    if (userId == null) {
      return;
    }
    try {
      if (onlineUserService.isOnline(userId)) {
        pushToUserWithMessageId(userId, type, payload, messageId);
      } else {
        offlineMessageStore.cacheOffline(userId, type, payload);
        log.info(
            "[WebSocket] 用户离线，消息已缓存: userId={}, type={}, messageId={}", userId, type, messageId);
      }
    } catch (Exception e) {
      log.warn("[WebSocket] 在线检查异常，降级直接推送: userId={}, err={}", userId, e.getMessage());
      pushToUserWithMessageId(userId, type, payload, messageId);
    }
  }

  /**
   * 内部推送方法（支持外部传入 messageId）。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载
   * @param priority 优先级
   * @param messageId 业务级消息 ID（为空时自动生成）
   */
  private void pushToUserInternal(
      String userId, String type, Object payload, String priority, String messageId) {
    if (userId == null) {
      return;
    }
    if (!applyFilters(PushContext.forUser(userId, type, payload, messageId, priority))) {
      return;
    }
    String payloadJson = messageSerializer.serialize(payload);

    String actualMessageId =
        (messageId != null && !messageId.isEmpty()) ? messageId : generateMessageId();
    String traceId = WebSocketTraceContext.getOrGenerateTraceId();

    WebSocketClusterMessage msg = WebSocketClusterMessage.forUser(userId, type, payloadJson);
    msg.setTraceId(traceId);
    msg.setPriority(priority);

    long start = System.currentTimeMillis();
    boolean success;
    if (!clusterPublisher.publish(msg)) {
      success = localPushToUser(userId, payloadJson);
      if (!success && retryQueue != null) {
        enqueueRetry(actualMessageId, userId, type, payloadJson);
      }
    } else {
      success = true;
    }
    long duration = System.currentTimeMillis() - start;

    webSocketMetrics.recordPush("USER", success);
    if (auditService != null) {
      auditService.auditPush(
          "USER", userId, null, success, duration, success ? null : "local push failed");
    }
  }

  /**
   * 推送消息到指定用户（用户离线时缓存到离线存储）。
   *
   * <p>若用户在线则直接推送，否则缓存到离线存储（用户上线后拉取）。 在线检查失败时降级为直接推送。
   *
   * @param userId 用户 ID（为 null 时直接返回）
   * @param type 消息类型
   * @param payload 消息负载
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
   * <p>推送流程与 {@link #pushToUser} 类似，但不指定用户 ID， 目标为所有订阅了广播主题的连接。
   *
   * @param type 消息类型
   * @param payload 消息负载
   */
  @Override
  public void broadcast(String type, Object payload) {
    broadcast(type, payload, null);
  }

  /**
   * 广播指定类型的消息到所有在线用户（带消息 ID，用于幂等去重）。
   *
   * @param type 消息类型
   * @param payload 消息负载
   * @param messageId 业务级消息唯一 ID
   */
  @Override
  public void broadcast(String type, Object payload, String messageId) {
    if (!applyFilters(PushContext.forBroadcast(type, payload, MessagePriority.NORMAL.name()))) {
      return;
    }
    String payloadJson = messageSerializer.serialize(payload);

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
    if (auditService != null) {
      auditService.auditPush(
          "BROADCAST", null, null, success, duration, success ? null : "local broadcast failed");
    }
  }

  /**
   * 推送消息到指定主题（Topic）。
   *
   * <p>适用于发布-订阅模式，消息推送到指定 Topic， 只有订阅了该 Topic 的连接才会收到。
   *
   * @param topic 主题名称（如 {@code "order.created"}）
   * @param payload 消息负载
   */
  @Override
  public void pushToTopic(String topic, Object payload) {
    if (!applyFilters(
        PushContext.forTopic(topic, "TOPIC", payload, MessagePriority.NORMAL.name()))) {
      return;
    }
    String payloadJson = messageSerializer.serialize(payload);

    String traceId = WebSocketTraceContext.getOrGenerateTraceId();
    WebSocketClusterMessage msg = WebSocketClusterMessage.forTopic(topic, null, payloadJson);
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
    if (auditService != null) {
      auditService.auditPush(
          "TOPIC", null, topic, success, duration, success ? null : "local topic push failed");
    }
  }

  // ==================== 新增接口方法 ====================

  /**
   * 推送带 TTL 的消息到指定用户。
   *
   * <p>消息在客户端有有效期，超过 TTL 后客户端应忽略该消息。 TTL 信息包装在 payload 外层，格式为 {@code {"_ttlSeconds": N,
   * "_expireAt": timestamp, "data": originalPayload}}。
   *
   * <p><b>P0-1-fix</b>：修复双重序列化 Bug —— 将原始 payload 对象包装 TTL 后直接传入内部推送方法， 确保仅序列化一次（避免客户端收到被转义的 JSON
   * 字符串而非对象）。
   *
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payload 消息负载
   * @param ttlSeconds 有效期（秒），≤ 0 时不包装 TTL
   */
  @Override
  public void pushToUserWithTtl(String userId, String type, Object payload, long ttlSeconds) {
    if (userId == null) {
      return;
    }
    // 包装原始 payload 对象（非 JSON 字符串），确保后续仅序列化一次
    Object wrappedPayload = wrapWithTtl(payload, ttlSeconds);
    if (!applyFilters(
        PushContext.forUser(userId, type, wrappedPayload, MessagePriority.NORMAL.name()))) {
      return;
    }
    String payloadJson = messageSerializer.serialize(wrappedPayload);

    String actualMessageId = generateMessageId();
    String traceId = WebSocketTraceContext.getOrGenerateTraceId();

    WebSocketClusterMessage msg = WebSocketClusterMessage.forUser(userId, type, payloadJson);
    msg.setTraceId(traceId);
    msg.setPriority(MessagePriority.NORMAL.name());

    long start = System.currentTimeMillis();
    boolean success;
    if (!clusterPublisher.publish(msg)) {
      success = localPushToUser(userId, payloadJson);
      if (!success && retryQueue != null) {
        enqueueRetry(actualMessageId, userId, type, payloadJson);
      }
    } else {
      success = true;
    }
    long duration = System.currentTimeMillis() - start;

    webSocketMetrics.recordPush("USER", success);
    if (auditService != null) {
      auditService.auditPush(
          "USER", userId, null, success, duration, success ? null : "local push failed");
    }
  }

  /**
   * 批量向多个用户推送相同消息。
   *
   * <p>对每个用户逐一调用 {@link #pushToUser(String, String, Object)}， 适用于通知、公告等批量推送场景。
   *
   * @param userIds 用户 ID 列表
   * @param type 消息类型标签
   * @param payload 消息内容
   */
  @Override
  public void batchPushToUsers(List<String> userIds, String type, Object payload) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    for (String userId : userIds) {
      try {
        pushToUser(userId, type, payload);
      } catch (Exception e) {
        log.warn("[WebSocket] 批量推送单个用户失败: userId={}, err={}", userId, e.getMessage());
      }
    }
  }

  /**
   * 批量向多个用户推送相同消息（带离线补偿）。
   *
   * @param userIds 用户 ID 列表
   * @param type 消息类型标签
   * @param payload 消息内容
   */
  @Override
  public void batchPushToUsersWithOffline(List<String> userIds, String type, Object payload) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    for (String userId : userIds) {
      try {
        pushToUserWithOffline(userId, type, payload);
      } catch (Exception e) {
        log.warn("[WebSocket] 批量推送(离线补偿)单个用户失败: userId={}, err={}", userId, e.getMessage());
      }
    }
  }

  /**
   * 向指定用户推送通知并返回推送结果。
   *
   * @param userId 用户 ID
   * @param type 消息类型标签
   * @param payload 消息内容
   * @return 推送结果
   */
  @Override
  public PushResult pushToUserWithResult(String userId, String type, Object payload) {
    if (userId == null) {
      return PushResult.failure(null, "INVALID_PARAM", "userId cannot be null");
    }
    try {
      String messageId = generateMessageId();
      pushToUserWithMessageId(userId, type, payload, messageId);
      return PushResult.success(messageId);
    } catch (Exception e) {
      log.warn("[WebSocket] 推送失败: userId={}, err={}", userId, e.getMessage());
      return PushResult.failure(null, "PUSH_EXCEPTION", e.getMessage());
    }
  }

  /**
   * 向指定用户推送通知并返回结果（带业务级消息 ID + 离线补偿）。
   *
   * @param userId 用户 ID
   * @param type 消息类型标签
   * @param payload 消息内容
   * @param messageId 业务级消息唯一 ID
   * @return 推送结果
   */
  @Override
  public PushResult pushToUserOfflineResult(
      String userId, String type, Object payload, String messageId) {
    if (userId == null) {
      return PushResult.failure(null, "INVALID_PARAM", "userId cannot be null");
    }
    String actualMessageId =
        (messageId != null && !messageId.isEmpty()) ? messageId : generateMessageId();
    try {
      if (onlineUserService.isOnline(userId)) {
        pushToUserWithMessageId(userId, type, payload, actualMessageId);
      } else {
        offlineMessageStore.cacheOffline(userId, type, payload);
        log.info(
            "[WebSocket] 用户离线，消息已缓存: userId={}, type={}, messageId={}",
            userId,
            type,
            actualMessageId);
      }
      return PushResult.success(actualMessageId);
    } catch (Exception e) {
      log.warn("[WebSocket] 推送(离线补偿)失败: userId={}, err={}", userId, e.getMessage());
      return PushResult.failure(actualMessageId, "PUSH_EXCEPTION", e.getMessage());
    }
  }

  /**
   * 刷新重试队列（由定时任务调用）。
   *
   * <p>从重试队列中取出到期的重试消息，最多 100 条， 重新尝试推送。重试成功后标记为成功，失败后重试次数+1（最大 3 次）， 超过最大重试次数则标记为失败。
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
   * 应用消息过滤器链。
   *
   * <p>按顺序调用所有过滤器（基于 {@link PushContext}），任一过滤器返回 false 则拦截消息。
   *
   * @param context 推送上下文（含 userId / pushType / payload / priority 等）
   * @return 是否通过所有过滤器
   */
  private boolean applyFilters(PushContext context) {
    for (MessageFilter filter : messageFilters) {
      if (!filter.shouldSend(context)) {
        log.info(
            "[WebSocket] 消息被过滤器拦截: filter={}, userId={}, pushType={}",
            filter.getName(),
            context.userId(),
            context.pushType());
        return false;
      }
    }
    return true;
  }

  /**
   * 本地降级推送到指定用户。
   *
   * <p>当集群广播失败时降级为本地 STOMP 推送， 仅能推送到当前实例的 WebSocket 连接。
   *
   * @param userId 用户 ID
   * @param payloadJson 序列化后的消息 JSON
   * @return 是否推送成功
   */
  private boolean localPushToUser(String userId, String payloadJson) {
    try {
      String destination =
          WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
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
   * <p>当集群广播失败时降级为本地 STOMP 广播， 仅能推送到当前实例的 WebSocket 连接。
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
   * <p>当集群广播失败时降级为本地 STOMP 主题推送， 仅能推送到当前实例的 WebSocket 连接。
   *
   * @param topic 主题名称
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
   * @param messageId 消息 ID
   * @param userId 用户 ID
   * @param type 消息类型
   * @param payloadJson 序列化后的消息 JSON
   */
  private void enqueueRetry(String messageId, String userId, String type, String payloadJson) {
    if (retryQueue == null) {
      return;
    }
    try {
      RetryableMessage retryMsg =
          RetryableMessage.forUser(messageId, userId, type, payloadJson, 3, 5000);
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
   * <p>在原始 payload 对象外层包裹 TTL 字段（返回 Map 而非 JSON 字符串， 确保后续序列化仅执行一次，避免双重序列化导致的 JSON 转义问题）：
   *
   * <pre>
   * {
   *   "_ttlSeconds": N,
   *   "_expireAt": timestamp,
   *   "data": originalPayloadObject
   * }
   * </pre>
   *
   * @param payload 原始消息负载对象
   * @param ttlSeconds 有效期（秒）
   * @return 包装后的 Map 对象（ttlSeconds ≤ 0 时返回原始 payload）
   */
  private Object wrapWithTtl(Object payload, long ttlSeconds) {
    if (ttlSeconds <= 0) {
      return payload;
    }
    return Map.of(
        "_ttlSeconds",
        ttlSeconds,
        "_expireAt",
        System.currentTimeMillis() + ttlSeconds * 1000,
        "data",
        payload);
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
