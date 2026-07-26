package com.njydsz.project.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.exception.custom.SysException;

/**
 * RateCardClient 降级工厂。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class RateCardClientFallbackFactory implements FallbackFactory<RateCardClient> {

    @Override
    public RateCardClient create(Throwable cause) {
        log.error("[Feign降级] RateCardClient 调用失败", cause);
        return new RateCardClient() {
            @Override
            public Result<?> getById(String id) {
                throw new SysException("项目服务暂不可用，请稍后重试");
            }
        };
    }
}
