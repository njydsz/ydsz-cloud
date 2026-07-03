package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * @author ydsz-pmis-team
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
            public Result<Map<String, Object>> getBenchDashboard() {
                Map<String, Object> data = new HashMap<>();
                data.put("source", "DOWN");
                data.put("activePools", Collections.emptyList());
                data.put("totalIdleCost", BigDecimal.ZERO);
                return Result.ok(data);
            }

            @Override
            public Result<List<Map<String, Object>>> listResourceAssignmentsByInitiation(Long initiationId) {
                return Result.ok(Collections.emptyList());
            }
        };
    }
}
