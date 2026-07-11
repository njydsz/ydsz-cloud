package com.njydsz.pmis.literule.server.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则健康度评分（P2-15 AI 增强）
 *
 * <p>综合评估规则当前的健康程度，分数范围 0~100。
 *
 * <h3>评分维度（权重可配置）</h3>
 * <ul>
 *   <li>命中率（hitRate） - 规则在样本中的触发比例，过低可能是规则失效</li>
 *   <li>错误率（errorRate） - 执行失败比例，过高是核心风险</li>
 *   <li>复杂度（complexity） - 表达式 token 数，过高会拖累性能</li>
 *   <li>覆盖率（coverage） - 变量被使用/声明比例</li>
 * </ul>
 *
 * <h3>健康度等级</h3>
 * <ul>
 *   <li>EXCELLENT（90~100）</li>
 *   <li>GOOD（75~89）</li>
 *   <li>WARN（60~74）</li>
 *   <li>BAD（0~59）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
public class RuleHealthScore {

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 总分（0~100） */
    private double score;

    /** 健康度等级 */
    private HealthLevel level;

    /** 命中率分项（0~100） */
    private double hitRateScore;

    /** 错误率分项（0~100） */
    private double errorRateScore;

    /** 复杂度分项（0~100，复杂度越低分数越高） */
    private double complexityScore;

    /** 覆盖率分项（0~100） */
    private double coverageScore;

    /** 总评估次数 */
    private long totalEvaluations;

    /** 命中次数 */
    private long hitCount;

    /** 实际命中率（0~1.0） */
    private double hitRate;

    /** 实际错误率（0~1.0） */
    private double errorRate;

    /** 表达式 token 数（粗略用 split 计算） */
    private int expressionTokenCount;

    /** 变量覆盖度（已用变量数 / 已声明变量数，0~1.0） */
    private double variableCoverage;

    /** 改进建议 */
    private List<String> suggestions = new ArrayList<>();

    /** 健康度等级 */
    public enum HealthLevel {
        EXCELLENT, GOOD, WARN, BAD;

        public static HealthLevel of(double score) {
            if (score >= 90) return EXCELLENT;
            if (score >= 75) return GOOD;
            if (score >= 60) return WARN;
            return BAD;
        }
    }
}
