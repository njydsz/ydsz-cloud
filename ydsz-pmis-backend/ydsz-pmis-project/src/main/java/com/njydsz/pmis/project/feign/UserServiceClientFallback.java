package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用户服务降级工厂
 *
 * <p>iam 服务不可用时返回 503 / 零费率 / 空映射，避免 NameAssembler / 成本计算等场景级联失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements FallbackFactory<UserServiceClient> {

    /**
     * 创建降级客户端实例
     *
     * @param cause 触发降级的异常
     * @return 降级后的 UserServiceClient 实例
     */
    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new UserServiceClient() {
            @Override
            public Result<Map<String, Object>> getEmployee(Long id) {
                return Result.failed(503, "用户服务暂不可用");
            }

            @Override
            public Result<String> getCustomerName(Long customerId) {
                return Result.ok("");
            }

            @Override
            public Result<Map<Long, String>> batchEmployeeName(List<Long> ids) {
                return Result.ok(Map.of());
            }

            @Override
            public Result<BigDecimal> getLevelRate(String levelCode) {
                return Result.ok(BigDecimal.ZERO);
            }
        };
    }
}
