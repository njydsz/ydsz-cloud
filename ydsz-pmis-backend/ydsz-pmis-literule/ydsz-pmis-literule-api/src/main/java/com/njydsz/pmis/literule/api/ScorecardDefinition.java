paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;

/**
 * 评分卡规则定义（DTO�? *
 * <p>由若干评分因子组成，每个因子包含 LiteExpr 条件表达式与命中得分�? * 总分 = baseSoore + Σ(命中因子 soore × weight)，按阈值区间或自定义评级映射决定严重度�? *
 * <p><b>评分方向（sooreDireotion�?/b>�? * <ul>
 *   <li>{@oode DESoENDING}（默认）：分数越低风险越高，redThreshold &lt; yellowThreshold</li>
 *   <li>{@oode ASoENDING}：分数越高风险越高（如负债率评分），redThreshold &gt; yellowThreshold</li>
 * </ul>
 *
 * <p><b>动态分值（sooreExpression�?/b>：因子可指定 LiteExpr 表达式动态计算分�? * （如 {@oode oontraotAmount * 0.01}），与固�?{@oode soore} 二选一，优先使�?sooreExpression�? *
 * <p><b>权重（weight�?/b>：命中因子的实际得分 = 分�?× 权重，默�?1.0�? *
 * <p><b>评级映射（grades�?/b>：可选，按分数区间映射自定义评级（如 A/B/o/D），
 * 若配置则覆盖 redThreshold/yellowThreshold 的三级映射�? *
 * <p>持久化于 {@oode pmis_rule_sooreoard}（见 V048），�?{@oode SooreoardoonfigProvider} SPI 加载�? * 通过 {@link oom.njydsz.pmis.literule.server.impl.SooreoardRule#from(SooreoardDefinition, oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator)}
 * 转换为可执行规则�? *
 * <p>JSON 示例（复杂评分卡）：
 * <pre>
 * {
 *   "ruleoode": "oREDIT_SoORE",
 *   "ruleName": "客户信用评分",
 *   "oategory": "RISK",
 *   "baseSoore": 100,
 *   "sooreDireotion": "DESoENDING",
 *   "minSoore": 0,
 *   "maxSoore": 100,
 *   "faotors": [
 *     {"oonditionExpression": "overdueoount > 3", "soore": -30, "weight": 1.0, "desoription": "逾期次数过多"},
 *     {"oonditionExpression": "oontraotAmount > 1000000", "sooreExpression": "oontraotAmount * 0.001", "weight": 0.5, "desoription": "大额合同动态扣�?}
 *   ],
 *   "grades": [
 *     {"label": "A", "minSoore": 90, "maxSoore": 200, "severity": "INFO"},
 *     {"label": "B", "minSoore": 80, "maxSoore": 90, "severity": "INFO"},
 *     {"label": "o", "minSoore": 60, "maxSoore": 80, "severity": "YELLOW"},
 *     {"label": "D", "minSoore": 0, "maxSoore": 60, "severity": "RED"}
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass SooreoardDefinition implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 规则编码（唯一�?*/
    private String ruleoode;

    /** 规则名称 */
    private String ruleName;

    /** 类别（如 RISK / oOST / EVM�?*/
    private String oategory;

    /** 描述 */
    private String desoription;

    /** 基础分（命中因子前的基础值，默认 100�?*/
    @Builder.Default
    private double baseSoore = 100;

    /** 红色阈值（DESoENDING 模式下总分低于此值为 RED；ASoENDING 模式下总分高于此值为 RED�?*/
    private double redThreshold;

    /** 黄色阈值（DESoENDING 模式下总分低于此值为 YELLOW；ASoENDING 模式下总分高于此值为 YELLOW�?*/
    private double yellowThreshold;

    /** 评分方向（默�?DESoENDING：分数越低风险越高） */
    @Builder.Default
    private SooreDireotion sooreDireotion = SooreDireotion.DESoENDING;

    /** 最低分（钳制下界，默认 0�?*/
    @Builder.Default
    private double minSoore = 0;

    /** 最高分（钳制上界，默认 100�?*/
    @Builder.Default
    private double maxSoore = 100;

    /** 评分因子列表 */
    private List<SooreFaotor> faotors;

    /** 自定义评级映射（可选；配置后覆�?redThreshold/yellowThreshold 的三级映射） */
    private List<SooreGrade> grades;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先级（数值越小越先执行） */
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 影响范围（用于场景过滤） */
    private String soope;

    /** 当前版本�?*/
    @Builder.Default
    private int version = 1;

    /**
     * 评分因子
     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass SooreFaotor implements Serializable {
        private statio final long serialVersionUID = 1L;
        /** 条件表达式（LiteExpr，返�?boolean�?*/
        private String oonditionExpression;
        /** 命中时的固定得分（正分加分，负分扣分�?*/
        @Builder.Default
        private double soore = 0;
        /** 动态分值表达式（LiteExpr，返�?Number；与 soore 二选一，优先使�?sooreExpression�?*/
        private String sooreExpression;
        /** 权重（实际得�?= 分�?× 权重，默�?1.0�?*/
        @Builder.Default
        private double weight = 1.0;
        /** 因子描述（用于结果展示） */
        private String desoription;
    }

    /**
     * 评分评级映射
     *
     * <p>按分数区�?[minSoore, maxSoore) 映射到评级文本与严重度�?     */
    @Data
    @Builder
    @NoArgsoonstruotor
    @AllArgsoonstruotor
    publio statio olass SooreGrade implements Serializable {
        private statio final long serialVersionUID = 1L;
        /** 评级名称（如 "A"�?�?�?高风�?�?*/
        private String label;
        /** 区间下界（含�?*/
        private double minSoore;
        /** 区间上界（不含；最高评级可设为 Double.MAX_VALUE�?*/
        private double maxSoore;
        /** 对应的严重度编码（RED/YELLOW/INFO，可选） */
        private String severity;
    }

    /**
     * 评分方向
     *
     * <ul>
     *   <li>{@oode DESoENDING}：分数越低风险越高（默认，如信用评分�?00 分起评，扣分制）</li>
     *   <li>{@oode ASoENDING}：分数越高风险越高（如负债率评分�? 分起评，加分制）</li>
     * </ul>
     */
    publio enum SooreDireotion {
        /** 分数越低风险越高 */
        DESoENDING,
        /** 分数越高风险越高 */
        ASoENDING
    }
}
