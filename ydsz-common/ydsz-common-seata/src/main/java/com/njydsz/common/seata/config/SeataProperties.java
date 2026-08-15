package com.njydsz.common.seata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.seata.api.TransactionType;

import lombok.Getter;
import lombok.Setter;

/**
 * 分布式事务配置属性
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
@ConfigurationProperties(prefix = "ydsz.seata")
public class SeataProperties {

    // ============= 基础配置 =============

    /** 是否启用分布式事务 */
    private boolean enabled = true;

    /** 默认事务类型 */
    private TransactionType defaultType = TransactionType.LOCAL;

    /** Seata 应用 ID */
    private String seataApplicationId = "ydsz-app";

    /** Seata 事务组 */
    private String seataTxServiceGroup = "ydsz-tx-group";

    // ============= 分模式开关（P2-6） =============

    /** 是否启用 TCC 模式 */
    private boolean tccEnabled = true;

    /** 是否启用 SAGA 模式 */
    private boolean sagaEnabled = true;

    /** 是否启用 Seata AT 模式 */
    private boolean seataAtEnabled = true;

    // ============= TCC 配置 =============

    /** TCC 补偿重试次数 */
    private int tccRetryCount = 3;

    /** TCC 补偿重试间隔（毫秒） */
    private long tccRetryIntervalMs = 1000;

    /** TCC Try 阶段超时时间（毫秒），0 表示不限制 */
    private long tccTryTimeoutMs = 60000;

    // ============= TCC 事务日志存储配置（P1-4） =============

    /**
     * TCC 事务日志存储类型：
     * <ul>
     *   <li>{@code memory} - 内存实现（默认，单机/测试）</li>
     *   <li>{@code redis} - Redis 实现（生产环境，跨服务共享）</li>
     *   <li>{@code db} - 数据库实现（生产环境，强持久化，无需 Redis）</li>
     * </ul>
     * 仅当选择 {@code redis} 且类路径存在 {@code RedisTemplate} 时才注册 Redis 实现；
     * 仅当选择 {@code db} 且类路径存在 {@code JdbcTemplate} 时才注册 DB 实现；
     * 否则回退到 {@code memory}。
     */
    private TccLogStoreType tccLogStore = TccLogStoreType.MEMORY;

    /** TCC 日志 Redis key 前缀（仅当 tcc-log-store=redis 时生效） */
    private String tccLogRedisKeyPrefix = "ydsz:tcc:log:";

    /** TCC 日志 Redis 保留时长（小时，仅当 tcc-log-store=redis 时生效） */
    private int tccLogRedisRetentionHours = 24;

    /** TCC 日志 DB 存储时使用的表名 */
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

    // ============= SAGA 配置 =============

    /** SAGA 最大重试次数 */
    private int sagaMaxRetries = 5;

    /** SAGA 重试间隔（毫秒） */
    private long sagaRetryIntervalMs = 2000;

    /** SAGA 事务超时时间（毫秒），0 表示不限制 */
    private long sagaTimeoutMs = 300000;

    // ============= 事务恢复配置（P0-11） =============

    /** 事务恢复扫描间隔（毫秒） */
    private long recoveryScanIntervalMs = 10000;

    /** 事务超时判定阈值（毫秒），超过此时间未 Confirm/Cancel 的事务将被恢复扫描处理 */
    private long recoveryTimeoutThresholdMs = 60000;

    /**
     * 恢复扫描单次处理最大事务数（P1-2 新增）
     *
     * <p>限制单次扫描循环处理的最大事务数量，避免一次处理过多导致：
     * <ul>
     *   <li>JVM 暂停时间过长，影响服务可用性</li>
     *   <li>长事务持有分布式锁，阻塞其他节点</li>
     * </ul>
     */
    private int recoveryBatchSize = 100;

    /**
     * 恢复扫描是否启用分页模式（P1-2 新增）
     *
     * <p>启用后每次扫描仅处理 {@link #recoveryBatchSize} 条记录，
     * 下次扫描从上次结束位置继续，渐进式处理所有超时事务。
     * 避免一次性加载全部超时事务到内存。
     */
    private boolean recoveryPagedMode = true;
}
