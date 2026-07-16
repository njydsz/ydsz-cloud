package com.njydsz.common.seata.api;

/**
 * 分布式事务类型枚举
 *
 * @author ydsz-team
 * @since 3.5.0
 */
public enum TransactionType {

    /** Seata AT 模式 - 自动补偿型 */
    SEATA_AT,

    /** TCC 模式 - Try-Confirm-Cancel */
    TCC,

    /** SAGA 模式 - 长事务编排 */
    SAGA,

    /** 本地事务 - 降级模式 */
    LOCAL
}
