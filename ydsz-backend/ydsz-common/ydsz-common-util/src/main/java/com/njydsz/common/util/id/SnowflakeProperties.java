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
 *       environment-variable-name: YDSZ_SNOWFLAKE_WORKER_ID
 * }</pre>
 *
 * <p><b>配置说明：</b>
 * <ul>
 *   <li>workerId：工作节点ID，范围 0-31</li>
 *   <li>datacenterId：数据中心ID，范围 0-31</li>
 *   <li>workerIdSource：workerId 来源策略
 *     <ul>
 *       <li>ENVIRONMENT_VARIABLE：从环境变量读取（默认）</li>
 *       <li>CONFIG：从配置文件读取</li>
 *       <li>INSTANCE_INDEX：从 Spring Cloud 实例索引读取</li>
 *     </ul>
 *   </li>
 *   <li>environmentVariableName：环境变量名，默认 YDSZ_SNOWFLAKE_WORKER_ID</li>
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
     * 环境变量名
     * <p>当 workerIdSource 为 ENVIRONMENT_VARIABLE 时使用
     * <p>默认值：YDSZ_SNOWFLAKE_WORKER_ID
     */
    private String environmentVariableName = "YDSZ_SNOWFLAKE_WORKER_ID";

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
        CONFIG,

        /**
         * 从 Spring Cloud 实例索引读取 workerId
         * <p>适用于云原生环境（如 Cloud Foundry、Kubernetes）
         */
        INSTANCE_INDEX
    }
}
