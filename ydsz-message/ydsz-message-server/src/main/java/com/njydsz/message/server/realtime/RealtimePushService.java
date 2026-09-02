package com.njydsz.message.server.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.socket.push.RealtimePushTemplate;

/**
 * 实时推送服务（业务门面，委托给 common-socket 的 {@link RealtimePushTemplate}）。
 *
 * <p>P1.3.0 重构：底层推送逻辑（STOMP + Redis Pub/Sub 集群广播 + 降级 + 离线补偿 + 指标监控） 已上迁到 {@code
 * ydsz-common-socket} 模块的 {@link RealtimePushTemplate}， 本类保留为业务门面，确保现有调用方无需修改注入路径。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePushService {

  private final RealtimePushTemplate realtimePushTemplate;

  /**
   * 向指定用户推送通知（集群广播）。
   *
   * @param userId 用户 ID
   * @param type 消息类型（NOTIFICATION/ALERT/DASHBOARD 等）
   * @param payload 消息内容
   */
  public void pushToUser(String userId, String type, Object payload) {
    realtimePushTemplate.pushToUser(userId, type, payload);
  }

  /**
   * 向指定用户推送通知，离线时缓存到 Redis 等待补偿。
   *
   * @param userId 用户 ID
   * @param type 消息类型标签
   * @param payload 消息内容
   */
  public void pushToUserWithOffline(String userId, String type, Object payload) {
    realtimePushTemplate.pushToUserWithOffline(userId, type, payload);
  }

  /**
   * 向所有在线用户广播消息。
   *
   * @param payload 消息内容
   */
  public void broadcast(Object payload) {
    realtimePushTemplate.broadcast(payload);
  }

  /**
   * 向所有在线用户广播消息（带类型标签）。
   *
   * @param type 消息类型标签（如 BROADCAST / ALERT）
   * @param payload 消息内容
   */
  public void broadcast(String type, Object payload) {
    realtimePushTemplate.broadcast(type, payload);
  }

  /**
   * 向指定主题推送消息。
   *
   * @param topic 主题路径
   * @param payload 消息内容
   */
  public void pushToTopic(String topic, Object payload) {
    realtimePushTemplate.pushToTopic(topic, payload);
  }
}
