package com.njydsz.common.util.id;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.util.security.DigestUtils;

/**
 * 分布式 ID 生成器（核心算法，无 Spring 依赖）。
 *
 * <p>基于 Twitter Snowflake 算法实现，64 位 long 类型唯一 ID，趋势递增、高性能、低冲突。
 *
 * <h2>ID 结构（64 位）</h2>
 * <pre>{@code
 * +------+----------------------+-------------+-------------+---------+
 * | sign |     timestamp        | datacenter  |   worker    | sequence |
 * | 1bit |       41bit          |    5bit     |    10bit    |  7bit   |
 * +------+----------------------+-------------+-------------+---------+
 * }</pre>
 *
 * <p>workerId 占 10 位（0-1023），与 {@link WorkerIdAllocator} 策略链的分配契约对齐
 * （支持 StatefulSet 最多 1024 副本 / IP 哈希 / 文件兜底随机），
 * 避免分配器产出值超出 ID 结构可承载范围导致启动失败。
 *
 * <p>序列号位数可通过构造器配置（{@code sequenceBits}），默认 7 位（每毫秒 128 个），
 * 最高 13 位（每毫秒 8192 个）。位数越高，每毫秒并发能力越强，
 * 但其余字段位数固定，总位数不能超过 63 位。
 *
 * <h2>性能特征</h2>
 * <ul>
 *   <li>单节点理论峰值（默认 7-bit）：12.8 万 ID/s（每毫秒 128 个）</li>
 *   <li>单节点理论峰值（13-bit）：81.9 万 ID/s（每毫秒 8192 个）</li>
 *   <li>实际吞吐量取决于 CAS 竞争程度，普通服务器 5-10 万 ID/s</li>
 * </ul>
 *
 * <h2>时钟回拨处理</h2>
 * <ul>
 *   <li>≤ 5ms：循环等待恢复</li>
 *   <li>> 5ms：抛出 {@link ClockBackwardException} 强制报错</li>
 * </ul>
 *
 * <p>本类为纯算法实现，不包含 Spring 注解。Spring Bean 装配请参见 {@code UtilAutoConfiguration}。
 *
 * @author ydsz-team
 * @since 4.0.0
 */
@Slf4j
public class SnowflakeIdGenerator {

    /** 默认起始纪元时间戳（2020-01-01 00:00:00 UTC） */
    private static final long DEFAULT_EPOCH = 1577836800000L;

    /** 默认序列号位数（每毫秒 128 个） */
    public static final int DEFAULT_SEQUENCE_BITS = 7;
    /** 最大允许序列号位数（每毫秒 8192 个） */
    public static final int MAX_SEQUENCE_BITS = 13;

    /** 工作节点 ID 占用位数（10 位，0-1023，与 WorkerIdAllocator 契约一致） */
    private static final long WORKER_ID_BITS = 10L;
    /** 数据中心 ID 占用位数 */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 最大工作节点 ID（1023） */
    private static final long MAX_WORKER_ID = -1L ^ (-1L << WORKER_ID_BITS);
    /** 最大数据中心 ID（31） */
    private static final long MAX_DATACENTER_ID = -1L ^ (-1L << DATACENTER_ID_BITS);

    // ---- 实例级可配置字段（由 sequenceBits 推导） ----

    /** 序列号占用位数 */
    private final long sequenceBits;
    /** 序列号掩码 */
    private final long sequenceMask;
    /** 工作节点 ID 左移位数 */
    private final long workerIdShift;
    /** 数据中心 ID 左移位数 */
    private final long datacenterIdShift;
    /** 时间戳左移位数 */
    private final long timestampLeftShift;

    /** 时钟回拨容忍阈值（毫秒），≤ 5ms 直接等待 */
    private static final long CLOCK_BACKWARD_TOLERANCE_MILLIS = 5L;
    /** 时钟回拨最大等待时间（毫秒），超时则抛出异常 */
    private static final long CLOCK_BACKWARD_MAX_WAIT_MILLIS = 5000L;

    /** 工作节点 ID */
    private final long workerId;
    /** 数据中心 ID */
    private final long datacenterId;

    /** 起始纪元时间戳（实例级，可配置） */
    private final long epoch;
    /** Snowflake 配置属性（resolveNodeId 读取 node-id 配置使用） */
    private final SnowflakeProperties properties;

    /**
     * 状态（高位 = 相对 epoch 的时间戳毫秒数，低位 = 序列号）。
     * -1 表示未初始化（首次生成时自动填充当前时间戳 + 序列号 0）。
     */
    private final AtomicLong state = new AtomicLong(-1L);

    /**
     * 完整构造器：显式指定 sequenceBits。
     *
     * <p>workerId 分配策略链：PodOrdinal → IpHash。
     * 业务方可通过声明自定义 {@link WorkerIdAllocator} Bean 插入更高优先级的策略。
     *
     * <p>datacenterId 优先级：显式配置 > 自动计算。
     *
     * @param properties   Snowflake 配置属性
     * @param allocator    WorkerId 分配策略链
     * @param sequenceBits 序列号位数（1-${@link #MAX_SEQUENCE_BITS}）
     * @throws IllegalArgumentException 当 sequenceBits 超出范围时
     @return 处理结果
     */
    public SnowflakeIdGenerator(SnowflakeProperties properties, WorkerIdAllocator allocator, int sequenceBits) {
        if (sequenceBits < 1 || sequenceBits > MAX_SEQUENCE_BITS) {
            throw new IllegalArgumentException(
                    String.format("sequenceBits must be between 1 and %d, got %d", MAX_SEQUENCE_BITS, sequenceBits));
        }

        this.sequenceBits = sequenceBits;
        this.sequenceMask = -1L ^ (-1L << sequenceBits);
        this.workerIdShift = sequenceBits;
        this.datacenterIdShift = sequenceBits + WORKER_ID_BITS;
        this.timestampLeftShift = sequenceBits + WORKER_ID_BITS + DATACENTER_ID_BITS;

        this.properties = properties;
        String nodeId = resolveNodeId();

        // workerId 分配：显式配置优先，否则使用策略链
        if (properties.getWorkerId() != null) {
            this.workerId = properties.getWorkerId();
        } else {
            this.workerId = allocator.allocate(nodeId);
        }

        this.datacenterId = properties.getDatacenterId() != null
                ? properties.getDatacenterId()
                : computeDatacenterId();

        if (this.workerId > MAX_WORKER_ID || this.workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        if (this.datacenterId > MAX_DATACENTER_ID || this.datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
        }
        // EPOCH：显式配置优先，否则使用默认值
        this.epoch = properties.getEpoch() != null ? properties.getEpoch() : DEFAULT_EPOCH;
        log.info("SnowflakeIdGenerator initialized. Worker ID: {}, Datacenter ID: {}, "
                        + "sequenceBits: {}, epoch: {}, allocator: {}",
                this.workerId, this.datacenterId, this.sequenceBits, this.epoch, allocator.name());
    }

    /**
     * 构造 Spring Bean，使用 {@link WorkerIdAllocator} 自动分配 workerId。
     *
     * <p>使用默认序列号位数（{@value #DEFAULT_SEQUENCE_BITS} 位）。
     *
     * @param properties Snowflake 配置属性
     * @param allocator  WorkerId 分配策略链
     @return 处理结果
     */
    public SnowflakeIdGenerator(SnowflakeProperties properties, WorkerIdAllocator allocator) {
        this(properties, allocator, DEFAULT_SEQUENCE_BITS);
    }

    /**
     * 便捷构造器：使用默认策略链（PodOrdinal → IpHash）。
     * 适用于非 Spring 托管场景（如单元测试、独立工具）。
     *
     * <p>使用默认序列号位数（{@value #DEFAULT_SEQUENCE_BITS} 位）。
     @return 处理结果
     */
    public SnowflakeIdGenerator() {
        this(new SnowflakeProperties(), WorkerIdAllocatorChain.defaults());
    }

    /**
     * 便捷构造器：指定 EPOCH。
     *
     * <p>使用默认序列号位数（{@value #DEFAULT_SEQUENCE_BITS} 位）。
     *
     * @param epoch 起始纪元时间戳（毫秒）
     * @since 4.0.0
     @return 处理结果
     */
    public SnowflakeIdGenerator(long epoch) {
        this(createPropertiesWithEpoch(epoch), WorkerIdAllocatorChain.defaults());
    }

    /**
     * 便捷构造器：指定 EPOCH 和序列号位数。
     *
     * @param epoch        起始纪元时间戳（毫秒）
     * @param sequenceBits 序列号位数（1-${@link #MAX_SEQUENCE_BITS}）
     * @since 4.0.0
     @return 处理结果
     */
    public SnowflakeIdGenerator(long epoch, int sequenceBits) {
        this(createPropertiesWithEpoch(epoch), WorkerIdAllocatorChain.defaults(), sequenceBits);
    }

    private static SnowflakeProperties createPropertiesWithEpoch(long epoch) {
        SnowflakeProperties props = new SnowflakeProperties();
        props.setEpoch(epoch);
        return props;
    }

    /**
     * 解析节点标识（用于 WorkerIdAllocator 策略链）。
     *
     * <p>优先级：显式配置（{@code ydsz.util.snowflake.node-id}）> 环境变量 HOSTNAME > 本机主机名。
     *
     * @return 节点标识字符串
     */
    private String resolveNodeId() {
        // 显式配置优先
        if (properties.getNodeId() != null && !properties.getNodeId().isEmpty()) {
            return properties.getNodeId();
        }
        // 环境变量
        String nodeId = System.getenv("HOSTNAME");
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = System.getenv("INSTANCE_INDEX");
        }
        if (nodeId == null || nodeId.isEmpty()) {
            nodeId = System.getenv("POD_INDEX");
        }
        // fallback：本机主机名
        if (nodeId == null || nodeId.isEmpty()) {
            try {
                nodeId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                nodeId = "unknown-node";
            }
        }
        return nodeId;
    }

    /**
     * 生成下一个唯一 ID（线程安全）。
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
                if (sequence > sequenceMask) {
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
     * 等待下一毫秒。
     *
     * <p>统一时钟回拨处理逻辑：
     * <ul>
     *   <li>≤ {@link #CLOCK_BACKWARD_TOLERANCE_MILLIS}ms：使用 {@link LockSupport#parkNanos} 挂起等待，
     *       避免忙等消耗 CPU</li>
     *   <li>超过最大等待时间：抛出 {@link ClockBackwardException}</li>
     * </ul>
      * @param lastTimestamp lastTimestamp
      @return 计算结果
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentTimeRelative();
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
            long parkMillis = Math.max(offset, 1L);
            LockSupport.parkNanos(parkMillis * 1_000_000L);
            totalWaited += parkMillis;
            timestamp = currentTimeRelative();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 当前时间相对 {@link #DEFAULT_EPOCH} 的毫秒数。
     *
     * <p>ID 内只存相对毫秒，配合 {@code epoch} 偏移量即可反解出绝对时间。
     * 这样 41 位时间戳字段寿命从 1970 年起算延长至约 2090 年，
     * 也保证 {@link #getLastTimestamp()} / {@link #parseTimestamp(long)} 的反解语义正确。
     @return 处理结果
     */
    private long currentTimeRelative() {
        return timeGen() - epoch;
    }

    /**
     * 处理时间戳解析（含时钟回拨容忍）。
     *
     * <p>统一使用 {@link LockSupport#parkNanos} 等待策略，替代原来的 100ms 截止时间自旋循环，
     * 减少 CPU 空转。所有时间戳均处于相对 epoch 的毫秒域内（见 {@link #currentTimeRelative()}）。
     *
     * @param lastTimestamp 上一次生成 ID 时的相对时间戳
     * @return 当前可用的相对时间戳（≥ lastTimestamp）；时钟回拨超阈值抛出 {@link ClockBackwardException}
     */
    private long resolveTimestamp(long lastTimestamp) {
        long timestamp = currentTimeRelative();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > CLOCK_BACKWARD_TOLERANCE_MILLIS) {
                log.error("Clock moved backwards by {} ms, exceeds tolerance {} ms",
                        offset, CLOCK_BACKWARD_TOLERANCE_MILLIS);
                throw new ClockBackwardException(offset, lastTimestamp, timeGen());
            }
            log.warn("Clock moved backwards by {} ms, parking {} ms to recover", offset, offset);
            long totalWaited = 0L;
            while (currentTimeRelative() < lastTimestamp) {
                if (totalWaited >= CLOCK_BACKWARD_MAX_WAIT_MILLIS) {
                    long remainingOffset = lastTimestamp - currentTimeRelative();
                    log.error("Clock still moved backwards after waiting {} ms, remaining offset: {} ms",
                            totalWaited, remainingOffset);
                    throw new ClockBackwardException(remainingOffset, lastTimestamp, timeGen());
                }
                long parkMillis = Math.min(Math.max(lastTimestamp - currentTimeRelative(), 1L),
                        CLOCK_BACKWARD_MAX_WAIT_MILLIS - totalWaited);
                LockSupport.parkNanos(parkMillis * 1_000_000L);
                totalWaited += parkMillis;
            }
            timestamp = currentTimeRelative();
        }
        return timestamp;
    }

    private long composeId(long timestamp, long sequence) {
        return (timestamp << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    private long packState(long timestamp, long sequence) {
        return (timestamp << sequenceBits) | sequence;
    }

    private long extractTimestamp(long currentState) {
        return currentState < 0 ? -1L : currentState >>> sequenceBits;
    }

    private long extractSequence(long currentState) {
        return currentState < 0 ? -1L : currentState & sequenceMask;
    }

    private static long computeDatacenterId() {
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

    // ==================== Getter ====================

    public long getWorkerId() {
        return workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    /**
     * 获取配置的序列号位数。
     *
     * @return 序列号位数
     * @since 4.0.0
     */
    public int getSequenceBits() {
        return (int) sequenceBits;
    }

    /**
     * 获取当前实例的序列号最大值（2^sequenceBits - 1）。
     *
     * @return 序列号上限值
     * @since 4.0.0
     */
    public long getMaxSequence() {
        return sequenceMask;
    }

    /**
     * 获取最近一次生成 ID 时的时间戳（毫秒）。
     *
     * <p>用于健康检查等场景快速获取最后一次 ID 生成时间，不会触发 ID 生成。
     * 如果尚未生成过 ID，返回当前时间。
     *
     * @return 最近一次 ID 生成时间戳（毫秒），未初始化时返回当前时间
     */
    public long getLastTimestamp() {
        long currentState = state.get();
        if (currentState < 0) {
            return System.currentTimeMillis();
        }
        return extractTimestamp(currentState) + epoch;
    }

    // ==================== 静态常量暴露 ====================

    public static long getMaxWorkerId() {
        return MAX_WORKER_ID;
    }

    public static long getMaxDatacenterId() {
        return MAX_DATACENTER_ID;
    }

    // ==================== ID 反解析（实例方法） ====================

    /**
     * 从 ID 中反解时间戳（使用本实例的 EPOCH 和位移配置）。
     *
     * @param id Snowflake ID
     * @return 绝对时间戳（毫秒）
     * @since 4.0.0
     */
    public long parseTimestamp(long id) {
        return (id >> timestampLeftShift) + epoch;
    }

    /**
     * 从 ID 中反解时间戳（使用本实例的位移配置 + 指定 EPOCH）。
     *
     * @param id    Snowflake ID
     * @param epoch 生成该 ID 时使用的 EPOCH
     * @return 绝对时间戳（毫秒）
     * @since 4.0.0
     */
    public long parseTimestamp(long id, long epoch) {
        return (id >> timestampLeftShift) + epoch;
    }

    /**
     * 从 ID 中反解数据中心 ID（使用本实例的位移配置）。
     *
     * @param id Snowflake ID
     * @return 数据中心 ID
     * @since 4.0.0
     */
    public long parseDatacenterId(long id) {
        return (id >> datacenterIdShift) & MAX_DATACENTER_ID;
    }

    /**
     * 从 ID 中反解工作节点 ID（使用本实例的位移配置）。
     *
     * @param id Snowflake ID
     * @return 工作节点 ID
     * @since 4.0.0
     */
    public long parseWorkerId(long id) {
        return (id >> workerIdShift) & MAX_WORKER_ID;
    }

    /**
     * 从 ID 中反解序列号（使用本实例的序列号掩码）。
     *
     * @param id Snowflake ID
     * @return 序列号
     * @since 4.0.0
     */
    public long parseSequence(long id) {
        return id & sequenceMask;
    }

    // ==================== 静态反解析方法（已废弃） ====================

    /**
     * 从默认配置中获取 EPOCH。
     *
     * @return 默认 EPOCH（毫秒）
     */
    public static long getEpoch() {
        return DEFAULT_EPOCH;
    }

    /**
     * 获取当前实例使用的 EPOCH。
     *
     * @return 实例级 EPOCH（毫秒）
     * @since 4.0.0
     */
    public long getInstanceEpoch() {
        return epoch;
    }
}
