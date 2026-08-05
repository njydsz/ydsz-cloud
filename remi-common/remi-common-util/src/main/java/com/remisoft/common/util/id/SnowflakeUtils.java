package com.remisoft.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import com.remisoft.common.util.security.DigestUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式 ID 生成器
 *
 * <p>基于 Twitter Snowflake 算法实现，64 位 long 类型唯一 ID，趋势递增、高性能、低冲突。
 *
 * <h2>ID 结构（64 位）</h2>
 * <pre>{@code
 * +------+----------------------+-------------+-------------+---------+
 * | sign |     timestamp        | datacenter  |   worker    | sequence |
 * | 1bit |       41bit          |    5bit     |    5bit     |  12bit  |
 * +------+----------------------+-------------+-------------+---------+
 * }</pre>
 *
 * <h2>性能特征</h2>
 * <ul>
 *   <li>单节点理论峰值：409.6 万 ID/s（12 位序列号 / 毫秒）</li>
 *   <li>实际吞吐量取决于 CAS 竞争程度，普通服务器 50-200 万 ID/s</li>
 *   <li>如需更高吞吐，建议使用 Leaf-segment 或号段模式</li>
 * </ul>
 *
 * <h2>时钟回拨处理</h2>
 * <ul>
 *   <li>≤ 5ms：循环等待恢复</li>
 *   <li>> 5ms：抛出 {@link ClockBackwardException} 强制报错</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public final class SnowflakeUtils {

    /** 起始纪元时间戳（2020-01-01 00:00:00 UTC） */
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

    /** 时钟回拨容忍阈值（毫秒），≤ 5ms 直接等待 */
    private static final long CLOCK_BACKWARD_TOLERANCE_MILLIS = 5L;
    /** 时钟回拨最大等待时间（毫秒），超时则抛出异常 */
    private static final long CLOCK_BACKWARD_MAX_WAIT_MILLIS = 5000L;

    /** 工作节点 ID */
    private final long workerId;
    /** 数据中心 ID */
    private final long datacenterId;

    /**
     * 状态（高 52 位 = 相对 epoch 的时间戳毫秒数，低 12 位 = 序列号）。
     * -1 表示未初始化（首次生成时自动填充当前时间戳 + 序列号 0）。
     */
    private final AtomicLong state = new AtomicLong(-1L);

    /** 单例实例 */
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
            throw new IllegalArgumentException(
                    String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("Snowflake initialized. Worker ID: {}, Datacenter ID: {}", workerId, datacenterId);
    }

    /**
     * 生成下一个唯一 ID（线程安全）
     *
     * <p>使用 CAS 无锁重试，在单节点 100 万 QPS 下 CAS 失败率 < 1%，
     * 绝大多数场景下 1 次 CAS 即可成功。
     *
     * @return 生成的唯一 ID
     * @throws ClockBackwardException 当时钟回拨超过容忍阈值时抛出
     */
    public long nextId() {
        for (; ; ) {
            long currentState = state.get();
            long lastTimestamp = extractTimestamp(currentState);
            long lastSequence = extractSequence(currentState);
            long timestamp = resolveTimestamp(lastTimestamp);

            long sequence;
            if (timestamp == lastTimestamp) {
                sequence = lastSequence + 1;
                if (sequence > SEQUENCE_MASK) {
                    timestamp = tilNextMillis(lastTimestamp);
                    sequence = 0;
                }
            } else {
                sequence = 0;
            }

            long nextState = packState(timestamp, sequence);
            if (state.compareAndSet(currentState, nextState)) {
                return composeId(timestamp, sequence);
            }
        }
    }

    /**
     * 等待下一毫秒
     *
     * <p>当序列号耗尽时调用，等待到下一毫秒以重置序列号。
     *
     * @param lastTimestamp 上一个时间戳
     * @return 下一毫秒的时间戳
     * @throws ClockBackwardException 当时钟回拨超过容忍阈值时抛出
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        long totalWaited = 0L;
        while (timestamp <= lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms",
                        offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
            }
            if (totalWaited >= CLOCK_BACKWARD_MAX_WAIT_MILLIS) {
                log.error("Clock moved backwards, waited {} ms, exceeds max wait {} ms",
                        totalWaited, CLOCK_BACKWARD_MAX_WAIT_MILLIS);
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
     * @return 当前时间戳（毫秒）
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 处理时间戳解析（含时钟回拨容忍）
     *
     * @param lastTimestamp 上次生成 ID 时的时间戳
     * @return 当前时间戳
     * @throws ClockBackwardException 当时钟回拨超过容忍阈值时抛出
     */
    private long resolveTimestamp(long lastTimestamp) {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                log.warn("Clock moved backwards by {} ms, waiting to recover", offset);
                // 循环等待到时间恢复或超时（100ms 上限）
                long deadlineNano = System.nanoTime() + 100_000_000L;
                while (System.currentTimeMillis() < lastTimestamp) {
                    if (System.nanoTime() > deadlineNano) {
                        long remainingOffset = lastTimestamp - timeGen();
                        log.error("Clock still moved backwards after waiting 100ms, remaining offset: {} ms",
                                remainingOffset);
                        throw new ClockBackwardException(remainingOffset, lastTimestamp, timeGen());
                    }
                    LockSupport.parkNanos(500_000L); // 0.5ms
                }
                timestamp = timeGen();
            } else {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms",
                        offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
            }
        }
        return timestamp;
    }

    /**
     * 组装最终的唯一 ID
     *
     * @param timestamp 时间戳（相对 epoch 毫秒数）
     * @param sequence  序列号
     * @return 组合后的唯一 ID
     */
    private long composeId(long timestamp, long sequence) {
        return (timestamp << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 打包状态：高 52 位存储时间戳，低 12 位存储序列号
     */
    private static long packState(long timestamp, long sequence) {
        return (timestamp << SEQUENCE_BITS) | sequence;
    }

    /**
     * 从状态中提取时间戳
     */
    private static long extractTimestamp(long state) {
        return state < 0 ? -1L : state >>> SEQUENCE_BITS;
    }

    /**
     * 从状态中提取序列号
     */
    private static long extractSequence(long state) {
        return state < 0 ? -1L : state & SEQUENCE_MASK;
    }

    /**
     * 获取下一个 ID（静态方法，委托给单例）
     *
     * @return 生成的唯一 ID
     */
    public static long nextIdLong() {
        return getInstance().nextId();
    }

    /**
     * 获取下一个 ID 字符串（静态方法，委托给单例）
     *
     * @return 生成的唯一 ID 字符串
     */
    public static String nextIdStr() {
        return String.valueOf(getInstance().nextId());
    }

    /**
     * 获取单例实例
     *
     * <p>必须通过 {@link #init(long, long)} 显式初始化，
     * 或通过 {@code SnowflakeAutoConfiguration} 自动配置后调用。
     *
     * @return SnowflakeUtils 单例实例
     * @throws IllegalStateException 若未初始化
     */
    public static SnowflakeUtils getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "SnowflakeUtils 未初始化，请先调用 init() 或通过 SnowflakeAutoConfiguration 配置");
        }
        return INSTANCE;
    }

    /**
     * 计算工作节点 ID
     *
     * <p>优先级：系统属性 > 环境变量 REMI_SNOWFLAKE_WORKER_ID > POD/HOSTNAME 哈希 > 本地 IP 哈希
     *
     * @return 计算得到的节点 ID
     */
    static long computeWorkerId() {
        String configured = System.getProperty("remi.snowflake.workerId",
                System.getenv("REMI_SNOWFLAKE_WORKER_ID"));
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
        // 容器化环境优先使用 HOSTNAME/INSTANCE_INDEX/POD_INDEX 哈希
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
            log.warn("无法获取本机主机地址，使用随机 workerId。容器化环境建议配置 INSTANCE_INDEX 环境变量");
            return ThreadLocalRandom.current().nextLong(32);
        }
    }

    /**
     * 计算数据中心 ID
     *
     * <p>优先级：系统属性 > 环境变量 REMI_SNOWFLAKE_DATACENTER_ID > 主机名哈希
     *
     * @return 计算得到的数据中心 ID
     */
    static long computeDatacenterId() {
        String configured = System.getProperty("remi.snowflake.datacenterId",
                System.getenv("REMI_SNOWFLAKE_DATACENTER_ID"));
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
     */
    public long getWorkerId() {
        return workerId;
    }

    /**
     * 获取数据中心 ID
     */
    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * 获取最近一次生成 ID 时的时间戳（毫秒）
     *
     * <p>用于健康检查等场景快速获取最后一次 ID 生成时间，不会触发 ID 生成。
     * 如果尚未生成过 ID，返回当前时间。
     *
     * @return 最近一次 ID 生成时间戳（毫秒），未初始化时返回 epoch 起点
     */
    public long getLastTimestamp() {
        long currentState = state.get();
        if (currentState < 0) {
            return System.currentTimeMillis();
        }
        return extractTimestamp(currentState) + EPOCH;
    }

    // ==================== ID 反解析 ====================

    /**
     * 从 Snowflake ID 中提取生成时间戳（毫秒，UTC）
     *
     * @param id Snowflake 算法生成的 64 位 ID
     * @return 生成该 ID 时的毫秒时间戳（2020-01-01 00:00:00 UTC 起算）
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT) + EPOCH;
    }

    /**
     * 从 Snowflake ID 中提取数据中心 ID
     *
     * @param id Snowflake 算法生成的 64 位 ID
     * @return 数据中心 ID（0-31）
     */
    public static long parseDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 从 Snowflake ID 中提取工作节点 ID
     *
     * @param id Snowflake 算法生成的 64 位 ID
     * @return 工作节点 ID（0-31）
     */
    public static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    /**
     * 从 Snowflake ID 中提取同一毫秒内的序列号
     *
     * @param id Snowflake 算法生成的 64 位 ID
     * @return 序列号（0-4095）
     */
    public static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }

    /**
     * 重置单例实例（仅供测试使用）
     *
     * <p>清除已初始化的单例实例，使后续调用 {@link #getInstance()} 或 {@link #init(long, long)}
     * 能够重新创建实例。此方法仅用于单元测试中确保测试隔离，生产环境严禁调用。
     *
     * <pre>{@code
     * &#64;AfterEach
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
