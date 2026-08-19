package com.njydsz.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.dto.BroadcastRequestDTO;
import com.njydsz.common.feign.dto.PushRealtimeRequestDTO;
import com.njydsz.common.feign.dto.RealtimePushDTO;
import com.njydsz.common.feign.fallback.NotificationClientFallbackFactory;

/**
 * 通知中心 Feign 客户端（通用通知能力）。
 *
 * <p>提供跨服务消息通知的统一入口，封装多通道路由（邮件/短信/Webhook/站内信/实时推送）。 与 {@code MessageSendClient}（message-api
 * 模块）的区别：
 *
 * <ul>
 *   <li>{@code NotificationClient} 定义在 common-feign 模块，使用 common-feign 的 DTO（{@link
 *       MessageRequest}）
 *   <li>{@code MessageSendClient} 定义在 message-api 模块，可引用 message-domain 的 VO/DTO
 *   <li>两者互补：common-feign 适合通用通知场景，message-api 适合需要消息领域对象的场景
 * </ul>
 *
 * <p>使用场景：
 *
 * <ul>
 *   <li>工作流审批通知
 *   <li>定时任务执行结果告警
 *   <li>规则引擎触发通知
 * </ul>
 *
 * <p><b>P0-3-fix</b>：
 *
 * <ul>
 *   <li>{@link #broadcast(BroadcastRequestDTO)} 将 topic 并入请求体，返回 {@link YdszResponse} 使调用方可感知结果
 *   <li>新增 {@link #pushRealtime(String, String, RealtimePushDTO)} 单播实时推送方法
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
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
  YdszResponse<MessageResult> sendMessage(@RequestBody MessageRequest request);

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
   * @param userId 目标用户 ID
   * @param type 推送消息类型（如 "TODO_COUNT"、"TASK_ASSIGNED"）
   * @param payload 推送数据
   * @return 推送结果（成功时 traceId 可用于追踪）
   */
  @PostMapping(FeignClientConstants.MESSAGE_PATH_PUSH_REALTIME)
  YdszResponse<MessageResult> pushRealtime(@RequestBody PushRealtimeRequestDTO request);
}
