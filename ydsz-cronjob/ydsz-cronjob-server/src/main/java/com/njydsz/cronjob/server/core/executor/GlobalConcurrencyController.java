package com.njydsz.cronjob.server.core.executor;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;

/**
 * P2-16: 全局并发控制（Redis 全局并发计数器）。
 *
 * <p>通过 Redis 原子计数器实现集群级别的全局并发控制，
 * 限制整个集群同时执行的任务总数，防止资源耗尽。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>任务派发前：{@link #tryAcquire} 原子递增全局并发计数器</li>
 *   <li>计数器超过 {@code maxGlobalConcurrent} 时拒绝派发（返回 false）</li>
 *   <li>任务执行完成：{@link #release} 原子递减计数器</li>
 * </ol>
 *
 * <h3>与租户级配额的关系</h3>
 * <ul>
 *   <li>租户级配额（{@link com.njydsz.cronjob.server.service.TenantQuotaService}）：
 *       按租户限制并发，防 noisy neighbor</li>
 *   <li>全局并发控制（本组件）：限制集群总并发，防资源耗尽</li>
 *   <li>两者互补，先检查全局再检查租户</li>
 * </ul>
 *
 * <h3>Redis Key 设计</h3>
 * <ul>
 *   <li>计数器：{@code ydsz:job:global:concurrent}（String 类型，INCR/DECR 原子操作）</li>
 *   <li>无 TTL（持久化），通过 release 保证最终一致</li>
 *   <li>异常场景：进程崩溃未 release 时，通过定期校准任务修正计数器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalConcurrencyController {

    private final RedisStringOps redisStringOps;
    private final CronjobProperties cronjobProperties;
    /** 节点发现策略（可选注入，用于动态获取在线节点数） */
    private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;

    /** 全局并发计数器 Redis key */
    private static final String GLOBAL_CONCURRENT_KEY = "ydsz:job:global:concurrent";

    /** 计数器校准锁 key（防止多节点同时校准） */
    private static final String CALIBRATION_LOCK_KEY = "ydsz:job:global:concurrent:calibration-lock";

    /**
     * 估算集群中的在线节点数。
     *
     * <p>优先通过 {@link NodeDiscoveryStrategy#getOnlineNodes()} 获取实际在线节点数，
     * 不可用时回退到配置值 {@code ydsz.cronjob.cluster.max-nodes}（默认 3）。
     *
     * @return 集群在线节点数（至少为 1）
     */
    private int estimateClusterNodeCount() {
        NodeDiscoveryStrategy discovery = nodeDiscoveryStrategyProvider.getIfAvailable();
        if (discovery != null) {
            try {
                int count = discovery.getOnlineNodes().size();
                if (count > 0) {
                    return count;
                }
            } catch (Exception e) {
                log.debug("[GlobalConcurrency] 节点发现异常, 回退到配置值: reason={}", e.getMessage());
            }
        }
        // 回退到配置值
        return Math.max(1, cronjobProperties.getCluster().getMaxNodes());
    }

    /**
     * 尝试获取全局并发配额。
     *
     * <p>原子递增全局并发计数器，如果递增后超过最大值则回滚并返回 false。
     * 全局并发上限 = 单节点并发 × 在线节点数（动态获取，回退到配置值）。
     *
     * @return true 获取成功；false 全局并发已满
     */
    public boolean tryAcquire() {
        int maxConcurrent = cronjobProperties.getExecutor().getMaxConcurrent();
        // 集群级并发 = 单节点并发 × 在线节点数（动态获取，不再硬编码）
        int nodeCount = estimateClusterNodeCount();
        int maxGlobal = Math.max(maxConcurrent, maxConcurrent * nodeCount);
        try {
            Long current = redisStringOps.incr(GLOBAL_CONCURRENT_KEY, 1);
            if (current == null) {
                return true; // Redis 异常时放行
            }
            if (current > maxGlobal) {
                // 超限，回滚
                redisStringOps.decr(GLOBAL_CONCURRENT_KEY, 1);
                log.debug("[GlobalConcurrency] 全局并发已满, 拒绝: current={} max={} nodes={}",
                        current, maxGlobal, nodeCount);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("[GlobalConcurrency] Redis 异常, 放行: reason={}", e.getMessage());
            return true;
        }
    }

    /**
     * 释放全局并发配额。
     *
     * <p>原子递减全局并发计数器。确保不会减到负数。
     */
    public void release() {
        try {
            long current = redisStringOps.decr(GLOBAL_CONCURRENT_KEY, 1);
            if (current < 0) {
                // 计数器为负，修正为 0
                redisStringOps.set(GLOBAL_CONCURRENT_KEY, "0");
                log.warn("[GlobalConcurrency] 计数器为负, 已修正为 0");
            }
        } catch (Exception e) {
            log.debug("[GlobalConcurrency] 释放失败(不影响主流程): reason={}", e.getMessage());
        }
    }

    /**
     * 获取当前全局并发数（供监控 API 使用）。
     *
     * @return 当前并发数；Redis 异常时返回 -1
     */
    public long getCurrentConcurrent() {
        try {
            String value = redisStringOps.get(GLOBAL_CONCURRENT_KEY);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取全局并发上限配置值。
     *
     * <p>动态计算：单节点并发 × 在线节点数（回退到配置值）。
     *
     * @return 最大并发数
     */
    public int getMaxGlobalConcurrent() {
        int maxConcurrent = cronjobProperties.getExecutor().getMaxConcurrent();
        return Math.max(maxConcurrent, maxConcurrent * estimateClusterNodeCount());
    }

    /**
     * 强制校准全局并发计数器。
     *
     * <p>由定时任务定期调用，通过查询 RUNNING 状态的日志数校准计数器。
     * 防止进程崩溃导致的计数器漂移。
     */
    public void calibrate(long actualRunningCount) {
        try {
            boolean acquired = redisStringOps.setIfAbsent(CALIBRATION_LOCK_KEY, "1", 30);
            if (!acquired) {
                return; // 其他节点正在校准
            }
            redisStringOps.set(GLOBAL_CONCURRENT_KEY, String.valueOf(actualRunningCount));
            log.info("[GlobalConcurrency] 计数器已校准: value={}", actualRunningCount);
        } catch (Exception e) {
            log.warn("[GlobalConcurrency] 校准失败: reason={}", e.getMessage());
        } finally {
            try {
                redisStringOps.delete(CALIBRATION_LOCK_KEY);
            } catch (Exception ignored) {
                log.debug("Caught exception (ignored): {}", ignored.getMessage());
            }
        }
    }
}
