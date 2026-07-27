package com.njydsz.project.api.fallback;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.api.client.RateCardClient;

import lombok.extern.slf4j.Slf4j;

/**
 * RateCardClient 降级工厂。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RateCardClientFallback implements FallbackFactory<RateCardClient> {

    @Override
    public RateCardClient create(Throwable cause) {
        log.warn("[RateCardClient] 降级触发：{}", cause.getMessage());
        return new RateCardClient() {
            @Override
            public BaseResponse<Map<String, Object>> getById(String id) {
                return BaseResponse.error("费率卡服务不可用");
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listAll() {
                return BaseResponse.error("费率卡服务不可用");
            }
        };
    }
}
