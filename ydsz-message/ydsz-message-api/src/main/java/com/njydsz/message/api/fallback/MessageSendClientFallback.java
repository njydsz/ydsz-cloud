package com.njydsz.message.api.fallback;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.api.client.MessageSendClient;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link MessageSendClient} 的 FallbackFactory。
 *
 * <p>消息中心服务不可用时降级返回统一错误码
 * ({@link FeignClientConstants#FEIGN_SERVICE_UNAVAILABLE})，
 * 仅记录 WARN 日志，保证调用方主流程不受影响。
 *
 * <p>注意：必须返回 error 而非 success(null)，
 * 否则调用方通过 {@code isSuccess()} 检查会误判为发送成功。
 *
 * @author ydsz-team
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
                return BaseResponse.error(FeignClientConstants.FEIGN_SERVICE_UNAVAILABLE, "消息中心服务不可用");
            }
        };
    }
}
