package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
            public Result<Map<String, Object>> recomputeBillableUtilization(String period, boolean recomputeAll) {
                Map<String, Object> data = new HashMap<>();
                data.put("ok", false);
                data.put("period", period);
                data.put("recomputeAll", recomputeAll);
                data.put("error", "execution 模块不可用");
                data.put("source", "FALLBACK");
                return Result.ok(data);
            }

            @Override
            public Result<Map<String, Object>> snapshotAverage(String period) {
                Map<String, Object> data = new HashMap<>();
                data.put("avg_pct", 0);
                data.put("sum_total", 0);
                data.put("sum_billable", 0);
                data.put("sum_bench", 0);
                data.put("headcount", 0);
                data.put("source", "DOWN");
                data.put("period", period);
                return Result.ok(data);
            }
        };
    }
}
