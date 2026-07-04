package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * MessageFeignClient 降级工厂。
 *
 * <p>消息服务不可用时返回 SERVICE_UNAVAILABLE 占位结果，调用方主流程不受影响。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MessageFeignClientFallbackFactory implements FallbackFactory<MessageFeignClient> {

    @Override
    public MessageFeignClient create(Throwable cause) {
        log.warn("[MessageFeignClient] Feign fallback triggered: {}",
                cause == null ? "null" : cause.getMessage());
        return dto -> Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
    }
}
