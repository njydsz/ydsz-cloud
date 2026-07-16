package com.njydsz.userinfo.api.fallback;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.api.client.BenchResourceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * BenchResourceClient 降级工厂
 *
 * <p>user 服务不可用时：
 * <ul>
 *   <li>getBenchDashboard → 返回空 map + source=DOWN</li>
 *   <li>listResourceAssignmentsByInitiation → 返回空列表 + isSuccess=true</li>
 * </ul>
 * 任何降级均不影响 execution 报表主流程。
 *
 * @author ydsz-team
 */
@Slf4j
@Component
public class BenchResourceClientFallback implements FallbackFactory<BenchResourceClient> {

    /**
     * 创建降级客户端实例
     *
     * @param cause 触发降级的异常
     * @return 降级后的 BenchResourceClient 实例
     */
    @Override
    public BenchResourceClient create(Throwable cause) {
        log.warn("[BenchResourceClientFallback] 触发降级：{}",
                cause == null ? "unknown" : cause.toString());
        return new BenchResourceClient() {
            @Override
            public BaseResponse<Map<String, Object>> getBenchDashboard() {
                Map<String, Object> data = new HashMap<>();
                data.put("source", "DOWN");
                data.put("activePools", Collections.emptyList());
                data.put("totalIdleCost", BigDecimal.ZERO);
                return BaseResponse.ok(data);
            }

            @Override
            public BaseResponse<List<Map<String, Object>>> listResourceAssignmentsByInitiation(String initiationId) {
                return BaseResponse.ok(Collections.emptyList());
            }
        };
    }
}
