package com.njydsz.pmis.execution.scheduler.handler;

import com.njydsz.pmis.common.feign.ExecutionClient;
import com.njydsz.pmis.common.job.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 可计费利用率定时任务处理器
 *
 * <p>每日凌晨 02:30 触发，调用 ydsz-pmis-execution 的
 * {@code /api/v1/execution/billable-utilization/recompute?period=yyyy-MM} 接口，
 * 由执行模块内部聚合 pmis_execution_time_entry 并写入快照表
 * pmis_billable_utilization_snapshot。
 *
 * <p>参数 JSON 格式：{@code {"period":"2026-06","recomputeAll":false}}
 * <ul>
 *   <li>period 不传 → 默认上一月</li>
 *   <li>recomputeAll=true → 强制清空 + 重算（运维手工触发）</li>
 * </ul>
 *
 * <p>Bean 名称 = {@code billableUtilizationJobHandler}，
 * 可在 pmis_job 表插入一条记录：handler=billableUtilizationJobHandler, cron="0 30 2 * * ?"
 *
 * <p>本类从 {@code ydsz-pmis-scheduler} 迁出至本模块，避免 scheduler→execution 的循环依赖
 * （execution 已依赖 scheduler）。由 Spring 在执行模块启动时扫描本 Bean，并按名称匹配 pmis_job.handler。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("billableUtilizationJobHandler")
@RequiredArgsConstructor
public class BillableUtilizationJobHandler implements JobHandler {

    private final ExecutionClient executionClient;

    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        String period = null;
        boolean recomputeAll = false;

        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                com.alibaba.fastjson2.JSONObject obj =
                        com.alibaba.fastjson2.JSON.parseObject(paramsJson);
                if (obj != null) {
                    period = obj.getString("period");
                    recomputeAll = Boolean.TRUE.equals(obj.getBoolean("recomputeAll"));
                }
            } catch (Exception e) {
                log.warn("[BillableUtilization] 参数 JSON 解析失败，按默认值处理: {}", e.getMessage());
            }
        }
        if (period == null || period.isBlank()) {
            period = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }

        log.info("[BillableUtilization] 触发快照重算 period={} recomputeAll={}", period, recomputeAll);

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("recomputeAll", recomputeAll);
        try {
            com.njydsz.pmis.common.api.Result<Map<String, Object>> r =
                    executionClient.recomputeBillableUtilization(period, recomputeAll);
            if (r != null && r.getData() != null) {
                result.putAll(r.getData());
            }
            result.put("ok", true);
            result.put("costMs", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[BillableUtilization] 调用 execution 重算失败: {}", e.getMessage(), e);
            // Feign fallback 已经返回 0；这里将异常转成 ok=false 但不抛出，避免重复调度被阻塞
            result.put("ok", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
