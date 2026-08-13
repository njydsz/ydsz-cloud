package com.njydsz.common.lock.controller;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 分布式锁运维管理控制器
 *
 * <p>提供锁的运行时管理能力：
 * <ul>
 *   <li>查询当前锁指标（获取成功率、平均耗时、竞争次数等）</li>
 *   <li>查看当前活跃的续期任务</li>
 *   <li>强制执行锁释放（用于死锁恢复）</li>
 *   <li>查询指定 key 的锁状态</li>
 * </ul>
 *
 * <p><b>安全注意：</b>本控制器应仅内网访问或通过网关配置访问控制，
 * 避免外部调用 {@code force-unlock} 导致数据不一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/lock")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnBean(StringRedisTemplate.class)
@Tag(name = "锁运维管理", description = "分布式锁运行时管理与死锁恢复")
public class LockAdminController {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private final StringRedisTemplate redisTemplate;
    private final LockMetrics lockMetrics;
    private final LockWatchDog lockWatchDog;

    public LockAdminController(StringRedisTemplate redisTemplate,
                                LockMetrics lockMetrics,
                                LockWatchDog lockWatchDog) {
        this.redisTemplate = redisTemplate;
        this.lockMetrics = lockMetrics;
        this.lockWatchDog = lockWatchDog;
    }

    /**
     * 获取锁运行时指标快照
     *
     * @return 包含各项指标的 Map
     */
    @GetMapping("/metrics")
    @Operation(summary = "获取锁指标", description = "获取当前锁子系统的运行时指标快照")
    public BaseResponse<Map<String, Object>> metrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("acquireSuccessCount", lockMetrics.getAcquireSuccessCount());
        metrics.put("acquireFailCount", lockMetrics.getAcquireFailCount());
        metrics.put("releaseCount", lockMetrics.getReleaseCount());
        metrics.put("averageWaitTimeMs", String.format("%.2f", lockMetrics.getAverageWaitTimeMillis()));
        metrics.put("averageHoldTimeMs", String.format("%.2f", lockMetrics.getAverageHoldTimeMillis()));
        metrics.put("competitionCount", lockMetrics.getCompetitionCount());
        metrics.put("activeLocks", lockMetrics.getActiveLocks());
        metrics.put("lockTimeoutCount", lockMetrics.getLockTimeoutCount());
        metrics.put("watchdogRenewCount", lockMetrics.getWatchdogRenewCount());
        metrics.put("idempotentHitCount", lockMetrics.getIdempotentHitCount());
        metrics.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
        log.info("[ydsz-lock] [admin] 查询锁指标 active={}", lockMetrics.getActiveLocks());
        return BaseResponse.success(metrics);
    }

    /**
     * 查询指定锁的状态
     *
     * @param key 锁 key（不含前缀）
     * @return 锁状态信息
     */
    @GetMapping("/status/{key}")
    @Operation(summary = "查询锁状态", description = "查询指定 key 的锁是否被持有及剩余时间")
    public BaseResponse<Map<String, Object>> lockStatus(
            @Parameter(description = "锁 key（不含 ydsz 前缀）")
            @PathVariable("key") String key) {
        String fullKey = LOCK_KEY_PREFIX + key;
        Map<String, Object> status = new HashMap<>();
        status.put("key", fullKey);
        Boolean exists = redisTemplate.hasKey(fullKey);
        status.put("exists", exists != null && exists);
        if (Boolean.TRUE.equals(exists)) {
            Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.MILLISECONDS);
            status.put("ttlMs", ttl);
        }
        log.debug("[ydsz-lock] [admin] 查询锁状态 key={} exists={}", fullKey, exists);
        return BaseResponse.success(status);
    }

    /**
     * 强制释放指定锁（死锁恢复用）
     *
     * <p><b>警告：</b>强制释放可能导致多节点并发访问同一资源，
     * 请在确认锁持有者已宕机或业务已安全的情况下使用。
     *
     * @param key 锁 key（不含前缀）
     * @return 操作结果
     */
    @DeleteMapping("/force-unlock/{key}")
    @Operation(summary = "强制释放锁", description = "紧急情况下强制释放指定锁（死锁恢复，需确认原持有者已安全）")
    public BaseResponse<Map<String, Object>> forceUnlock(
            @Parameter(description = "锁 key（不含 ydsz 前缀）")
            @PathVariable("key") String key) {
        String fullKey = LOCK_KEY_PREFIX + key;
        Map<String, Object> result = new HashMap<>();
        result.put("key", fullKey);
        Boolean deleted = redisTemplate.delete(fullKey);
        boolean success = deleted != null && deleted;
        result.put("released", success);
        // 同时停止看门狗续期任务
        lockWatchDog.cancelRenewal(fullKey);
        log.warn("[ydsz-lock] [admin] 强制释放锁 key={} success={}", fullKey, success);
        return BaseResponse.success(result);
    }

    /**
     * 列出活跃续期任务
     *
     * @return 当前活跃的 WatchDog 续期任务数
     */
    @GetMapping("/watchdog/tasks")
    @Operation(summary = "查看活跃续期任务", description = "列出当前 WatchDog 正在续期的锁任务数")
    public BaseResponse<Map<String, Object>> activeWatchdogTasks() {
        Map<String, Object> info = new HashMap<>();
        info.put("activeRenewalTasks", lockWatchDog.getActiveTaskCount());
        info.put("timestamp", System.currentTimeMillis());
        return BaseResponse.success(info);
    }

    /**
     * 根据模式搜索锁 key
     *
     * @param pattern 搜索模式（如 "order:*"，使用 Redis KEYS 语法）
     * @return 匹配的锁 key 列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索锁 key", description = "按前缀模式搜索当前持有的锁（谨慎使用，大数据量时影响 Redis 性能）")
    public BaseResponse<Set<String>> searchKeys(
            @Parameter(description = "Redis key 模式，如 'lock:order:*'")
            @RequestParam(value = "pattern", defaultValue = "lock:*") String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        log.debug("[ydsz-lock] [admin] 搜索锁 key pattern={} count={}", pattern, keys == null ? 0 : keys.size());
        return BaseResponse.success(keys == null ? Collections.emptySet() : keys);
    }
}
