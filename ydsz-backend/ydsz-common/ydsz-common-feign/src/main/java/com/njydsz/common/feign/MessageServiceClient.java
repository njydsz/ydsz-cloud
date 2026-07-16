package com.njydsz.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.fallback.MessageServiceClientFallbackFactory;

/**
 * 消息服务 Feign 客户端（兼容旧 com.njydsz.common.feign.MessageServiceClient）。
 *
 * <p>调用 message 服务提供的消息发送接口，支持多通道（站内信/邮件/Webhook/短信）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.MESSAGE, contextId = "messageServiceClient",
        fallbackFactory = MessageServiceClientFallbackFactory.class)
public interface MessageServiceClient {

    /**
     * 发送消息。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_SEND)
    BaseResponse<MessageResult> send(@RequestBody MessageRequest request);
}
