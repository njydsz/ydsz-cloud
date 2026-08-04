package com.njydsz.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import com.njydsz.common.util.security.DigestUtils;

import lombok.extern.slf4j.Slf4j;

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
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Slf4j
public final class SnowflakeUtils {

    /** 起始纪元时间戳（2020-01-01 00:00:00 UTC），减少 ID 中时间位长度 */
    private static final long EPOCH = 1577836800000L;

    /** 工作节点 ID 占用位数 */
    private static final long WORKER_ID_BITS = 5L;
    /** 数据中心 ID 占用位数 */
    private static final long DATACENTER_ID_BITS = 5L;
    /** 序列号占用位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大工作节点 ID（31） */
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
    /** 最大数据中心 ID（31） */
    private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);

    /** 工作节点 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 数据中心 ID 左移位数 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 时间戳左移位数 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 序列号掩码（4095） */
    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);
    /** 最大分片数（4096） */
    private static final long MAX_SHARD_COUNT = SEQUENCE_MASK + 1;

    /** 时钟回拨容忍阈值（毫秒），5ms 以内直接等待 */
    private static final long CLOCK_BACKWARD_TOLERANCE_MILLIS = 5L;
    /** 时钟回拨最大等待时间（毫秒），超过则抛出异常 */
    private static final long CLOCK_BACKWARD_MAX_WAIT_MILLIS = 5000L;

    /** 工作节点 ID */
    private final long workerId;
    /** 数据中心 ID */
    private final long datacenterId;
    /** 分片数量（用于高并发优化） */
    private final int shardCount;
    /** 分片掩码 */
    private final int shardMask;
    /** 分片状态数组（每个分片独立维护序列号和时间戳，减少竞争） */
    private final AtomicLong[] shardStates;

    /** 单例实例（volatile 保证可见性） */
    private static volatile SnowflakeUtils INSTANCE;

    /** 对外暴露的最大工作节点 ID */
    public static long getMaxWorkerId() { return MAX_WORKER_ID; }
    /** 对外暴露的最大数据中心 ID */
    public static long getMaxDatacenterId() { return MAX_DATACENTER_ID; }

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
     * <p>当序列号耗尽时调用，等待到下一毫秒以重置序列号。
     * 当 offset 为 0（同一毫秒内）时，至少等待 1ms 避免 CPU 忙等。
     *
     * @param lastTimestamp 上一个时间戳
     * @return 下一毫秒的时间戳
     * @throws ClockBackwardException 当时间回拨超过容忍阈值时抛出
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
            // offset 为 0 表示同一毫秒内序列号耗尽，至少等待 1ms 避免 CPU 忙等
            long parkMillis = Math.max(offset, 1L);
            LockSupport.parkNanos(parkMillis * 1_000_000);
            totalWaited += parkMillis;
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
                // 循环等待到时间恢复或超时（100ms 上限），避免 parkNanos 提前唤醒导致时间仍 < lastTimestamp 直接抛异常
                long deadlineNano = System.nanoTime() + 100_000_000L; // 100ms 超时
                while (System.currentTimeMillis() < lastTimestamp) {
                    if (System.nanoTime() > deadlineNano) {
                        long remainingOffset = lastTimestamp - timeGen();
                        log.error("Clock still moved backwards after waiting 100ms, remaining offset: {} ms", remainingOffset);
                        throw new ClockBackwardException(remainingOffset, lastTimestamp, timeGen());
                    }
                    LockSupport.parkNanos(500_000L); // 0.5ms
                }
                timestamp = timeGen();
            } else {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms", offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
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
     * 获取单例实例
     *
     * <p>必须通过 {@link #init(long, long)} 或 {@link #getInstance(long, long)} 显式初始化，
     * 或通过 {@code SnowflakeAutoConfiguration} 自动配置后调用。若未初始化直接抛出
     * {@link IllegalStateException}，避免 Bean 在自动配置之前触发 {@link #nextIdLong()} 时
     * 静默使用自动计算的 workerId，导致配置的 workerId 被忽略。
     *
     * @return SnowflakeUtils 单例实例
     * @throws IllegalStateException 若未初始化
     */
    public static SnowflakeUtils getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("SnowflakeUtils 未初始化，请先调用 init() 或通过 SnowflakeAutoConfiguration 配置");
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
     *   <li>环境变量：YDSZ_SNOWFLAKE_WORKER_ID（与 SnowflakeProperties / SnowflakeAutoConfiguration 统一）</li>
     *   <li>默认：通过 IP 地址哈希自动计算</li>
     * </ul>
     *
     * @return 计算得到的节点 ID
     */
    private static long computeWorkerId() {
        // 优先读系统属性，其次环境变量；环境变量名与 SnowflakeProperties 默认值保持一致
        String configured = System.getProperty("ydsz.snowflake.workerId",
                System.getenv("YDSZ_SNOWFLAKE_WORKER_ID"));
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
        // 容器化环境（K8s 常见）优先使用 HOSTNAME/INSTANCE_INDEX/POD_INDEX 哈希取模，
        // 避免同主机多容器 IP 相同导致 workerId 冲突引发 ID 重复
        String containerId = System.getenv("HOSTNAME");
        if (containerId == null || containerId.isEmpty()) {
            containerId = System.getenv("INSTANCE_INDEX");
        }
        if (containerId == null || containerId.isEmpty()) {
            containerId = System.getenv("POD_INDEX");
        }
        if (containerId != null && !containerId.isEmpty()) {
            String hash = DigestUtils.sha256Hex(containerId);
            return Long.parseLong(hash.substring(0, 5), 16) % 32;
        }
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            String hash = DigestUtils.sha256Hex(hostAddress);
            return Long.parseLong(hash.substring(0, 5), 16) % 32;
        } catch (UnknownHostException e) {
            log.warn("无法获取本机主机地址，使用随机 workerId。容器化环境建议配置 WorkerIdRegistry 或 INSTANCE_INDEX 环境变量");
            return ThreadLocalRandom.current().nextLong(32);
        }
    }

    /**
     * 计算数据中心 ID，支持通过系统属性或环境变量覆盖
     * <ul>
     *   <li>系统属性：ydsz.snowflake.datacenterId</li>
     *   <li>环境变量：YDSZ_SNOWFLAKE_DATACENTER_ID（与 workerId 命名风格统一）</li>
     *   <li>默认：通过主机名哈希自动计算</li>
     * </ul>
     *
     * @return 计算得到的数据中心 ID
     */
    private static long getDataCenterId() {
        String configured = System.getProperty("ydsz.snowflake.datacenterId",
                System.getenv("YDSZ_SNOWFLAKE_DATACENTER_ID"));
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
     * 获取最近一次生成 ID 时的时间戳（基于 shard 0 的状态估算）。
     *
     * <p>该方法用于健康检查等场景快速获取「最后一次 ID 生成时间」的近似值，
     * 不会触发 ID 生成。返回的时间戳为毫秒（相对 epoch 起点）。
     *
     * @return 最近一次 ID 生成时间戳（毫秒），未初始化时返回 epoch 起点
     * @since 1.0.0
     */
    public long getLastTimestamp() {
        if (shardStates == null || shardStates.length == 0) {
            return EPOCH;
        }
        long maxTimestamp = 0L;
        for (AtomicLong state : shardStates) {
            long t = extractTimestamp(state.get());
            if (t > maxTimestamp) {
                maxTimestamp = t;
            }
        }
        // 未生成过 ID 时（maxTimestamp == 0L）返回当前时间，避免健康检查时钟回拨检测失效
        return maxTimestamp == 0L ? System.currentTimeMillis() : maxTimestamp;
    }

    /**
     * 获取分片数量（用于健康检查和监控）
     *
     * @return 分片数量
     * @since 1.2.0
     */
    public int getShardCount() {
        return shardCount;
    }

    /**
     * 重置单例实例（仅供测试使用）
     *
     * <p>清除已初始化的单例实例，使后续调用 {@link #getInstance()} 或 {@link #init(long, long)}
     * 能够重新创建实例。此方法仅用于单元测试中确保测试隔离，生产环境严禁调用。
     *
     * <pre>{@code
     * @AfterEach
     * void tearDown() {
     *     SnowflakeUtils.resetForTesting();
     * }
     * }</pre>
     */
    static void resetForTesting() {
        synchronized (SnowflakeUtils.class) {
            INSTANCE = null;
        }
    }

}
