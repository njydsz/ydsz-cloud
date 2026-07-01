package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 降级策略：项目服务不可用时返回空快照（预算=0）。
 * <p>
 * 这会导致 {@code BudgetGuard} 跳过预算校验（安全降级），并记录告警日志，
 * 不会因为项目服务临时不可用而阻断业务流。
 * </p>
 */
@Slf4j
@Component
public class InitiationServiceClientFallback implements FallbackFactory<InitiationServiceClient> {

    @Override
    public InitiationServiceClient create(Throwable cause) {
        log.warn("[Feign] project 服务降级: {}", cause == null ? "?" : cause.getMessage());
        return new InitiationServiceClient() {
            @Override
            public R<Map<String, Object>> budgetSnapshot(Long id) {
                // 返回 code=503 + 空数据；BudgetGuard 将识别后跳过强管控
                return R.failed(503, "项目服务暂不可用，预算强管控已自动降级");
            }
        };
    }
}
