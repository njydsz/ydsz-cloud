package com.njydsz.pmis.notification.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息服务 Feign Fallback
 *
 * <p>消息服务不可用时返回安全降级结果：邮件通道视为失败（不影响站内消息持久化）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageServiceClientFallback implements FallbackFactory<MessageServiceClient> {

    @Override
    public MessageServiceClient create(Throwable cause) {
        log.warn("[Feign] message 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new MessageServiceClient() {
            @Override
            public Result<Object> send(MessageFeignDTO dto) {
                return Result.failed(503, "消息服务暂不可用, 邮件未发送: receiver=" + dto.getReceiver());
            }

            @Override
            public Result<List<String>> channels() {
                return Result.ok(List.of());
            }
        };
    }
}
