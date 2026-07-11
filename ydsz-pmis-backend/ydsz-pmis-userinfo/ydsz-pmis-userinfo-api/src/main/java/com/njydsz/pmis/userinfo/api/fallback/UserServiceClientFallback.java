package com.njydsz.pmis.userinfo.api.fallback;
import com.njydsz.pmis.userinfo.api.client.UserServiceClient;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户服务统一降级工厂（P1 架构优化：合并 project + system 两个版本）。
 *
 * <p>userinfo 服务不可用时返回 503 / 零费率 / 空映射，避免 NameAssembler / 通知模块级联失败。
 *
 * @author ydsz-pmis-team
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
            public Result<Map<String, Object>> getEmployee(String id) {
                return Result.failed(BizErrorCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<String> getCustomerName(String customerId) {
                return Result.ok("");
            }

            @Override
            public Result<Map<String, String>> batchEmployeeName(List<String> ids) {
                return Result.ok(Map.of());
            }

            @Override
            public Result<Map<String, String>> batchCustomerName(List<String> customerIds) {
                log.warn("[UserServiceClientFallback] batchCustomerName 降级: ids={}", customerIds);
                return Result.ok(Map.of());
            }

            @Override
            public Result<BigDecimal> getLevelRate(String levelCode) {
                return Result.ok(BigDecimal.ZERO);
            }
        };
    }
}