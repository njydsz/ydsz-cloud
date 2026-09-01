package com.njydsz.common.notify.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;

/**
 * 统一消息通知服务接口
 *
 * <p>支持邮件、企业微信、钉钉、飞书等全渠道消息发送。 核心入口为 {@link #send(NotifyRequest)}，其他方法为其便捷封装。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NotifyService {

  /**
   * 发送完整通知请求（统一入口）
   *
   * <p>支持模板、优先级、用户偏好、去重等高级特性。 其他发送方法最终均委托此方法执行。
   *
   * @param request 通知请求
   * @return 发送结果
   */
  NotifySendResult send(NotifyRequest request);

  /**
   * 发送通知消息到指定渠道
   *
   * @param channel 通知渠道
   * @param receiver 接收者标识（如邮箱地址、手机号、用户ID等）
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  default NotifySendResult send(
      NotifyChannel channel, String receiver, String title, String content) {
    NotifyRequest request =
        NotifyRequest.of(channel, receiver, title, content)
            .priority(NotifyPriority.P2_NORMAL)
            .build();
    return send(request);
  }

  /**
   * 发送通知消息到指定渠道（带模板参数）
   *
   * @param channel 通知渠道
   * @param receiver 接收者标识
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   * @return 发送结果
   */
  default NotifySendResult sendTemplate(
      NotifyChannel channel, String receiver, String templateCode, Object templateParams) {
    NotifyRequest request =
        NotifyRequest.of(channel, receiver, null, null)
            .template(templateCode, templateParams)
            .priority(NotifyPriority.P2_NORMAL)
            .build();
    return send(request);
  }

  /**
   * 批量发送通知消息到多个接收者（串行模式）
   *
   * @param channel 通知渠道
   * @param receivers 接收者标识列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 发送结果
   */
  NotifySendResult batchSend(
      NotifyChannel channel, List<String> receivers, String title, String content);

  /**
   * 并行批量发送通知消息到多个接收者
   *
   * <p>使用虚拟线程池并行发送，显著提升大批量发送场景的吞吐量。
   *
   * @param channel 通知渠道
   * @param receivers 接收者标识列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 异步发送结果，包含成功/失败统计
   */
  CompletableFuture<NotifySendResult> parallelBatchSend(
      NotifyChannel channel, List<String> receivers, String title, String content);

  /**
   * 并行批量发送通知消息到多个接收者（返回结构化明细）
   *
   * <p>与 {@link #parallelBatchSend} 逻辑一致，但返回 {@link BatchSendResultDTO} 包含每个接收者的
   * 发送结果明细，便于业务方定位失败接收者并执行定向重试。
   *
   * @param channel 通知渠道
   * @param receivers 接收者标识列表
   * @param title 消息标题
   * @param content 消息内容
   * @return 异步批量发送结构化结果
   */
  CompletableFuture<BatchSendResultDTO> parallelBatchSendDetailed(
      NotifyChannel channel, List<String> receivers, String title, String content);
}
