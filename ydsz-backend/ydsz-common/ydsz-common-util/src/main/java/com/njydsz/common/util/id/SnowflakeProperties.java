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
 *       worker-id: 1
 *       datacenter-id: 0
 *       worker-id-source: CONFIG
 * }</pre>
 *
 * <p><b>配置说明：</b>
 * <ul>
 *   <li>workerId：工作节点ID，范围 0-31</li>
 *   <li>datacenterId：数据中心ID，范围 0-31</li>
 *   <li>workerIdSource：workerId 来源策略
 *     <ul>
 *       <li>ENVIRONMENT_VARIABLE：从环境变量 YDSZ_SNOWFLAKE_WORKER_ID 读取（默认）</li>
 *       <li>CONFIG：从配置文件读取</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ydsz.util.snowflake")
public class SnowflakeProperties {

    /** WorkerId 环境变量名（与 SnowflakeUtils 保持一致） */
    public static final String WORKER_ID_ENV_VAR = "YDSZ_SNOWFLAKE_WORKER_ID";

    /**
     * 工作节点ID
     * <p>范围：0-31，仅在 workerIdSource 为 CONFIG 时生效
     */
    @Min(0)
    @Max(31)
    private Long workerId;

    /**
     * 数据中心ID
     * <p>范围：0-31
     */
    @Min(0)
    @Max(31)
    private Long datacenterId;

    /**
     * workerId 来源策略
     * <p>默认从环境变量读取
     */
    private WorkerIdSource workerIdSource = WorkerIdSource.ENVIRONMENT_VARIABLE;

    /**
     * WorkerId 租约时间（毫秒）
     * <p>仅在使用 WorkerIdRegistry（分布式注册中心）时生效。
     * <p>租约到期前一半时间点自动续约，未续约则 WorkerId 可被其他实例抢占。
     * <p>默认值：300000（5 分钟）
     */
    private long leaseMillis = 300_000L;

    /**
     * workerId 来源策略枚举
     */
    public enum WorkerIdSource {
        /**
         * 从环境变量读取 workerId
         */
        ENVIRONMENT_VARIABLE,

        /**
         * 从配置文件读取 workerId
         */
        CONFIG
    }
}
