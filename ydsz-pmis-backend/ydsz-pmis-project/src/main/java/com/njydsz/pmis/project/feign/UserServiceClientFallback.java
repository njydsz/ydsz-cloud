package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * UserServiceClient 降级工厂
 *
 * <p>当 user 服务不可用时返回空数据，避免业务被拖垮。
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
            public Result<Map<String, Object>> getEmployee(Long id) {
                return Result.ok(null);
            }

            @Override
            public Result<String> getCustomerName(Long customerId) {
                return Result.ok("");
            }

            @Override
            public Result<Map<Long, String>> batchEmployeeName(List<Long> ids) {
                return Result.ok(Map.of());
            }
        };
    }
}
