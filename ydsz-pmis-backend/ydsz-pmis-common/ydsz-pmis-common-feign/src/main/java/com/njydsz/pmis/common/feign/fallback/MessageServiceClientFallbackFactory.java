package com.njydsz.pmis.common.feign.fallback;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.feign.MessageServiceClient;

/**
 * MessageServiceClient 降级工厂。
 *
 * <p>当 message 服务不可用时，消息发送降级为记录日志并返回失败响应，
 * 调用方可根据返回的 error code 决定是否重试或降级处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class MessageServiceClientFallbackFactory extends DefaultFallbackFactory<MessageServiceClient> {

    @Override
    protected MessageServiceClient createFallback(Throwable cause) {
        return new MessageServiceClient() {
            @Override
            public BaseResponse<MessageResult> send(MessageRequest request) {
                log.warn("MessageServiceClient.send 降级: bizId={}, cause={}",
                        request.getBizId(), cause.getMessage());
                return BaseResponse.error("B01004", "消息服务暂时不可用");
            }
        };
    }
}
