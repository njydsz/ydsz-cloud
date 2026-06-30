package com.njydsz.pmis.execution.job;

import com.njydsz.pmis.execution.service.AlertDispatchService;
import com.njydsz.pmis.scheduler.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 预警重试 Job（P5-2 定时补偿）
 *
 * <p>每 5 分钟扫描 PENDING/FAILED 预警并尝试重新分发；超过 maxRetry 后保持 FAILED。
 *
 * <p>Job 配置示例：
 * <pre>
 *   job_key:   alertDispatchRetryJob
 *   handler:   alertDispatchRetryJobHandler
 *   cron:      0 0/5 * * * ?
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component("alertDispatchRetryJobHandler")
@RequiredArgsConstructor
public class AlertDispatchRetryJobHandler implements JobHandler {

    private final AlertDispatchService alertDispatchService;

    private static final int DEFAULT_MAX_RETRY = 3;

    @Override
    public Object execute(String paramsJson) {
        long start = System.currentTimeMillis();
        int maxRetry = parseMaxRetry(paramsJson);
        int sent = alertDispatchService.retryFailed(maxRetry);
        long cost = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("retried", sent);
        result.put("maxRetry", maxRetry);
        result.put("costMs", cost);
        log.info("[AlertDispatchRetryJob] 重试完成: sent={} maxRetry={} costMs={}", sent, maxRetry, cost);
        return result;
    }

    private int parseMaxRetry(String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return DEFAULT_MAX_RETRY;
        }
        try {
            // 简化：支持 "{\"maxRetry\":5}" 或 "maxRetry=5"
            String s = paramsJson.replaceAll("[{}\" ]", "");
            for (String kv : s.split(",")) {
                String[] pair = kv.split("[:=]");
                if (pair.length == 2 && "maxRetry".equalsIgnoreCase(pair[0])) {
                    return Integer.parseInt(pair[1]);
                }
            }
        } catch (Exception e) {
            log.debug("[AlertDispatchRetryJob] 参数解析失败, 使用默认 maxRetry={}", DEFAULT_MAX_RETRY);
        }
        return DEFAULT_MAX_RETRY;
    }
}
