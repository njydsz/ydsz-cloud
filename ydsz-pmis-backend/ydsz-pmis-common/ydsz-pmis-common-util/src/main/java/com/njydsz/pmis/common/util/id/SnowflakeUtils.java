package com.njydsz.pmis.common.util.id;

import lombok.extern.slf4j.Slf4j;
import com.njydsz.pmis.common.util.security.DigestUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 高性能分布式 ID 生成器
 * <p>
 * 基于 Snowflake 算法实现，参考 Twitter、百度 UidGenerator、美团 Leaf 等开源实现优化。
 * </p>
 * <p>
 * 特性：
 * 1. 支持 64 位 long 和字符串格式 ID
 * 2. 支持自定义节点 ID 或自动计算
 * 3. 支持时间回拨检测与容忍
 * 4. 支持高并发场景（分片优化）
 * 5. 支持 ID 解析（时间戳、节点 ID、序列号）
 * </p>
 * <p>
 * ID 结构（64 位）：
 * <pre>{@code
 * +------+----------------------+-------------+-------------+---------+
 * | sign |     timestamp        | datacenter  |   worker    | sequence |
 * | 1bit |       41bit          |    5bit     |    5bit     |  12bit  |
 * +------+----------------------+-------------+-------------+---------+
 * }</pre>
 * </p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public final class SnowflakeUtils {

    private static final long EPOCH = 1577836800000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);
    private static final long MAX_SHARD_COUNT = SEQUENCE_MASK + 1;

    private static final long CLOCK_BACKWARD_TOLERANCE_MILLIS = 5L;
    private static final long CLOCK_BACKWARD_MAX_WAIT_MILLIS = 5000L;

    private final long workerId;
    private final long datacenterId;
    private final int shardCount;
    private final int shardMask;
    private final AtomicLong[] shardStates;

    private static volatile SnowflakeUtils INSTANCE;

    // Exposed constants for external use
    public static final long MAX_WORKER_ID_PUBLIC = MAX_WORKER_ID;
    public static final long MAX_DATACENTER_ID_PUBLIC = MAX_DATACENTER_ID;

    /**
     * 初始化 Snowflake 实例（仅可调用一次）
     *
     * @param workerId     工作节点 ID（0-31）
     * @param datacenterId 数据中心 ID（0-31）
     * @throws IllegalStateException 如果已经初始化过
     */
    public static void init(long workerId, long datacenterId) {
        if (INSTANCE != null) {
            throw new IllegalStateException("SnowflakeUtils has already been initialized");
        }
        synchronized (SnowflakeUtils.class) {
            if (INSTANCE != null) {
                throw new IllegalStateException("SnowflakeUtils has already been initialized");
            }
            INSTANCE = new SnowflakeUtils(workerId, datacenterId);
        }
    }

    private SnowflakeUtils(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.shardCount = initShardCount();
        this.shardMask = this.shardCount - 1;
        this.shardStates = initShardStates(this.shardCount);
        log.info("Snowflake initialized. Worker ID: {}, Datacenter ID: {}", workerId, datacenterId);
    }

    /**
     * 生成下一个唯一 ID（线程安全）
     *
     * @return 生成的唯一 ID
     */
    public long nextId() {
        int shardIndex = (int) (Thread.currentThread().threadId() & shardMask);
        AtomicLong shardState = shardStates[shardIndex];
        for (; ; ) {
            long currentState = shardState.get();
            long lastTimestamp = extractTimestamp(currentState);
            long lastSequence = extractSequence(currentState);
            long timestamp = resolveTimestamp(lastTimestamp);
            long sequence = timestamp == lastTimestamp ? lastSequence + shardCount : shardIndex;
            if (sequence > SEQUENCE_MASK) {
                timestamp = tilNextMillis(lastTimestamp);
                sequence = shardIndex;
            }
            long nextState = packState(timestamp, sequence);
            if (shardState.compareAndSet(currentState, nextState)) {
                return composeId(timestamp, sequence);
            }
        }
    }

    /**
     * 等待下一毫秒
     *
     * @param lastTimestamp 上一个时间戳
     * @return 下一毫秒的时间戳
     * @throws RuntimeException 当时间回拨超过容忍阈值时抛出
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        long totalWaited = 0L;
        while (timestamp <= lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms", offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
            }
            if (totalWaited >= CLOCK_BACKWARD_MAX_WAIT_MILLIS) {
                log.error("Clock moved backwards, waited {} ms, exceeds max wait {} ms", totalWaited, CLOCK_BACKWARD_MAX_WAIT_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
            }
            LockSupport.parkNanos(offset * 1_000_000);
            totalWaited += offset;
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 处理时间戳解析
     *
     * @param lastTimestamp 上次生成 ID 时的时间戳
     * @return 当前时间戳
     * @throws RuntimeException 当时间回拨超过容忍阈值时抛出
     */
    private long resolveTimestamp(long lastTimestamp) {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                log.warn("Clock moved backwards by {} ms, waiting to recover", offset);
                LockSupport.parkNanos(offset * 1_000_000);
                timestamp = timeGen();
                if (timestamp < lastTimestamp) {
                    long remainingOffset = lastTimestamp - timestamp;
                    log.error("Clock still moved backwards after waiting, remaining offset: {} ms", remainingOffset);
                    throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id, remaining offset: %d ms", remainingOffset));
                }
            } else {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms", offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
            }
        }
        return timestamp;
    }

    /**
     * 组装最终的唯一 ID
     *
     * @param timestamp 时间戳
     * @param sequence  序列号
     * @return 组合后的唯一 ID
     */
    private long composeId(long timestamp, long sequence) {
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 初始化分片数量
     *
     * @return 分片数量
     */
    private static int initShardCount() {
        int defaultShardCount = Math.max(8, Runtime.getRuntime().availableProcessors());
        int configuredShardCount = Integer.getInteger("ydsz.snowflake.shardCount", defaultShardCount);
        int normalizedShardCount = Math.max(1, configuredShardCount);
        int powerOfTwoShardCount = 1;
        while (powerOfTwoShardCount < normalizedShardCount) {
            powerOfTwoShardCount <<= 1;
        }
        return (int) Math.min(powerOfTwoShardCount, MAX_SHARD_COUNT);
    }

    /**
     * 初始化分片状态数组
     *
     * @param shardCount 分片数量
     * @return 分片状态数组
     */
    private static AtomicLong[] initShardStates(int shardCount) {
        AtomicLong[] states = new AtomicLong[shardCount];
        for (int i = 0; i < shardCount; i++) {
            states[i] = new AtomicLong(-1L);
        }
        return states;
    }

    /**
     * 打包分片状态
     *
     * @param timestamp 时间戳
     * @param sequence  序列号
     * @return 打包后的状态值
     */
    private static long packState(long timestamp, long sequence) {
        return (timestamp << SEQUENCE_BITS) | sequence;
    }

    /**
     * 从分片状态中提取时间戳
     *
     * @param state 分片状态
     * @return 时间戳
     */
    private static long extractTimestamp(long state) {
        return state < 0 ? -1L : state >>> SEQUENCE_BITS;
    }

    /**
     * 从分片状态中提取序列号
     *
     * @param state 分片状态
     * @return 序列号
     */
    private static long extractSequence(long state) {
        return state < 0 ? -1L : state & SEQUENCE_MASK;
    }

    /**
     * 获取下一个 ID（兼容旧版静态调用）
     *
     * @return 生成的唯一 ID
     */
    public static long nextIdLong() {
        return getInstance().nextId();
    }

    /**
     * 获取下一个 ID 字符串（兼容旧版静态调用）
     *
     * @return 生成的唯一 ID 字符串
     */
    public static String nextIdStr() {
        return String.valueOf(getInstance().nextId());
    }

    /**
     * 获取单例实例（自动计算节点 ID）
     *
     * @return SnowflakeUtils 单例实例
     */
    public static SnowflakeUtils getInstance() {
        if (INSTANCE == null) {
            synchronized (SnowflakeUtils.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SnowflakeUtils(computeWorkerId(), getDataCenterId());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 获取单例实例（自定义节点 ID）
     *
     * @param workerId     工作节点 ID
     * @param datacenterId 数据中心 ID
     * @return SnowflakeUtils 实例
     */
    public static SnowflakeUtils getInstance(long workerId, long datacenterId) {
        synchronized (SnowflakeUtils.class) {
            if (INSTANCE != null) {
                throw new IllegalStateException("SnowflakeUtils has already been initialized");
            }
            INSTANCE = new SnowflakeUtils(workerId, datacenterId);
            return INSTANCE;
        }
    }

    /**
     * 计算工作节点 ID，支持通过系统属性或环境变量覆盖
     * <ul>
     *   <li>系统属性：ydsz.snowflake.workerId</li>
     *   <li>环境变量：SNOWFLAKE_WORKER_ID</li>
     *   <li>默认：通过 IP 地址哈希自动计算</li>
     * </ul>
     *
     * @return 计算得到的节点 ID
     */
    private static long computeWorkerId() {
        String configured = System.getProperty("ydsz.snowflake.workerId",
                System.getenv("SNOWFLAKE_WORKER_ID"));
        if (configured != null && !configured.isEmpty()) {
            try {
                long id = Long.parseLong(configured);
                if (id >= 0 && id <= MAX_WORKER_ID) {
                    return id;
                }
                log.warn("配置的 WorkerId {} 超出范围 [0, {}]，使用自动计算", id, MAX_WORKER_ID);
            } catch (NumberFormatException e) {
                log.warn("配置的 WorkerId {} 格式无效，使用自动计算", configured);
            }
        }
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String hash = DigestUtils.sha256Hex(hostAddress);
            return Long.parseLong(hash.substring(0, 5), 16) % 32;
        } catch (UnknownHostException e) {
            return ThreadLocalRandom.current().nextLong(32);
        }
    }

    /**
     * 计算数据中心 ID，支持通过系统属性或环境变量覆盖
     * <ul>
     *   <li>系统属性：ydsz.snowflake.datacenterId</li>
     *   <li>环境变量：SNOWFLAKE_DATACENTER_ID</li>
     *   <li>默认：通过主机名哈希自动计算</li>
     * </ul>
     *
     * @return 计算得到的数据中心 ID
     */
    private static long getDataCenterId() {
        String configured = System.getProperty("ydsz.snowflake.datacenterId",
                System.getenv("SNOWFLAKE_DATACENTER_ID"));
        if (configured != null && !configured.isEmpty()) {
            try {
                long id = Long.parseLong(configured);
                if (id >= 0 && id <= MAX_DATACENTER_ID) {
                    return id;
                }
                log.warn("配置的 DatacenterId {} 超出范围 [0, {}]，使用自动计算", id, MAX_DATACENTER_ID);
            } catch (NumberFormatException e) {
                log.warn("配置的 DatacenterId {} 格式无效，使用自动计算", configured);
            }
        }
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String hash = DigestUtils.sha256Hex(hostName);
            return Long.parseLong(hash.substring(0, 5), 16) % 32;
        } catch (UnknownHostException e) {
            return ThreadLocalRandom.current().nextLong(32);
        }
    }

    /**
     * 解析 ID 中的时间戳
     *
     * @param id ID
     * @return 时间戳（毫秒）
     */
    public static long parseTimestamp(long id) {
        return ((id >>> TIMESTAMP_LEFT_SHIFT) + EPOCH);
    }

    /**
     * 解析 ID 中的工作节点 ID
     *
     * @param id ID
     * @return 工作节点 ID
     */
    public static long parseWorkerId(long id) {
        return (id >>> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 解析 ID 中的数据中心 ID
     *
     * @param id ID
     * @return 数据中心 ID
     */
    public static long parseDatacenterId(long id) {
        return (id >>> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 解析 ID 中的序列号
     *
     * @param id ID
     * @return 序列号
     */
    public static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    /**
     * 获取 ID 生成时间（Instant 格式）
     *
     * @param id ID
     * @return Instant 对象
     */
    public static Instant parseInstant(long id) {
        return Instant.ofEpochMilli(parseTimestamp(id));
    }

    /**
     * 获取工作节点 ID
     *
     * @return 工作节点 ID
     */
    public long getWorkerId() {
        return workerId;
    }

    /**
     * 获取数据中心 ID
     *
     * @return 数据中心 ID
     */
    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * 时钟回拨异常
     *
     * <p>当系统时钟发生回拨时抛出，调用方可捕获此异常进行特殊处理（如等待时钟同步、切换节点等）。
     */
    public static class ClockBackwardException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        
        private final long backwardMillis;
        private final long lastTimestamp;
        private final long currentTimestamp;

        public ClockBackwardException(long backwardMillis, long lastTimestamp, long currentTimestamp) {
            super(String.format("时钟回拨%d毫秒，上次时间=%d，当前时间=%d", backwardMillis, lastTimestamp, currentTimestamp));
            this.backwardMillis = backwardMillis;
            this.lastTimestamp = lastTimestamp;
            this.currentTimestamp = currentTimestamp;
        }

        public long getBackwardMillis() { return backwardMillis; }
        public long getLastTimestamp() { return lastTimestamp; }
        public long getCurrentTimestamp() { return currentTimestamp; }
    }
}
