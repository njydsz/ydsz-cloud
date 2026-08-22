package com.njydsz.workflow.domain.gateway;

import java.util.List;

/**
 * 通知发送客户端（外部依赖抽象接口）。
 *
 * <p>抽象通知中心的发送能力，domain 层通过本接口发送通知，
 * infra 层提供适配器实现（Feign 调用通知中心服务）。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>外部依赖抽象接口置于 {@code domain/gateway/} 包下、
 * 以 {@code Client} 结尾（符合 §34.2.1 表格：gateway/ 外部依赖抽象接口）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NotificationClient {

  /**
   * 发送单条通知。
   *
   * @param channel 通道（INAPP / DINGTALK / WECHAT / SMS / EMAIL）
   * @param receiverId 接收人 ID
   * @param title 标题
   * @param content 内容
   * @param bizType 业务类型
   * @param level 级别（INFO / WARN / URGENT）
   */
  void notify(String channel, String receiverId, String title, String content, String bizType, String level);

  /**
   * 批量发送通知。
   *
   * @param channel 通道
   * @param receiverIds 接收人 ID 列表
   * @param title 标题
   * @param content 内容
   * @param bizType 业务类型
   * @param level 级别
   */
  void notifyBatch(
      String channel,
      List<String> receiverIds,
      String title,
      String content,
      String bizType,
      String level);
}
