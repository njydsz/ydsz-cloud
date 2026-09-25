package com.njydsz.message.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.common.feign.dto.BroadcastRequestDTO;
import com.njydsz.common.feign.dto.PushRealtimeRequestDTO;
import com.njydsz.message.api.fallback.NotificationClientFallbackFactory;

/**
 * 通知中心 Feign 客户端（通用通知能力）。
 *
 * <p>提供跨服务消息通知的统一入口，封装多通道路由（邮件/短信/Webhook/站内信/实时推送）。
 * 支持单条消息发送（{@link #sendMessage}）、WebSocket/SSE 广播推送（{@link #broadcast}）、
 * 单播实时推送（{@link #pushRealtime}）三种远程调用方法。
 *
 * <p>使用场景：工作流审批通知、定时任务执行结果告警、规则引擎触发通知等。
 *
 * <p>降级策略：通过 {@code fallbackFactory = NotificationClientFallbackFactory.class}
 * 在消息服务不可用时返回降级响应，避免调用方阻塞。
 *
 * @author ydsz
 * @since 26.09.24
 * @see MessageSendClient message-api 的细粒度消息客户端
 */
@FeignClient(
    name = FeignClientConstants.MESSAGE,
    contextId = "notificationClient",
    fallbackFactory = NotificationClientFallbackFactory.class)
public interface NotificationClient {

  /**
   * 发送多通道消息通知。
   *
   * <p>消息中心根据 channel 字段自动路由到具体通道实现 （EMAIL/SMS/PUSH/INAPP/WEBHOOK 等）。
   *
   * @param request 消息请求（channel、receiver、subject、content 等）
   * @return 发送结果（包含 MessageResult 详细信息）
   */
  @PostMapping(FeignClientConstants.MESSAGE_PATH_SEND)
  YdszResponse<MessageResult> sendMessage(@RequestBody MessageSendDTO request);

  /**
   * 实时广播推送（WebSocket/SSE）。
   *
   * <p>将消息广播到当前租户的在线用户，不经过消息中心持久化。 topic 字段用于前端订阅过滤，messageId 用于幂等去重。
   *
   * @param request 广播请求（topic、data、可选 messageId）
   * @return 推送结果（成功时 traceId 可用于追踪）
   */
  @PostMapping(FeignClientConstants.MESSAGE_PATH_BROADCAST)
  YdszResponse<MessageResult> broadcast(@RequestBody BroadcastRequestDTO request);

  /**
   * 实时单播推送（WebSocket/SSE）。
   *
   * <p>将消息推送到指定用户的 WebSocket 连接，不经过消息中心持久化。 适用于工作流待办数推送、任务分配通知等场景。
   *
   * @param request 推送请求（含目标用户 ID、消息类型与推送数据）
   * @return 推送结果（成功时 traceId 可用于追踪）
   */
  @PostMapping(FeignClientConstants.MESSAGE_PATH_PUSH_REALTIME)
  YdszResponse<MessageResult> pushRealtime(@RequestBody PushRealtimeRequestDTO request);
}
