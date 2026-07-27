package com.njydsz.common.core.trace;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

/**
 * 基于 Snowflake 算法的有序 TraceId 生成器。
 *
 * <p>生成格式为 {@code 16 位十六进制字符串}，结构如下：
 * <ul>
 *   <li>时间戳（毫秒级，自定义纪元起算）— 41 bit，高 11 位十六进制字符</li>
 *   <li>工作节点 ID — 10 bit（5 bit datacenterId + 5 bit workerId）</li>
 *   <li>序列号 — 12 bit，同一毫秒内递增</li>
 * </ul>
 *
 * <p>相比 {@link TraceIdGenerator} 的 UUID 方案，本实现生成的 TraceId
 * <b>按时间有序</b>，可直接按 traceId 排序还原请求时序，便于日志排查。</p>
 *
 * <p><b>线程安全性：</b>序列号分配使用 synchronized 保证线程安全。</p>
 *
 * <p><b>时钟回拨处理：</b>检测到时钟回拨时，等待回拨时间结束再继续生成，
 * 避免产生重复 ID。回拨超过 5 秒时抛出异常。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TraceIdSupplier
 */
public class SnowflakeTraceIdSupplier implements TraceIdSupplier {

    /** 自定义纪元：2024-01-01 00:00:00 UTC（毫秒） */
    private static final long TWEPOCH = 1704067200000L;

    /** 工作节点 ID 位数 */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心 ID 位数 */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 最大工作节点 ID */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 最大数据中心 ID */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 序列号位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 工作节点 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心 ID 左移位数 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 序列号掩码 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 最大时钟回拨容忍时间（毫秒） */
    private static final long MAX_BACKWARD_MS = 5000L;

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 使用自动推导的 workerId 和 datacenterId 创建实例。
     *
     * <p>workerId 基于 PID 推导，datacenterId 基于本机 IP 推导。
     * 适用于单机或容器环境。</p>
     */
    public SnowflakeTraceIdSupplier() {
        this(deriveDatacenterId(), deriveWorkerId());
    }

    /**
     * 使用指定的 workerId 和 datacenterId 创建实例。
     *
     * @param workerId     工作节点 ID（0 ~ 31）
     * @param datacenterId 数据中心 ID（0 ~ 31）
     */
    public SnowflakeTraceIdSupplier(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "datacenterId must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    @Override
    public synchronized String generate() {
        long currentTimestamp = timeGen();

        if (currentTimestamp < lastTimestamp) {
            long offset = lastTimestamp - currentTimestamp;
            if (offset <= MAX_BACKWARD_MS) {
                try {
                    wait(offset);
                    currentTimestamp = timeGen();
                    if (currentTimestamp < lastTimestamp) {
                        throw new IllegalStateException(
                                "Clock moved backwards after waiting: offset=" + offset);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for clock", e);
                }
            } else {
                throw new IllegalStateException(
                        "Clock moved backwards beyond tolerance: offset=" + offset + "ms");
            }
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        long id = ((currentTimestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;

        return String.format("%016x", id);
    }

    /**
     * 阻塞等待直到下一毫秒
     *
     * <p>当同一毫秒内的序列号耗尽（超过 4096 个）时调用，
     * 自旋直到时间戳推进到下一毫秒再继续生成。
     *
     * @param lastTimestamp 上次生成的时间戳
     * @return 推进后的新时间戳
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return 当前时间戳
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 从进程 PID 推导 workerId（0 ~ 31）
     *
     * <p>相同 PID 多次调用结果一致，进程重启后 PID 变化不冲突。
     * 解析失败时降级返回 0。
     *
     * @return workerId
     */
    private static long deriveWorkerId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        try {
            long pid = Long.parseLong(name.split("@")[0]);
            return pid % (MAX_WORKER_ID + 1);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 从本机 IP 推导 datacenterId（0 ~ 31）
     *
     * <p>取本机非回环 IPv4 地址的最后一字节的低 5 位。
     * 解析失败时降级返回 0。
     *
     * @return datacenterId
     */
    private static long deriveDatacenterId() {
        try {
            InetAddress address = getLocalAddress();
            if (address != null) {
                byte[] bytes = address.getAddress();
                return (bytes[bytes.length - 1] & 0x1F);
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return 0L;
    }

    /**
     * 获取本机非回环 IP 地址
     *
     * <p>优先返回 site-local 地址（如 192.168.x.x），其次任意非回环地址，
     * 最后降级为 {@link InetAddress#getLocalHost()}。
     *
     * @return 本机 IP 地址；解析失败时返回 null
     */
    private static InetAddress getLocalAddress() {
        try {
            InetAddress candidate = null;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress()) {
                        if (addr.isSiteLocalAddress()) {
                            return addr;
                        }
                        if (candidate == null) {
                            candidate = addr;
                        }
                    }
                }
            }
            if (candidate != null) {
                return candidate;
            }
            return InetAddress.getLocalHost();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost();
            } catch (UnknownHostException ex) {
                return null;
            }
        }
    }
}
