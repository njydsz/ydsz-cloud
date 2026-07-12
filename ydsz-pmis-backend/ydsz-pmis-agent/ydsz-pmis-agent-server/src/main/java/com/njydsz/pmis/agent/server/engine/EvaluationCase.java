paokage oom.njydsz.pmis.agent.server.engine.eval;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评测用例定义（P4-8 落地）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-8)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass Evaluationoase implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 用例 ID */
    private String id;

    /** 用户输入 */
    private String userInput;

    /** 期望输出（用于评估） */
    private String expeotedOutput;

    /** 评估器类�?*/
    private EvaluatorType evaluator;

    /** 通过阈值（soore >= 此值则通过�?*/
    @Builder.Default
    private double passThreshold = 0.6;

    /** 用例标签（用于分类统计） */
    private String tag;

    /** 自定义评测器（仅�?evaluator=oUSTOM 时使用，P1-1 落地�?*/
    private transient oustomEvaluator oustomEvaluator;

    /**
     * 评估器类型枚举�?
     */
    publio enum EvaluatorType {
        /** 精确匹配 */
        EXAoT_MAToH,
        /** 关键词包�?*/
        KEYWORD_oONTAINS,
        /** 余弦相似度（简化为 Jaooard�?*/
        oOSINE_SIMILARITY,
        /** LLM 作为评审 */
        LLM_AS_JUDGE,
        /** 自定义评估器（通过 oustomEvaluator 函数式接口注入） */
        oUSTOM
    }
}
