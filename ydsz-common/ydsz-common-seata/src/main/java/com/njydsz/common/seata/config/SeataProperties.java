package com.njydsz.common.seata.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分布式事务（Seata）配置属性
 *
 * <p>配置前缀：{@code ydsz.seata}
 *
 * <p><b>模式说明：</b>
 *
 * <ul>
 *   <li>{@code AT} — 自动事务模式（默认），基于 undo_log 反向补偿，无需编写回滚代码，注解 {@code @GlobalTransactional} 即启用</li>
 *   <li>{@code TCC} — 手动补偿模式，需实现 Try/Confirm/Cancel 三阶段，适用于高性能和跨资源类型事务</li>
 *   <li>{@code SAGA} — 长事务模式，适用于微服务编排场景</li>
 *   <li>{@code XA} — 强一致性模式，基于 XA 两阶段提交协议</li>
 * </ul>
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   seata:
 *     enabled: true
 *     application-id: ${spring.application.name}
 *     tx-service-group: default_tx_group
 *     data-source-proxy-mode: AT
 *     enable-auto-data-source-proxy: true
 *     client:
 *       rm:
 *         async-commit-buffer-limit: 10000
 *         report-retry-count: 5
 *         meta-report-enabled: false
 *       tm:
 *         commit-retry-count: 5
 *         rollback-retry-count: 5
 *     undo-log:
 *       table-name: undo_log
 *       serialization: jackson
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.seata")
public class SeataProperties {

    /** 是否启用 Seata 分布式事务（默认 false，按需开启） */
    private boolean enabled = false;

    /** 应用 ID（默认与 spring.application.name 一致） */
    private String applicationId = "${spring.application.name}";

    /** 事务服务分组（对应 Seata Server 的 vgroupMapping 配置） */
    private String txServiceGroup = "default_tx_group";

    /** 数据源代理模式：AT / TCC / SAGA / XA */
    private String dataSourceProxyMode = "AT";

    /** 是否自动创建数据源代理（AT 模式必须为 true） */
    private boolean enableAutoDataSourceProxy = true;

    /** disableGlobalTransaction 全局开关（Seata 原生属性兼容） */
    private boolean disableGlobalTransaction = false;

    /** UndoLog 配置 */
    private UndoLog undoLog = new UndoLog();

    /** RM（Resource Manager）配置 */
    private Rm rm = new Rm();

    /** TM（Transaction Manager）配置 */
    private Tm tm = new Tm();

    /**
     * UndoLog 配置子属性
     */
    @Getter
    @Setter
    public static class UndoLog {

        /** undo_log 表名 */
        private String tableName = "undo_log";

        /** undo_log 序列化方式（jackson / kryo / fastjson / protobuf） */
        private String serialization = "jackson";

        /** 仅特定列存入 undo_log：true=只序列化修改字段，false=整行的前后镜像 */
        private boolean onlyCareUpdateColumns = true;
    }

    /**
     * RM 配置子属性
     */
    @Getter
    @Setter
    public static class Rm {

        /** 异步提交缓冲区大小（默认 10000） */
        private int asyncCommitBufferLimit = 10000;

        /** SQL 结果集行数限制（超出后触发降级跳过） */
        private int reportRetryCount = 5;

        /** undo_log 元数据定期上报 Server（默认 false，减少网络开销） */
        private boolean metaReportEnabled = false;

        /** lock 锁重试次数 */
        private int lockRetryTimes = 30;

        /** lock 锁重试间隔（毫秒） */
        private int lockRetryInternal = 10;
    }

    /**
     * TM 配置子属性
     */
    @Getter
    @Setter
    public static class Tm {

        /** 全局事务提交重试次数 */
        private int commitRetryCount = 5;

        /** 全局事务回滚重试次数 */
        private int rollbackRetryCount = 5;

        /** 全局事务超时时间（毫秒，默认 60s） */
        private int globalTransactionTimeout = 60000;
    }
}
