package com.njydsz.common.socket.push;

import java.util.List;

/**
 * 实时推送模板接口。
 *
 * <p>面向业务模块的统一推送入口抽象，提供用户单播、广播、主题推送、离线补偿、
 * 优先级、TTL、批量推送等能力。默认实现为 {@link DefaultRealtimePushTemplate}
 * （STOMP + Redis Pub/Sub 集群广播 + 降级 + 离线补偿 + 全链路增强）。
 *
 * <p><b>使用约定：</b>
 *
 * <ul>
 *   <li>业务模块通过 Spring 注入 {@link RealtimePushTemplate} Bean 使用，禁止直接依赖 STOMP/Redis 细节</li>
 *   <li>common-socket 模块未装配时（无默认实现 Bean），业务方可通过
 *       {@code ObjectProvider<RealtimePushTemplate>} 可选注入并降级为 no-op</li>
 *   <li>所有推送方法内部保证不抛出异常：失败降级为离线缓存或丢弃，并记录日志</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RealtimePushTemplate {

  /**
   * 推送消息到指定用户。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签（如 NOTIFICATION / BATCH_PROGRESS）
   * @param payload 消息内容（任意可序列化对象）
   */
  void pushToUser(String userId, String type, Object payload);

  /**
   * 推送消息到指定用户（带业务级消息 ID）。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param messageId 业务级消息唯一 ID（幂等去重）
   */
  void pushToUserWithMessageId(String userId, String type, Object payload, String messageId);

  /**
   * 推送消息到指定用户（指定优先级）。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param priority 消息优先级（{@code MessagePriority} 枚举名）
   */
  void pushToUser(String userId, String type, Object payload, String priority);

  /**
   * 推送消息到指定用户，失败时缓存到离线消息存储（带消息 ID）。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param messageId 业务级消息唯一 ID
   */
  void pushToUserWithOffline(String userId, String type, Object payload, String messageId);

  /**
   * 推送消息到指定用户，失败时缓存到离线消息存储。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   */
  void pushToUserWithOffline(String userId, String type, Object payload);

  /**
   * 广播消息给所有在线用户。
   *
   * @param payload 消息内容
   */
  void broadcast(Object payload);

  /**
   * 广播消息给所有在线用户（带类型标签）。
   *
   * @param type 业务类型标签
   * @param payload 消息内容
   */
  void broadcast(String type, Object payload);

  /**
   * 广播消息给所有在线用户（带类型标签和消息 ID）。
   *
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param messageId 业务级消息唯一 ID
   */
  void broadcast(String type, Object payload, String messageId);

  /**
   * 推送消息到指定主题。
   *
   * @param topic 主题路径
   * @param payload 消息内容
   */
  void pushToTopic(String topic, Object payload);

  /**
   * 推送带 TTL 的消息到指定用户。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param ttlSeconds 消息过期时间（秒），过期后不再投递
   */
  void pushToUserWithTtl(String userId, String type, Object payload, long ttlSeconds);

  /**
   * 批量推送消息给多个用户。
   *
   * @param userIds 目标用户 ID 列表
   * @param type 业务类型标签
   * @param payload 消息内容
   */
  void batchPushToUsers(List<String> userIds, String type, Object payload);

  /**
   * 批量推送消息给多个用户，失败时缓存到离线消息存储。
   *
   * @param userIds 目标用户 ID 列表
   * @param type 业务类型标签
   * @param payload 消息内容
   */
  void batchPushToUsersWithOffline(List<String> userIds, String type, Object payload);

  /**
   * 向指定用户推送消息并返回推送结果。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @return 推送结果（含消息 ID / 错误码）
   */
  PushResult pushToUserWithResult(String userId, String type, Object payload);

  /**
   * 向指定用户推送消息并返回推送结果（带消息 ID + 离线补偿）。
   *
   * @param userId 目标用户 ID
   * @param type 业务类型标签
   * @param payload 消息内容
   * @param messageId 业务级消息唯一 ID
   * @return 推送结果（含消息 ID / 错误码）
   */
  PushResult pushToUserOfflineResult(String userId, String type, Object payload, String messageId);

  /**
   * 立即重试积压的待重试消息。
   *
   * <p>由调度器或运维入口触发，将重试队列中的消息重新投递。
   */
  void flushRetryMessages();
}
