package com.njydsz.pmis.cronjob.server.handler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.common.json.YdszJson;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.job.JobHandler;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.api.client.ExecutionClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 可计费利用率定时任务处理器
 *
 * <p>每日凌晨 02:30 触发，调用 ydsz-pmis-project 的
 * {@code POST /execution/billable-utilization/recompute?period=yyyy-MM} 接口，
 * 由项目业务模块内部聚合 pmis_execution_time_entry 并写入快照表
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("billableUtilizationJobHandler")
@RequiredArgsConstructor
public class BillableUtilizationJobHandler implements JobHandler {

    /** 执行模块 Feign 客户端 */
    private final ExecutionClient executionClient;

    /**
     * 执行可计费利用率快照重算
     *
     * @param paramsJson 参数 JSON，可包含 period（周期，默认上一月）和 recomputeAll（是否强制重算）
     * @return 执行结果，包含 period/recomputeAll/ok/costMs 等字段
     * @throws Exception 当执行过程中发生异常时抛出
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        String period = null;
        boolean recomputeAll = false;

        if (paramsJson != null && !paramsJson.isBlank()) {
            try {
                Map<String, Object> obj = YdszJson.parseMap(paramsJson);
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

        Map<String, Object> R = new HashMap<>();
        R.put("period", period);
        R.put("recomputeAll", recomputeAll);
        try {
            BaseResponse<Map<String, Object>> r =
                    executionClient.recomputeBillableUtilization(period, recomputeAll);
            if (r != null && r.getData() != null) {
                R.putAll(r.getData());
            }
            R.put("ok", true);
            R.put("costMs", System.currentTimeMillis() - start);
            return R;
        } catch (Exception e) {
            log.error("[BillableUtilization] 调用 execution 重算失败: {}", e.getMessage(), e);
            // Feign fallback 已经返回 ok=false；这里捕获异常避免调度框架认为任务失败
            R.put("ok", false);
            R.put("error", e.getMessage());
            return R;
        }
    }
}
