package com.njydsz.message.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.api.fallback.MessageSendClientFallback;

/**
 * 消息发送 Feign 客户端（供跨服务调用）。
 *
 * <p>提供消息发送的远程调用能力，支持多通道路由（邮件/短信/Webhook/站内信）。 典型场景：工作流审批通知、定时任务执行结果告警、规则引擎触发动作等。
 *
 * <p>与同模块 {@link NotificationClient} 的区别：
 *
 * <ul>
 *   <li>{@link NotificationClient} 使用 common-feign 的 DTO（{@code MessageRequest} 等）
 *   <li>{@code MessageSendClient} 可引用 message-domain 的 VO/DTO
 *   <li>两者互补：通用 DTO 场景使用 {@link NotificationClient}，需要消息领域对象的场景使用 {@code MessageSendClient}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
/**
 * MessageSendClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.message.api.client}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FeignClient(
    name = FeignClientConstants.MESSAGE,
    contextId = "messageSendClient",
    fallbackFactory = MessageSendClientFallback.class)
public interface MessageSendClient {

  /**
   * 发送多通道消息（邮件 / 短信 / Webhook / 站内信等）。
   *
   * @param request 消息请求
   * @return 发送结果（messageId + status）
   */
  @PostMapping(FeignClientConstants.MESSAGE_PATH_SEND)
  YdszResponse<String> sendMessage(@RequestBody MessageRequest request);
}
