package com.njydsz.pmis.project.api.fallback;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.api.client.ExecutionClient;

import lombok.extern.slf4j.Slf4j;

/**
 * ExecutionClient 降级
 *
 * <p>execution 模块不可用时：recompute 返回 ok=false 让 cronjob 记录失败，
 * snapshotAverage 返回空 map + source=DOWN 让调用方走兜底逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ExecutionClientFallback implements FallbackFactory<ExecutionClient> {

    /**
     * 创建降级代理
     *
     * @param cause 触发降级的异常
     * @return ExecutionClient 降级实现
     */
    @Override
    public ExecutionClient create(Throwable cause) {
        log.warn("[ExecutionClientFallback] 触发降级：{}", cause == null ? "unknown" : cause.toString());
        return new ExecutionClient() {
            @Override
            public BaseResponse<Map<String, Object>> recomputeBillableUtilization(String period, boolean recomputeAll) {
                Map<String, Object> data = new HashMap<>();
                data.put("ok", false);
                data.put("period", period);
                data.put("recomputeAll", recomputeAll);
                data.put("error", "execution 模块不可用");
                data.put("source", "FALLBACK");
                return BaseResponse.ok(data);
            }

            @Override
            public BaseResponse<Map<String, Object>> snapshotAverage(String period) {
                Map<String, Object> data = new HashMap<>();
                data.put("avg_pct", 0);
                data.put("sum_total", 0);
                data.put("sum_billable", 0);
                data.put("sum_bench", 0);
                data.put("headcount", 0);
                data.put("source", "DOWN");
                data.put("period", period);
                return BaseResponse.ok(data);
            }
        };
    }
}
