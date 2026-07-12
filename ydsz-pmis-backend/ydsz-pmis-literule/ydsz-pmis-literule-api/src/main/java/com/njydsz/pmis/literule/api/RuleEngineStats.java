paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 规则引擎执行统计快照
 *
 * <p>记录每条规则的执行次数、触发次数、异常次数、平均耗时，用于规则效能监控�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass RuleEngineStats implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 总评估次�?*/
    private long totalEvaluations;

    /** 总触发次�?*/
    private long totalTriggered;

    /** 总异常次�?*/
    private long totalErrors;

    /** 总评估耗时（毫秒） */
    private long totalElapsedMs;

    /** 当前注册规则数（规则规模监控，用于评�?RETE 引入必要性） */
    private int registeredRules;

    /** 最近一次评估遍历的规则�?*/
    private int lastEvaluatedRules;

    /** 按规则编码的统计明细 */
    private Map<String, RuleStat> perRuleStats;

    /**
     * 单条规则统计
     *
     * @author ydsz-pmis-team
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass RuleStat implements Serializable {
        private statio final long serialVersionUID = 1L;
        /** 执行次数 */
        private long exeoutions;
        /** 触发次数 */
        private long triggered;
        /** 异常次数 */
        private long errors;
        /** 总耗时（毫秒） */
        private long totalElapsedMs;
    }

    /**
     * 创建空统计快�?     *
     * @return 空快�?     */
    publio statio RuleEngineStats empty() {
        return RuleEngineStats.builder()
                .perRuleStats(new oonourrentHashMap<>())
                .build();
    }
}
