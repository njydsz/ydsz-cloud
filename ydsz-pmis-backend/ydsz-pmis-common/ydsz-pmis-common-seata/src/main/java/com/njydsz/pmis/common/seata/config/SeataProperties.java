package com.njydsz.pmis.common.seata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.pmis.common.seata.api.TransactionType;

import lombok.Getter;
import lombok.Setter;

/**
 * 分布式事务配置属性
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pmis.seata")
public class SeataProperties {

    /** 是否启用分布式事务 */
    private boolean enabled = true;

    /** 默认事务类型 */
    private TransactionType defaultType = TransactionType.LOCAL;

    /** Seata 应用 ID */
    private String seataApplicationId = "pmis-app";

    /** Seata 事务组 */
    private String seataTxServiceGroup = "pmis-tx-group";

    /** TCC 补偿重试次数 */
    private int tccRetryCount = 3;

    /** TCC 补偿重试间隔（毫秒） */
    private long tccRetryIntervalMs = 1000;

    /** SAGA 最大重试次数 */
    private int sagaMaxRetries = 5;

    /** SAGA 重试间隔（毫秒） */
    private long sagaRetryIntervalMs = 2000;
}
