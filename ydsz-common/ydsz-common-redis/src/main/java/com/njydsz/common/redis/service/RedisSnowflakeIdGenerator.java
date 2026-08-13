package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.config.RedisProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式雪花 ID 生成器
 *
 * <p>基于 Twitter Snowflake 算法：
 * <ul>
 *   <li>1 位符号位（固定 0）</li>
 *   <li>41 位时间戳（毫秒级，约 69 年）</li>
 *   <li>10 位工作机器 ID（5 位数据中心 + 5 位工作节点）</li>
 *   <li>12 位序列号（每毫秒 4096 个 ID）</li>
 * </ul>
 *
 * <p><b>workerId 分配：</b>通过 Redis INCR 全局递增分配，确保集群中每个实例的 workerId 唯一。
 * 支持自定义 datacenterId（0~31）和 workerId 上限检测。
 *
 * <p><b>特性：</b>
 * <ul>
 *   <li>全局唯一：workerId + sequence + timestamp 组合保证唯一</li>
 *   <li>趋势递增：同一毫秒内序列递增，整体按时间单调递增</li>
 *   <li>高吞吐：单实例峰值可达 4096 * 1000 = 409.6 万 QPS</li>
 *   <li>高可用：Redis 不可用时降级为本地序列生成</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Autowired
 * private RedisSnowflakeIdGenerator idGenerator;
 *
 * public Long createOrder() {
 *     return idGenerator.nextId();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisSnowflakeIdGenerator {

    /** 起始时间戳（2024-01-01） */
    private static final long EPOCH = 1704038400000L;

    /** 工作机器 ID 占用位数 */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心 ID 占用位数 */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 序列号占用位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大工作机器 ID（31） */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 最大数据中心 ID（31） */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 序列号掩码（4095） */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 工作机器 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心 ID 左移位数 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** Redis 分配 workerId 的 Lua 脚本（原子递增 + 上限保护） */
    private static final String ALLOCATE_WORKER_LUA =
            "local current = redis.call('INCR', KEYS[1]) " +
            "if current > tonumber(ARGV[1]) then " +
            "  redis.call('SET', KEYS[1], 0) " +
            "  return 0 " +
            "end " +
            "return current - 1";

    /**
     * Redis 心跳续约 Lua 脚本：
     * 仅当 key 存在时才续约（EXPIRE），避免已释放的 workerId 被错误续约
     */
    private static final String HEARTBEAT_LUA =
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +
            "  return 1 " +
            "end " +
            "return 0";

    /** workerId 心跳间隔（秒）：默认 30 秒 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    /** workerId 心跳 TTL（秒）：默认 90 秒，超过 3 个心跳周期未续约则自动释放 */
    private static final long HEARTBEAT_TTL_SECONDS = 90L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final long datacenterId;
    private volatile long workerId;
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    private final ConcurrentHashMap<String, DefaultRedisScript<Long>> scriptCache = new ConcurrentHashMap<>();

    /** 心跳定时任务执行器 */
    private ScheduledExecutorService heartbeatExecutor;

    /** 标记当前实例是否已关闭，用于心跳任务退出判断 */
    private volatile boolean shutdown = false;

    public RedisSnowflakeIdGenerator(RedisTemplate<String, Object> redisTemplate,
                                     RedisProperties redisProperties) {
        this(redisTemplate, redisProperties, -1L, true);
    }

    /**
     * 构造方法
     *
     * @param redisTemplate  Redis 客户端
     * @param redisProperties Redis 配置
     * @param datacenterId   数据中心 ID（0~31），-1 表示自动分配
     * @param autoAllocateWorkerId 是否自动通过 Redis 分配 workerId
     */
    public RedisSnowflakeIdGenerator(RedisTemplate<String, Object> redisTemplate,
                                     RedisProperties redisProperties,
                                     long datacenterId,
                                     boolean autoAllocateWorkerId) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;

        long dcId = datacenterId < 0
                ? (Math.abs((long) (System.getenv().getOrDefault("HOSTNAME", "0").hashCode())) % (MAX_DATACENTER_ID + 1))
                : Math.min(datacenterId, MAX_DATACENTER_ID);
        this.datacenterId = dcId;

        long wId = 0L;
        if (autoAllocateWorkerId) {
            try {
                wId = allocateWorkerIdFromRedis();
                // 分配成功后立即设置心跳 TTL，并启动续约任务
                setWorkerIdHeartbeat(wId);
                startHeartbeatScheduler(wId);
            } catch (Exception e) {
                log.warn("【Snowflake】从 Redis 分配 workerId 失败，降级为本地生成 | error={}", e);
                wId = Math.abs(Thread.currentThread().threadId()) % (MAX_WORKER_ID + 1);
            }
        }
        this.workerId = wId;
        log.info("【Snowflake】ID 生成器初始化完成 | datacenterId={} | workerId={}", this.datacenterId, this.workerId);
    }

    private long allocateWorkerIdFromRedis() {
        String key = formatKey("snowflake:worker_id");
        DefaultRedisScript<Long> script = scriptCache.computeIfAbsent("alloc_worker", k -> {
            DefaultRedisScript<Long> s = new DefaultRedisScript<>();
            s.setScriptText(ALLOCATE_WORKER_LUA);
            s.setResultType(Long.class);
            return s;
        });
        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                String.valueOf(MAX_WORKER_ID));
        return result != null ? result : 0L;
    }

    /**
     * 为 workerId 设置初始心跳 TTL
     *
     * @param workerId 工作节点 ID
     */
    private void setWorkerIdHeartbeat(long workerId) {
        String key = formatKey("snowflake:worker:" + workerId);
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));
        log.debug("【Snowflake】设置 workerId 心跳 TTL | workerId={} | ttl={}s", workerId, HEARTBEAT_TTL_SECONDS);
    }

    /**
     * 启动 workerId 心跳续约定时任务
     *
     * @param workerId 工作节点 ID
     */
    private void startHeartbeatScheduler(long workerId) {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "snowflake-heartbeat-" + workerId);
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (shutdown) {
                return;
            }
            try {
                renewWorkerIdHeartbeat(workerId);
            } catch (Exception e) {
                log.warn("【Snowflake】workerId 心跳续约失败 | workerId={}", workerId, e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        log.info("【Snowflake】启动 workerId 心跳续约任务 | workerId={} | interval={}s",
                workerId, HEARTBEAT_INTERVAL_SECONDS);
    }

    /**
     * 续约 workerId 心跳（使用 Lua 脚本确保原子性）
     *
     * @param workerId 工作节点 ID
     */
    private void renewWorkerIdHeartbeat(long workerId) {
        String key = formatKey("snowflake:worker:" + workerId);
        DefaultRedisScript<Long> script = scriptCache.computeIfAbsent("heartbeat", k -> {
            DefaultRedisScript<Long> s = new DefaultRedisScript<>();
            s.setScriptText(HEARTBEAT_LUA);
            s.setResultType(Long.class);
            return s;
        });

        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                String.valueOf(HEARTBEAT_TTL_SECONDS));

        if (result != null && result == 1L) {
            log.debug("【Snowflake】workerId 心跳续约成功 | workerId={}", workerId);
        } else {
            log.warn("【Snowflake】workerId 已过期或被释放，尝试重新分配 | workerId={}", workerId);
            // workerId 已失效，尝试重新分配
            try {
                long oldWorkerId = this.workerId;
                long newWorkerId = allocateWorkerIdFromRedis();
                setWorkerIdHeartbeat(newWorkerId);
                this.workerId = newWorkerId;
                log.info("【Snowflake】重新分配 workerId 成功 | oldWorkerId={} | newWorkerId={}",
                        oldWorkerId, newWorkerId);
            } catch (Exception e) {
                    log.error("【Snowflake】重新分配 workerId 失败", e);
            }
        }
    }

    /**
     * 应用关闭时清理心跳任务
     */
    @PreDestroy
    public void shutdown() {
        shutdown = true;
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("【Snowflake】停止 workerId 心跳续约任务 | workerId={}", workerId);
        }
    }

    /**
     * 生成下一个唯一 ID
     *
     * <p><b>CAS 优化说明：</b>
     * 原实现使用 {@code synchronized} 保证线程安全，高并发下所有线程竞争同一把锁。
     * 现改用 CAS（{@link AtomicLong#compareAndSet}）替代大部分同步逻辑，仅在序列号用尽等待下一毫秒时使用同步。
     * 参考 Leaf-snowflake 的优化实践。
     *
     * @return 64 位 Long 类型 ID
     */
    public long nextId() {
        long timestamp = currentTimeMillis();
        long lastTs = lastTimestamp.get();

        // 时钟回拨检测与处理
        if (timestamp < lastTs) {
            long offset = lastTs - timestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset << 1);
                    timestamp = currentTimeMillis();
                    lastTs = lastTimestamp.get();
                    if (timestamp < lastTs) {
                        throw new RuntimeException("时钟回拨超过 5ms，拒绝生成 ID");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("ID 生成被中断", e);
                }
            } else {
                throw new RuntimeException(
                        String.format("时钟回拨 %d ms，拒绝生成 ID", offset));
            }
        }

        // CAS 更新序列号（热路径，无锁）
        if (timestamp == lastTs) {
            // 同一毫秒内，CAS 递增序列号
            while (true) {
                long currentSeq = sequence.get();
                long nextSeq = (currentSeq + 1) & SEQUENCE_MASK;

                // 序列号用尽（溢出），等待下一毫秒
                if (nextSeq == 0) {
                    timestamp = waitNextMillisSequenceExhausted(lastTs);
                    lastTs = lastTimestamp.get();
                    // 重新进入外层逻辑（因为 timestamp 已变化）
                    break;
                }

                if (sequence.compareAndSet(currentSeq, nextSeq)) {
                    // CAS 成功，组装 ID
                    return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                            | (datacenterId << DATACENTER_ID_SHIFT)
                            | (workerId << WORKER_ID_SHIFT)
                            | nextSeq;
                }
                // CAS 失败，重试
            }
        }

        // 处理：序列号用尽等待后的新时间戳 或 其他线程已更新时间戳的情况
        if (timestamp != lastTs) {
            sequence.set(0L);
            // CAS 更新 lastTimestamp
            if (lastTimestamp.compareAndSet(lastTs, timestamp)
                    || lastTimestamp.get() == timestamp) {
                // 组装 ID（序列号从 0 开始）
                return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                        | (datacenterId << DATACENTER_ID_SHIFT)
                        | (workerId << WORKER_ID_SHIFT)
                        | 0L;
            } else {
                // CAS 失败（其他线程已更新了时间戳），重新进入热路径
                return nextId();
            }
        }

        // 兜底：重新尝试
        return nextId();
    }

    /**
     * 序列号用尽时等待下一毫秒（同步保护，仅在极端并发下触发）
     *
     * @param lastTs 上一毫秒的时间戳
     * @return 新的时间戳
     */
    private synchronized long waitNextMillisSequenceExhausted(long lastTs) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTs) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 解析 ID 中的时间戳
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * 解析 ID 中的数据中心 ID
     */
    public static long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 解析 ID 中的工作节点 ID
     */
    public static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 解析 ID 中的序列号
     */
    public static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getWorkerId() {
        return workerId;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private String formatKey(String key) {
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }
}
