package com.njydsz.cronjob.server.handler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.object.YdszJsonObject;

import org.springframework.stereotype.Component;

import com.njydsz.common.job.JobHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 可计费利用率定时任务处理器
 *
 * <p>每日凌晨 02:30 触发，原通过 Feign 调用 ydsz-project 的
 * {@code POST /execution/billable-utilization/recompute?period=yyyy-MM} 接口触发重算。
 * Feign 契约下线后，此处理器待迁移至消息队列或同进程 Service 调用。
 *
 * <p>参数 JSON 格式：{@code {"period":"2026-06","recomputeAll":false}}
 * <ul>
 *   <li>period 不传 → 默认上一月</li>
 *   <li>recomputeAll=true → 强制清空 + 重算（运维手工触发）</li>
 * </ul>
 *
 * <p>Bean 名称 = {@code billableUtilizationJobHandler}，
 * 可在 ydsz_job 表插入一条记录：handler=billableUtilizationJobHandler, cron="0 30 2 * * ?"
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component("billableUtilizationJobHandler")
public class BillableUtilizationJobHandler implements JobHandler {

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
                YdszJsonObject obj = YdszJson.parseObjectToJsonObject(paramsJson);
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
        // TODO Feign 契约已下线：原通过 ExecutionClient.recomputeBillableUtilization 触发重算，
        //      后续应迁移至消息队列或同进程 Service 调用（project 模块的 BillableUtilizationService）
        log.warn("[BillableUtilization] Feign 契约已下线，快照重算待迁移: period={} recomputeAll={}", period, recomputeAll);
        R.put("ok", false);
        R.put("error", "Feign 契约已下线，快照重算待迁移至消息队列或同进程 Service 调用");
        R.put("costMs", System.currentTimeMillis() - start);
        return R;
    }
}
