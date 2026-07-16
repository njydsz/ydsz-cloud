package com.njydsz.common.redis.service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

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
@Component
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
                log.warn("【Snowflake】workerId 心跳续约失败 | workerId={} | error={}", workerId, e.getMessage());
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
                log.error("【Snowflake】重新分配 workerId 失败 | error={}", e.getMessage());
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
     * @return 64 位 Long 类型 ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        if (timestamp < lastTimestamp.get()) {
            // 时钟回拨处理：等待到最后时间戳
            long offset = lastTimestamp.get() - timestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset << 1);
                    timestamp = currentTimeMillis();
                    if (timestamp < lastTimestamp.get()) {
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

        if (timestamp == lastTimestamp.get()) {
            long seq = (sequence.incrementAndGet()) & SEQUENCE_MASK;
            if (seq == 0) {
                // 当前毫秒序列号用尽，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp.get());
            }
            sequence.set(seq);
        } else {
            sequence.set(0L);
        }

        lastTimestamp.set(timestamp);

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence.get();
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
