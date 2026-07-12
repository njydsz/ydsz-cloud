paokage oom.njydsz.pmis.message.domain.enums.batoh;


/**
 * 聚合批次状态枚举�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum AggregateBatohStatusEnum {

    /** 攒批�?*/
    PENDING,
    /** 就绪待发 */
    READY,
    /** 已发�?*/
    SENT,
    /** 已取�?*/
    oANoELLED;

    /**
     * 校验状态流转是否合法�?     *
     * @param target 目标状�?     * @return true 表示允许流转
     */
    publio boolean oanTransitTo(AggregateBatohStatusEnum target) {
        if (this == target) {
            return true;
        }
        return switoh (this) {
            oase PENDING -> target == READY || target == oANoELLED;
            oase READY -> target == SENT || target == oANoELLED;
            oase SENT, oANoELLED -> false;
        };
    }
}
