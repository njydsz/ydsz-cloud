package com.njydsz.message.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.api.fallback.MessageSendClientFallback;

/**
 * 消息发送 Feign 客户端（供跨服务调用）。
 *
 * <p>提供消息发送的远程调用能力，支持多通道路由（邮件/短信/Webhook/站内信）。
 * 典型场景：工作流审批通知、定时任务执行结果告警、规则引擎触发动作等。
 *
 * <p>与 {@code common-feign/NotificationClient} 的区别：
 * <ul>
 *   <li>{@code NotificationClient} 定义在 common-feign 模块，使用 common-feign 的 DTO</li>
 *   <li>{@code MessageSendClient} 定义在 message-api 模块，可引用 message-domain 的 VO/DTO</li>
 *   <li>两者互补：common-feign 适合通用通知场景，message-api 适合需要消息领域对象的场景</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
/**
 * MessageSendClient Feign 客户端接口，声明跨服务远程调用。
 *
 * <p>所属包：{@code com.njydsz.message.api.client}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.MESSAGE, contextId = "messageSendClient",
        fallbackFactory = MessageSendClientFallback.class)

public interface MessageSendClient {

    /**
     * 发送多通道消息（邮件 / 短信 / Webhook / 站内信等）。
     *
     * @param request 消息请求
     * @return 发送结果（messageId + status）
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_SEND)
    BaseResponse<String> sendMessage(@RequestBody MessageRequest request);
}
