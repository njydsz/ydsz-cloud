package com.njydsz.workflow.server.job;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.job.JobHandler;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.server.engine.FlowClusterLockHelper;
import com.njydsz.workflow.server.service.FlowThirdPartyRetryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 三方审批回调失败重试任务处理器
 *
 * <p>P0-4: 失败回调最终一致性保证。
 *
 * <p>调度入口（Bean 名称 = {@code flowThirdPartyRetryJobHandler}）：
 * <ul>
 *   <li>通过 ydsz_job 表配置：handler=flowThirdPartyRetryJobHandler, cron="0 0/10 * * * ?"（每 10 分钟扫描一次）</li>
 *   <li>paramsJson 可选参数：
 *     <ul>
 *       <li>{@code maxRetries} — 最大重试次数阈值，默认 3（超过则进入死信不再扫描）</li>
 *       <li>{@code batchSize} — 单批扫描条数，默认 50</li>
 *       <li>{@code lockLeaseSec} — 集群锁持有时间（秒），默认 120</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>集群幂等：通过 {@link FlowClusterLockHelper#tryRun} 包装，多节点部署时同一时刻只有一个节点执行。
 * 锁 key = {@code thirdPartyRetry:scan}，TTL 由 lockLeaseSec 控制。
 *
 * <p>容错策略：
 * <ul>
 *   <li>未获取锁（其他节点正在执行）→ 直接返回 skipped</li>
 *   <li>扫描异常 / 重试异常 → 返回 error，但不抛出（避免 JobHandler 框架标记任务失败）</li>
 *   <li>单条重试异常由 {@link FlowThirdPartyRetryService#retryBatch} 内部 try-catch，不影响其他条目</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component("flowThirdPartyRetryJobHandler")
@RequiredArgsConstructor
public class FlowThirdPartyRetryJobHandler implements JobHandler {

    /** 集群锁 key 前缀（与 FlowClusterLockHelper 的 LOCK_PREFIX 拼接） */
    private static final String LOCK_KEY = "thirdPartyRetry:scan";

    /** 三方回调重试服务 */
    private final FlowThirdPartyRetryService retryService;
    /** 集群锁辅助工具 */
    private final FlowClusterLockHelper clusterLockHelper;

    /** 默认最大重试次数（可被 paramsJson 或配置覆盖） */
    @Value("${ydsz.workflow.third-party.retry.max-retries:3}")
    private int defaultMaxRetries;

    /** 默认单批扫描条数 */
    @Value("${ydsz.workflow.third-party.retry.batch-size:50}")
    private int defaultBatchSize;

    /** 默认集群锁持有时间（秒） */
    @Value("${ydsz.workflow.third-party.retry.lock-lease-sec:120}")
    private int defaultLockLeaseSec;

    /**
     * 执行重试任务
     *
     * @param paramsJson 参数 JSON（可空），支持字段：maxRetries / batchSize / lockLeaseSec
     * @return 执行结果摘要：scanned / success / fail / skipped / errors / costMs / locked
     */
    @Override
    public Object execute(String paramsJson) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[ThirdPartyRetry] 开始扫描失败回调 params={}", paramsJson);

        // 解析参数
        int maxRetries = parseIntParam(paramsJson, "maxRetries", defaultMaxRetries);
        int batchSize = parseIntParam(paramsJson, "batchSize", defaultBatchSize);
        int lockLeaseSec = parseIntParam(paramsJson, "lockLeaseSec", defaultLockLeaseSec);

        // 集群幂等：尝试获取锁后执行
        FlowThirdPartyRetryService.RetryResult result = clusterLockHelper.tryRun(
                LOCK_KEY, lockLeaseSec,
                () -> retryService.retryBatch(maxRetries, batchSize));

        Map<String, Object> resp = new HashMap<>();
        if (result == null) {
            // 未获取锁（其他节点正在执行）
            log.info("[ThirdPartyRetry] 未获取集群锁，跳过本次执行: key={}", LOCK_KEY);
            resp.put("ok", true);
            resp.put("locked", false);
            resp.put("skipped", true);
            resp.put("costMs", System.currentTimeMillis() - start);
            return resp;
        }

        log.info("[ThirdPartyRetry] 执行完成: scanned={} success={} fail={} skipped={} errors={} costMs={}",
                result.scanned, result.success, result.fail, result.skipped, result.errors,
                System.currentTimeMillis() - start);

        resp.put("ok", result.errors == 0);
        resp.put("locked", true);
        resp.put("scanned", result.scanned);
        resp.put("success", result.success);
        resp.put("fail", result.fail);
        resp.put("skipped", result.skipped);
        resp.put("errors", result.errors);
        resp.put("costMs", System.currentTimeMillis() - start);
        return resp;
    }

    // ============================== 私有辅助 ==============================

    /**
     * 从 paramsJson 解析整型参数，解析失败或缺失时返回默认值
     *
     * @param paramsJson   参数 JSON
     * @param key          参数键
     * @param defaultValue 默认值
     * @return 解析值
     */
    private int parseIntParam(String paramsJson, String key, int defaultValue) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return defaultValue;
        }
        try {
            Map<String, Object> obj = YdszJson.parseMap(paramsJson);
            if (obj == null) {
                return defaultValue;
            }
            Object val = obj.get(key);
            if (val == null) {
                return defaultValue;
            }
            if (val instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            log.warn("[ThirdPartyRetry] 参数解析失败，使用默认值: key={} default={} err={}",
                    key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }
}
