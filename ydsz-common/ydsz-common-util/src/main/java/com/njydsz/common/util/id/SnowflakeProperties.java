package com.njydsz.common.util.id;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

/**
 * Snowflake ID 生成器配置属性
 *
 * <p>配置前缀：{@code ydsz.util.snowflake}
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   util:
 *     snowflake:
 *       worker-id: 1         # 可选：显式指定 workerId（最高优先级）
 *       datacenter-id: 0     # 可选：显式指定 datacenterId
 *       node-id: pod-0       # 可选：节点标识（用于 PodOrdinal 策略）
 *       sequence-bits: 7     # 可选：序列号位数（默认 7，最高 13）
 * }</pre>
 *
 * <p><b>workerId 自动分配策略链：</b>
 * <ol>
 *   <li>显式配置：{@code ydsz.util.snowflake.worker-id}</li>
 *   <li>PodOrdinal：{@link PodOrdinalWorkerIdAllocator} 从 StatefulSet 主机名解析序号</li>
 *   <li>IpHash：{@link IpHashWorkerIdAllocator} 基于本地 IPv4 地址哈希</li>
 *   <li>FilePersisted：{@link FilePersistedWorkerIdAllocator} 基于本地文件持久化</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ydsz.util.snowflake")
public class SnowflakeProperties {

    /**
     * 是否启用 Snowflake 自动配置。
     * <p>默认 true；设置为 false 时可禁用 {@link SnowflakeIdGenerator} 的注册。
     */
    private boolean enabled = true;

    /**
     * 工作节点ID（显式配置，最高优先级）。
     * <p>范围：0-1023；不设置时由 {@link WorkerIdAllocator} 策略链自动分配。
     */
    @Min(0)
    @Max(1023)
    private Long workerId;

    /**
     * 数据中心ID（显式配置，不设时自动计算机器名哈希取模）。
     * <p>范围：0-31
     */
    @Min(0)
    @Max(31)
    private Long datacenterId;

    /**
     * 节点标识（用于 PodOrdinal/IFilePersisted 策略）。
     * <p>不设置时自动解析 HOSTNAME 环境变量或 InetAddress.getLocalHost()。
     */
    private String nodeId;

    /**
     * 起始纪元时间戳（毫秒），即 Snowflake ID 中时间戳字段的起算点。
     * <p>默认值：{@code 1577836800000}（2020-01-01 00:00:00 UTC）。
     * <p>修改 EPOCH 会影响 ID 的数值范围，请确保集群内所有节点使用相同的 EPOCH。
     * <p>注意：修改 EPOCH 后，新生成的 ID 与旧 EPOCH 生成的 ID 不保证连续，
     * 且反解时间戳时需使用与生成时相同的 EPOCH 才能得到正确结果。
     *
     * @since 4.0.0
     */
    private Long epoch;

    /**
     * 序列号占用位数。
     * <p>默认 {@value SnowflakeIdGenerator#DEFAULT_SEQUENCE_BITS} 位（每毫秒 128 个 ID）。
     * <p>最高 {@value SnowflakeIdGenerator#MAX_SEQUENCE_BITS} 位（每毫秒 8192 个 ID）。
     * <p>增大此值可提升并发吞吐，但会压缩时间戳字段，缩短 ID 可用年限。
     *
     * @since 4.0.0
     */
    @Min(1)
    @Max(13)
    private Integer sequenceBits;
}
