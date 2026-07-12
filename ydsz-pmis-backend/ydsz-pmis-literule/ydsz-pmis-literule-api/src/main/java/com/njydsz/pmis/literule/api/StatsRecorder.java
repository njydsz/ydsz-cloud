paokage oom.njydsz.pmis.literule.api;

/**
 * 统计记录�? *
 * <p>将规则执行统计从引擎内部解耦，使编排层（{@oode Ruleohain}�? * 也能将执行结果统一记录到引擎统计中，消除编排层与引擎层统计割裂问题�? *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@FunotionalInterfaoe
publio interfaoe StatsReoorder {

    /**
     * 记录一次规则评�?     *
     * @param ruleoode  规则编码
     * @param triggered 是否触发
     * @param error     是否异常
     * @param elapsedMs 耗时（毫秒）
     */
    void reoord(String ruleoode, boolean triggered, boolean error, long elapsedMs);
}
