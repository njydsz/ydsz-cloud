package com.njydsz.cronjob.server.core.executor;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalConcurrencyController {

    private final StringRedisTemplate redisTemplate;
    private final CronjobProperties cronjobProperties;

    /** 全局并发计数器 Redis key */
    private static final String GLOBAL_CONCURRENT_KEY = "ydsz:job:global:concurrent";

    /** 计数器校准锁 key（防止多节点同时校准） */
    private static final String CALIBRATION_LOCK_KEY = "ydsz:job:global:concurrent:calibration-lock";

    /**
     * 尝试获取全局并发配额。
     *
     * <p>原子递增全局并发计数器，如果递增后超过最大值则回滚并返回 false。
     *
     * @return true 获取成功；false 全局并发已满
     */
    public boolean tryAcquire() {
        int maxConcurrent = cronjobProperties.getExecutor().getMaxConcurrent();
        // 集群级并发 = 单节点并发 × 节点数（估算），简化为配置值
        int maxGlobal = maxConcurrent * 3; // 假设最多 3 个节点
        try {
            Long current = redisTemplate.opsForValue().increment(GLOBAL_CONCURRENT_KEY);
            if (current == null) {
                return true; // Redis 异常时放行
            }
            if (current > maxGlobal) {
                // 超限，回滚
                redisTemplate.opsForValue().decrement(GLOBAL_CONCURRENT_KEY);
                log.debug("[GlobalConcurrency] 全局并发已满, 拒绝: current={} max={}",
                        current, maxGlobal);
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
            Long current = redisTemplate.opsForValue().decrement(GLOBAL_CONCURRENT_KEY);
            if (current != null && current < 0) {
                // 计数器为负，修正为 0
                redisTemplate.opsForValue().set(GLOBAL_CONCURRENT_KEY, "0");
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
            String value = redisTemplate.opsForValue().get(GLOBAL_CONCURRENT_KEY);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 获取全局并发上限配置值。
     *
     * @return 最大并发数
     */
    public int getMaxGlobalConcurrent() {
        return cronjobProperties.getExecutor().getMaxConcurrent() * 3;
    }

    /**
     * 强制校准全局并发计数器。
     *
     * <p>由定时任务定期调用，通过查询 RUNNING 状态的日志数校准计数器。
     * 防止进程崩溃导致的计数器漂移。
     */
    public void calibrate(long actualRunningCount) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(CALIBRATION_LOCK_KEY, "1", Duration.ofSeconds(30));
            if (!Boolean.TRUE.equals(acquired)) {
                return; // 其他节点正在校准
            }
            redisTemplate.opsForValue().set(GLOBAL_CONCURRENT_KEY, String.valueOf(actualRunningCount));
            log.info("[GlobalConcurrency] 计数器已校准: value={}", actualRunningCount);
        } catch (Exception e) {
            log.warn("[GlobalConcurrency] 校准失败: reason={}", e.getMessage());
        } finally {
            try {
                redisTemplate.delete(CALIBRATION_LOCK_KEY);
            } catch (Exception ignored) {
            }
        }
    }
}
