package com.njydsz.common.seata.config;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.config.validator.ValidXidSignConfig;

import lombok.Getter;
import lombok.Setter;

/**
 * 分布式事务配置属性
 *
 * <p><b>P2-2 修复</b>：增加 JSR-303 参数校验注解，在应用启动时即验证配置合法性，
 * 避免运行时才发现配置错误导致事务异常。
 *
 * <p>校验规则：
 * <ul>
 *   <li>{@code tcc-retry-count} / {@code saga-max-retries} ∈ [0, 10]</li>
 *   <li>{@code tcc-retry-interval-ms} ≥ 100（避免重试过于频繁）</li>
 *   <li>{@code recovery-batch-size} ∈ [1, 1000]</li>
 *   <li>{@code recovery-timeout-threshold-ms} ≥ 5000（避免误扫描刚创建的事务）</li>
 *   <li>{@code xid-sign-enabled=true} 时 {@code xid-sign-key} 不能为空且长度 ≥ 16</li>
 * </ul>
 *
 * <p><b>P2-7 修复</b>：补充 Seata 2.x 客户端关键配置参数，使业务可通过 application.yml 调整。
 *
 * <p><b>P1-4 修复</b>：新增事务超时配置，TCC Try 阶段超时后自动 Cancel。
 *
 * <p><b>P2-6 修复</b>：新增 per-mode 开关，可独立关闭 TCC 或 SAGA。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@ValidXidSignConfig
@ConfigurationProperties(prefix = "ydsz.seata")
public class SeataProperties {

    // ============= 基础配置 =============

    /** 是否启用分布式事务 */
    private boolean enabled = true;

    /** 默认事务类型 */
    @NotNull(message = "ydsz.seata.default-type 不能为空")
    private TransactionType defaultType = TransactionType.LOCAL;

    /** Seata 应用 ID */
    @NotBlank(message = "ydsz.seata.seata-application-id 不能为空")
    private String seataApplicationId = "ydsz-app";

    /** Seata 事务组 */
    @NotBlank(message = "ydsz.seata.seata-tx-service-group 不能为空")
    private String seataTxServiceGroup = "ydsz-tx-group";

    // ============= 分模式开关（P2-6） =============

    /** 是否启用 TCC 模式 */
    private boolean tccEnabled = true;

    /** 是否启用 SAGA 模式 */
    private boolean sagaEnabled = true;

    /** 是否启用 Seata AT 模式 */
    private boolean seataAtEnabled = true;

    // ============= TCC 配置 =============

    /**
     * TCC 补偿重试次数
     *
     * <p>范围：0-10，默认 3。设置为 0 表示不重试，适用于幂等性由业务保证的场景。
     */
    @Min(value = 0, message = "ydsz.seata.tcc-retry-count 不能小于 0")
    @Max(value = 10, message = "ydsz.seata.tcc-retry-count 不能大于 10")
    private int tccRetryCount = 3;

    /**
     * TCC 补偿重试间隔（毫秒）
     *
     * <p>最小值 100ms，避免过于频繁的重试导致下游服务过载。
     */
    @Min(value = 100, message = "ydsz.seata.tcc-retry-interval-ms 不能小于 100ms")
    private long tccRetryIntervalMs = 1000;

    /**
     * TCC Try 阶段超时时间（毫秒），0 表示不限制
     *
     * <p>建议设置为业务正常执行时间的 1.5-2 倍，避免误触发 Cancel。
     */
    @Min(value = 0, message = "ydsz.seata.tcc-try-timeout-ms 不能小于 0")
    private long tccTryTimeoutMs = 60000;

    // ============= TCC 事务日志存储配置（P1-4） =============

    /**
     * TCC 事务日志存储类型：
     * <ul>
     *   <li>{@code memory} - 内存实现（默认，单机/测试）</li>
     *   <li>{@code redis} - Redis 实现（生产环境，跨服务共享）</li>
     *   <li>{@code db} - 数据库实现（生产环境，强持久化，无需 Redis）</li>
     * </ul>
     */
    @NotNull(message = "ydsz.seata.tcc-log-store 不能为空")
    private TccLogStoreType tccLogStore = TccLogStoreType.MEMORY;

    /** TCC 日志 Redis key 前缀（仅当 tcc-log-store=redis 时生效） */
    private String tccLogRedisKeyPrefix = "ydsz:tcc:log:";

    /**
     * TCC 日志 Redis 保留时长（小时，仅当 tcc-log-store=redis 时生效）
     *
     * <p>范围：1-720（30天），默认 24。
     */
    @Min(value = 1, message = "ydsz.seata.tcc-log-redis-retention-hours 不能小于 1")
    @Max(value = 720, message = "ydsz.seata.tcc-log-redis-retention-hours 不能大于 720")
    private int tccLogRedisRetentionHours = 24;

    /** TCC 日志 DB 存储时使用的表名 */
    @NotBlank(message = "ydsz.seata.tcc-log-db-table 不能为空")
    private String tccLogDbTable = "tcc_transaction_log";

    /** TCC 日志 DB 存储时的架构名（可选，null 使用默认） */
    private String tccLogDbSchema = null;

    /**
     * TCC 日志 DB 存储时的数据库方言（P0-3 新增）
     *
     * <p>支持的值：
     * <ul>
     *   <li>{@code mysql} - MySQL / MariaDB（使用 ON DUPLICATE KEY UPDATE）</li>
     *   <li>{@code postgresql} - PostgreSQL 9.5+（使用 ON CONFLICT DO UPDATE）</li>
     * </ul>
     * 为空或null时自动根据数据源元数据检测。
     */
    private String tccLogDbDialect = null;

    // ============= XID 签名配置（P0-4） =============

    /**
     * XID 签名开关（P0-4 新增）
     *
     * <p>开启后，XID 跨服务传播时携带 HMAC-SHA256 签名，
     * 下游服务验证签名有效后才绑定到上下文，防止 XID 伪造注入。
     *
     * <p>生产环境建议开启，配置 {@link #xidSignKey} 为强密钥。
     */
    private boolean xidSignEnabled = false;

    /**
     * XID 签名密钥（当 xid-sign-enabled=true 时必填）
     *
     * <p>建议使用 32 字节以上的随机字符串，通过环境变量或配置中心注入，
     * 确保所有参与服务的密钥一致。
     */
    private String xidSignKey = null;

    // ============= SAGA 配置 =============

    /**
     * SAGA 最大重试次数
     *
     * <p>范围：0-10，默认 5。设置为 0 表示不重试。
     */
    @Min(value = 0, message = "ydsz.seata.saga-max-retries 不能小于 0")
    @Max(value = 10, message = "ydsz.seata.saga-max-retries 不能大于 10")
    private int sagaMaxRetries = 5;

    /**
     * SAGA 重试间隔（毫秒）
     *
     * <p>最小值 100ms，避免过于频繁。
     */
    @Min(value = 100, message = "ydsz.seata.saga-retry-interval-ms 不能小于 100ms")
    private long sagaRetryIntervalMs = 2000;

    /**
     * SAGA 事务超时时间（毫秒），0 表示不限制
     *
     * <p>建议根据业务 SLA 设置，默认 5 分钟。
     */
    @Min(value = 0, message = "ydsz.seata.saga-timeout-ms 不能小于 0")
    private long sagaTimeoutMs = 300000;

    // ============= 事务恢复配置（P0-11） =============

    /**
     * 事务恢复扫描间隔（毫秒）
     *
     * <p>最小值 1000ms，避免过于频繁的扫描影响性能。
     */
    @Min(value = 1000, message = "ydsz.seata.recovery-scan-interval-ms 不能小于 1000ms")
    private long recoveryScanIntervalMs = 10000;

    /**
     * 事务超时判定阈值（毫秒），超过此时间未 Confirm/Cancel 的事务将被恢复扫描处理
     *
     * <p>最小值 5000ms，避免误扫描刚创建的事务。
     */
    @Min(value = 5000, message = "ydsz.seata.recovery-timeout-threshold-ms 不能小于 5000ms")
    private long recoveryTimeoutThresholdMs = 60000;

    /**
     * 恢复扫描单次处理最大事务数（P1-2 新增）
     *
     * <p>限制单次扫描循环处理的最大事务数量，避免一次处理过多导致：
     * <ul>
     *   <li>JVM 暂停时间过长，影响服务可用性</li>
     *   <li>长事务持有分布式锁，阻塞其他节点</li>
     * </ul>
     * <p>范围：1-1000，默认 100。
     */
    @Min(value = 1, message = "ydsz.seata.recovery-batch-size 不能小于 1")
    @Max(value = 1000, message = "ydsz.seata.recovery-batch-size 不能大于 1000")
    private int recoveryBatchSize = 100;

    /**
     * 恢复扫描是否启用分页模式（P1-2 新增）
     *
     * <p>启用后每次扫描仅处理 {@link #recoveryBatchSize} 条记录，
     * 下次扫描从上次结束位置继续，渐进式处理所有超时事务。
     * 避免一次性加载全部超时事务到内存。
     */
    private boolean recoveryPagedMode = true;

    // ============= 按事务名称的差异化超时配置（P2-7） =============

    /**
     * 按事务名称配置的超时覆盖（可选）
     *
     * <p>key 为事务名称（与 XID 前缀匹配），value 为超时时间毫秒。
     * 例如：
     * <pre>{@code
     * ydsz.seata.tx-timeout-overrides:
     *   order-create: 120000
     *   inventory-deduct: 30000
     * }</pre>
     */
    private Map<String, Long> txTimeoutOverrides = new HashMap<>();

    /**
     * TCC 事务日志存储类型。
     *
     * <p>决定 TCC 一阶段确认信息持久化在哪里，直接影响分布式事务的恢复能力：
     * 生产环境跨服务共享时必须选择 {@link #REDIS} 或 {@link #DB}，否则事务恢复扫描无法跨实例工作。
     */
    public enum TccLogStoreType {
        /** 内存实现（单机/测试） */
        MEMORY,
        /** Redis 实现（生产环境，跨服务共享） */
        REDIS,
        /** 数据库实现（生产环境，强持久化，无需 Redis） */
        DB
    }
}
