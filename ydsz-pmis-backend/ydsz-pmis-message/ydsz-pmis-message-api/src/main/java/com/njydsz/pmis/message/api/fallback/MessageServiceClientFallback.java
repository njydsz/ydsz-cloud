package com.njydsz.pmis.message.api.fallback;
import com.njydsz.pmis.message.api.client.MessageServiceClient;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * MessageServiceClient fallback factory.
 *
 * <p>Returns degraded success (with FAILED status) when the message module is unavailable.
 * Ensures caller's main flow is not affected by message delivery failures.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageServiceClientFallback implements FallbackFactory<MessageServiceClient> {

    @Override
    public MessageServiceClient create(Throwable cause) {
        log.warn("[Feign] MessageServiceClient degraded: {}", cause == null ? "?" : cause.getMessage());
        return new MessageServiceClient() {
            @Override
            public Result<MessageResult> send(MessageRequest request) {
                if (request == null) {
                    return Result.ok(MessageResult.fail("UNKNOWN", "Degraded: empty request"));
                }
                log.warn("[Feign] Degraded send: bizType={} bizId={} channel={} template={}",
                        request.getBizType(), request.getBizId(),
                        request.getChannel(), request.getTemplateCode());
                MessageResult r = MessageResult.fail(request.getChannel(),
                        BizErrorCode.SERVICE_UNAVAILABLE.getMessage());
                return Result.ok(r);
            }
        };
    }
}
