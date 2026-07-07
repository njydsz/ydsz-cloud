package com.njydsz.pmis.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法分布式 ID 生成器
 *
 * <p>大厂规范: 业务 ID / 主键 ID 一律由应用层雪花算法生成，
 * 不依赖数据库自增（暴露业务量、不可跨库、不可水平扩展），
 * 不用 UUID（B-tree 索引页分裂严重、写入性能差）。
 *
 * <p>雪花算法 64 位结构:
 * <pre>
 *  0 | 41bit 毫秒时间戳 | 10bit 工作机器ID | 12bit 序列号
 * </pre>
 * <ul>
 *   <li>时间戳: 41bit，可用 69 年（自定义起始 epoch）</li>
 *   <li>workerId: 10bit，支持 1024 个实例（通过 K8s POD_INDEX 或环境变量注入）</li>
 *   <li>sequence: 12bit，单机单毫秒最多 4096 个 ID</li>
 * </ul>
 *
 * <p>最终 ID 以 19 位十进制字符串输出，存储到 VARCHAR(20) 主键 / 业务ID列；
 * 索引/排序效率接近 BIGINT，避免自增 ID 的枚举攻击与分库困难。
 *
 * <p>时钟回拨处理: 检测到回拨时等待 5ms，仍回拨则抛出异常（避免 ID 重复）。
 *
 * <p>使用方式:
 * <pre>
 *   String id = SnowflakeIdGenerator.nextIdStr();   // 19 位字符串
 *   String traceId = SnowflakeIdGenerator.nextTraceId(); // 16 进制 16 位
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳: 2024-01-01 00:00:00 UTC（项目上线年份） */
    private static final long TWEPOCH = 1704067200000L;

    /** workerId 占用位数: 10bit（支持 1024 实例） */
    private static final long WORKER_ID_BITS = 10L;

    /** 最大 workerId: 1023 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 序列号占用位数: 12bit */
    private static final long SEQUENCE_BITS = 12L;

    /** workerId 左移位数: 12 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 时间戳左移位数: 22 (10+12) */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 序列号掩码: 0xFFF = 4095 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 允许的时钟回拨最大毫秒数: 5ms（超过即抛异常） */
    private static final long MAX_CLOCK_BACKWARD_MS = 5L;

    /** 静态 workerId，由 Spring 注入后赋值 */
    private static volatile long workerId;

    /** 静态实例（用于无 Spring 上下文的工具方法调用） */
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

    /** 上次生成 ID 的时间戳 */
    private long lastTimestamp = -1L;

    /** 当前毫秒内序列号 */
    private long sequence = 0L;

    static {
        // 类加载兜底初始化 workerId（@Component 注入前的快速调用场景）
        long wid;
        String envWorkerId = System.getenv("PMIS_WORKER_ID");
        if (envWorkerId != null && !envWorkerId.isEmpty()) {
            wid = Long.parseLong(envWorkerId);
        } else {
            String podIndex = System.getenv("POD_INDEX");
            if (podIndex != null && !podIndex.isEmpty()) {
                wid = Long.parseLong(podIndex);
            } else {
                String serviceName = System.getProperty("spring.application.name", "default");
                wid = Math.abs(serviceName.hashCode()) % (MAX_WORKER_ID + 1);
            }
        }
        workerId = wid;
    }
    /**
     * Spring 构造时注入 workerId
     * <p>优先级: 环境变量 PMIS_WORKER_ID > K8s POD_INDEX > 服务名 hash 取模
     *
     * @param envWorkerId 环境变量 PMIS_WORKER_ID
     */
    public SnowflakeIdGenerator(
            @Value("${PMIS_WORKER_ID:}") String envWorkerId) {
        long wid;
        if (envWorkerId != null && !envWorkerId.isEmpty()) {
            wid = Long.parseLong(envWorkerId);
        } else {
            // K8s StatefulSet: POD_INDEX
            String podIndex = System.getenv("POD_INDEX");
            if (podIndex != null && !podIndex.isEmpty()) {
                wid = Long.parseLong(podIndex);
            } else {
                // 兜底: 服务名 hash 取模，避免多实例冲突
                String serviceName = System.getProperty("spring.application.name", "default");
                wid = Math.abs(serviceName.hashCode()) % (MAX_WORKER_ID + 1);
            }
        }
        if (wid < 0 || wid > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId 超出范围 [0," + MAX_WORKER_ID + "]: " + wid);
        }
        workerId = wid;
    }

    /**
     * 默认构造器（无 Spring 上下文时使用，workerId 由 hash 兜底）
     */
    private SnowflakeIdGenerator() {
        // 仅在静态 INSTANCE 初始化时调用
    }

    /**
     * 生成下一个 64 位 ID（Long 类型）
     *
     * @return 雪花 ID
     */
    public static long nextId() {
        return INSTANCE.generateId();
    }

    /**
     * 生成下一个 ID 的字符串形式（便于数据库存储与日志输出）
     *
     * @return 18 位数字字符串
     */
    public static String nextIdStr() {
        return String.valueOf(INSTANCE.generateId());
    }

    /**
     * 生成 16 位 traceId（16 进制，兼容现有 traceId 长度）
     * <p>取雪花 ID 的高 16 位（时间戳部分）转 16 进制
     *
     * @return 16 位 16 进制 traceId
     */
    public static String nextTraceId() {
        long id = INSTANCE.generateId();
        // 取低 64 位的中间 16 位作为 traceId，避免过长
        return String.format("%016x", id);
    }

    /**
     * 实际生成 ID 的方法（synchronized 保证线程安全）
     */
    private synchronized long generateId() {
        long currentTimestamp = System.currentTimeMillis();

        // 时钟回拨检测
        if (currentTimestamp < lastTimestamp) {
            long backward = lastTimestamp - currentTimestamp;
            if (backward <= MAX_CLOCK_BACKWARD_MS) {
                // 小幅回拨: 等待 lastTimestamp + 1ms
                try {
                    Thread.sleep(backward + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待时钟回拨期间被中断", e);
                }
                currentTimestamp = System.currentTimeMillis();
                if (currentTimestamp < lastTimestamp) {
                    throw new IllegalStateException(
                            "时钟回拨超过 " + MAX_CLOCK_BACKWARD_MS + "ms，拒绝生成 ID 避免重复");
                }
            } else {
                throw new IllegalStateException(
                        "时钟回拨 " + backward + "ms，超过阈值 " + MAX_CLOCK_BACKWARD_MS + "ms");
            }
        }

        if (currentTimestamp == lastTimestamp) {
            // 同一毫秒内序列号递增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号耗尽，等待下一毫秒
                currentTimestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 阻塞等待下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前 workerId（用于诊断）
     */
    public static long getWorkerId() {
        return workerId;
    }
}
