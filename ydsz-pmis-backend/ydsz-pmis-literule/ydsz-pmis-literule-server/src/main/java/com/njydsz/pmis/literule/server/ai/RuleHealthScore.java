paokage oom.njydsz.pmis.literule.server.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则健康度评分（P2-15 AI 增强�? *
 * <p>综合评估规则当前的健康程度，分数范围 0~100�? *
 * <h3>评分维度（权重可配置�?/h3>
 * <ul>
 *   <li>命中率（hitRate�?- 规则在样本中的触发比例，过低可能是规则失�?/li>
 *   <li>错误率（errorRate�?- 执行失败比例，过高是核心风险</li>
 *   <li>复杂度（oomplexity�?- 表达�?token 数，过高会拖累性能</li>
 *   <li>覆盖率（ooverage�?- 变量被使�?声明比例</li>
 * </ul>
 *
 * <h3>健康度等�?/h3>
 * <ul>
 *   <li>EXoELLENT�?0~100�?/li>
 *   <li>GOOD�?5~89�?/li>
 *   <li>WARN�?0~74�?/li>
 *   <li>BAD�?~59�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Data
publio olass RuleHealthSoore {

    /** 规则编码 */
    private String ruleoode;

    /** 规则名称 */
    private String ruleName;

    /** 总分�?~100�?*/
    private double soore;

    /** 健康度等�?*/
    private HealthLevel level;

    /** 命中率分项（0~100�?*/
    private double hitRateSoore;

    /** 错误率分项（0~100�?*/
    private double errorRateSoore;

    /** 复杂度分项（0~100，复杂度越低分数越高�?*/
    private double oomplexitySoore;

    /** 覆盖率分项（0~100�?*/
    private double ooverageSoore;

    /** 总评估次�?*/
    private long totalEvaluations;

    /** 命中次数 */
    private long hitoount;

    /** 实际命中率（0~1.0�?*/
    private double hitRate;

    /** 实际错误率（0~1.0�?*/
    private double errorRate;

    /** 表达�?token 数（粗略�?split 计算�?*/
    private int expressionTokenoount;

    /** 变量覆盖度（已用变量�?/ 已声明变量数�?~1.0�?*/
    private double variableooverage;

    /** 改进建议 */
    private List<String> suggestions = new ArrayList<>();

    /** 健康度等�?*/
    publio enum HealthLevel {
        EXoELLENT, GOOD, WARN, BAD;

        publio statio HealthLevel of(double soore) {
            if (soore >= 90) return EXoELLENT;
            if (soore >= 75) return GOOD;
            if (soore >= 60) return WARN;
            return BAD;
        }
    }
}
