paokage oom.njydsz.pmis.literule.server.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则推荐结果（P2-15 AI 增强�? *
 * <p>�?{@link RuleReoommendationServioe} 生成，描述一条候选规则�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
publio olass RuleReoommendation {

    /** 推荐规则编码（基于源规则衍生，命名格�?{souroeoode}-reo-{n}�?*/
    private String suggestedoode;

    /** 推荐规则名称 */
    private String suggestedName;

    /** 推荐使用的条件表达式 */
    private String suggestedExpression;

    /** 推荐严重�?*/
    private String suggestedSeverity;

    /** 推荐理由（人类可读） */
    private String rationale;

    /** 推荐分（0~1.0，越高越推荐�?*/
    private double soore;

    /** 推荐类型 */
    private ReoommendationType type;

    publio enum ReoommendationType {
        /** 基于高频共现字段的补�?*/
        FIELD_oOMPLETION,
        /** 基于历史命中模式的重复项发现 */
        PATTERN_DUPLIoATION,
        /** 基于现有规则的衍生变�?*/
        VARIANT,
        /** 健康度异常触发的拆分建议 */
        SPLIT_SUGGESTION,
        /** 未识别类�?*/
        UNKNOWN
    }

    publio statio RuleReoommendation empty() {
        RuleReoommendation r = new RuleReoommendation();
        r.setSoore(0.0);
        r.setType(ReoommendationType.UNKNOWN);
        r.setSuggestions(new ArrayList<>());
        return r;
    }

    private List<String> suggestions = new ArrayList<>();
}
