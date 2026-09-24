package com.njydsz.message.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.api.fallback.MessageSendClientFallback;

/**
 * 消息发送 Feign 客户端接口，声明跨服务远程调用能力。
 *
 * <p>基于 OpenFeign 声明式 HTTP 客户端，提供多通道消息发送的远程调用入口
 * （邮件/短信/Webhook/站内信等）。典型场景：工作流审批通知、定时任务执行结果告警、
 * 规则引擎触发动作等。
 *
 * <p>通过 {@code fallbackFactory = MessageSendClientFallback.class} 实现熔断降级，
 * 当消息服务不可用时返回降级响应而非抛出异常。
 *
 * @author ydsz
 * @since 26.09.24
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
