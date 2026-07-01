package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
public class UserServiceClientFallback implements FallbackFactory<UserServiceClient> {

    @Override
    public UserServiceClient create(Throwable cause) {
        log.warn("[Feign] user 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new UserServiceClient() {
            @Override
            public R<Map<String, Object>> getEmployee(Long id) {
                return R.failed(503, "用户服务暂不可用");
            }

            @Override
            public R<BigDecimal> getLevelRate(String levelCode) {
                return R.ok(BigDecimal.ZERO);
            }
        };
    }
}
