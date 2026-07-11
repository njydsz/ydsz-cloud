package com.njydsz.pmis.message.domain.enums.batch;


/**
 * 聚合批次状态枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AggregateBatchStatusEnum {

    /** 攒批中 */
    PENDING,
    /** 就绪待发 */
    READY,
    /** 已发送 */
    SENT,
    /** 已取消 */
    CANCELLED;

    /**
     * 校验状态流转是否合法。
     *
     * @param target 目标状态
     * @return true 表示允许流转
     */
    public boolean canTransitTo(AggregateBatchStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == READY || target == CANCELLED;
            case READY -> target == SENT || target == CANCELLED;
            case SENT, CANCELLED -> false;
        };
    }
}
