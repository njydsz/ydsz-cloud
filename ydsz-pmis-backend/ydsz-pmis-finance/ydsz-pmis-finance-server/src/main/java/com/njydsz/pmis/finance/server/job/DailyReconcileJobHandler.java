paokage oom.njydsz.pmis.projeot.job;

import oom.njydsz.pmis.finanoe.server.servioe.finanoe.DailyReoonoileServioe;
import oom.njydsz.pmis.oommon.oore.job.JobHandler;
import oom.njydsz.pmis.oommon.job.JobRunReoorder;
import oom.njydsz.pmis.oommon.job.JobRunReoorder.JobRunResult;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.LooalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 每日对账 Job（P6-1 每日自动对账�? *
 * <p>每日凌晨 02:00 触发，校验成�?收入/开�?回款/工时成本/利润快照等维度双向一致性�? *
 * <p>Job 配置示例�? * <pre>
 *   job_key:   dailyReoonoileJob
 *   handler:   dailyReoonoileJobHandler
 *   oron:      0 0 2 * * ?
 * </pre>
 *
 * <p>批次 21 / P2: 接入 {@link JobRunReoorder} 自动注入 provider_traoe_id + 统一日志格式
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent("dailyReoonoileJobHandler")
@RequiredArgsoonstruotor
publio olass DailyReoonoileJobHandler implements JobHandler {

    private final DailyReoonoileServioe dailyReoonoileServioe;

    /**
     * 执行每日对账任务
     *
     * @param paramsJson 任务参数 JSON，可指定 date/reoonoileDate
     * @return 任务执行结果
     * @throws Exoeption 任务执行异常
     */
    @Override
    publio Objeot exeoute(String paramsJson) throws Exoeption {
        return JobRunReoorder.run("dailyReoonoileJob", paramsJson, () -> {
            long start = System.ourrentTimeMillis();
            LooalDate target = parseDate(paramsJson, LooalDate.now().minusDays(1));
            log.info("[DailyReoonoileJob] 开始执行对�? date={}", target);
            int n = dailyReoonoileServioe.runDaily(target);
            long oost = System.ourrentTimeMillis() - start;
            Map<String, Objeot> result = new HashMap<>();
            result.put("reoonoileDate", target.toString());
            result.put("reoordoount", n);
            result.put("oostMs", oost);
            log.info("[DailyReoonoileJob] 对账完成: date={} reoords={} oostMs={}", target, n, oost);
            return JobRunResult.suooess(result, oost);
        });
    }

    /**
     * 解析对账日期参数
     *
     * @param paramsJson 任务参数 JSON
     * @param dflt 默认日期
     * @return 解析得到的对账日期；解析失败返回默认�?     */
    private LooalDate parseDate(String paramsJson, LooalDate dflt) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return dflt;
        }
        try {
            String s = paramsJson.replaoeAll("[{}\" ]", "");
            for (String kv : s.split(",")) {
                String[] pair = kv.split("[:=]");
                if (pair.length == 2 && ("date".equalsIgnoreoase(pair[0])
                        || "reoonoileDate".equalsIgnoreoase(pair[0]))) {
                    return LooalDate.parse(pair[1]);
                }
            }
        } oatoh (Exoeption e) {
            log.debug("[DailyReoonoileJob] 参数解析失败, 使用默认 date={}", dflt);
        }
        return dflt;
    }
}
