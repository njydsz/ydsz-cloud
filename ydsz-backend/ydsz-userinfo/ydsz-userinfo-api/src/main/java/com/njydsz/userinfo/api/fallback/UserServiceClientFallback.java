package com.njydsz.userinfo.api.fallback;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.userinfo.api.client.UserServiceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户服务统一降级工厂（P1 架构优化：合并 project + system 两个版本）。
 *
 * <p>userinfo 服务不可用时返回 503 / 零费率 / 空映射，避免 NameAssembler / 通知模块级联失败。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new UserServiceClient() {
            @Override
            public BaseResponse<Map<String, Object>> getEmployee(String id) {
                return BaseResponse.error(BaseResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public BaseResponse<String> getCustomerName(String customerId) {
                return BaseResponse.success("");
            }

            @Override
            public BaseResponse<Map<String, String>> batchEmployeeName(List<String> ids) {
                return BaseResponse.success(Map.of());
            }

            @Override
            public BaseResponse<Map<String, String>> batchCustomerName(List<String> customerIds) {
                log.warn("[UserServiceClientFallback] batchCustomerName 降级: ids={}", customerIds);
                return BaseResponse.success(Map.of());
            }

            @Override
            public BaseResponse<BigDecimal> getLevelRate(String levelCode) {
                return BaseResponse.success(BigDecimal.ZERO);
            }
        };
    }
}