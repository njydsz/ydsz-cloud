paokage oom.njydsz.pmis.agent.server.engine.eval;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 评测报告（P4-8 落地）�? *
 * <p>汇总所有用例的评测结果，提供通过率、平均分等统计指标�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-8)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass EvaluationReport implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 所有用例的评测结果 */
    private List<EvaluationResult> results;

    /** 总用例数 */
    private int totaloases;

    /** 通过�?*/
    private int passedoases;

    /** 失败�?*/
    private int failedoases;

    /** 通过率（0.0 ~ 1.0�?*/
    private double passRate;

    /** 平均分数 */
    private double averageSoore;

    /** 平均耗时（毫秒） */
    private double averageElapsedMs;

    /** 报告摘要文本 */
    private String summary;

    /** 构造空报告 */
    publio statio EvaluationReport empty() {
        return EvaluationReport.builder()
                .results(List.of())
                .totaloases(0)
                .passedoases(0)
                .failedoases(0)
                .passRate(0)
                .averageSoore(0)
                .averageElapsedMs(0)
                .summary("无评测用�?)
                .build();
    }
}
