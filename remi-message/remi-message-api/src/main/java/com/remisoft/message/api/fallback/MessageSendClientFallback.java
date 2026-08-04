package com.remisoft.message.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.feign.MessageRequest;
import com.remisoft.message.api.client.MessageSendClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link MessageSendClient} 的 FallbackFactory。
 *
 * <p>消息中心服务不可用时降级返回 null，仅记录 WARN 日志，
 * 保证调用方主流程不受影响（消息发送是辅助功能，不应阻断业务）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageSendClientFallback implements FallbackFactory<MessageSendClient> {

    @Override
    public MessageSendClient create(Throwable cause) {
        log.warn("[MessageSendClient] 降级触发: {}", cause.getMessage());
        return new MessageSendClient() {
            @Override
            public BaseResponse<String> sendMessage(MessageRequest request) {
                log.warn("[MessageSendClient] sendMessage 降级: receiver={}, subject={}, reason=消息中心服务不可用",
                        request == null ? null : request.getReceiver(),
                        request == null ? null : request.getSubject());
                return BaseResponse.success(null);
            }
        };
    }
}
