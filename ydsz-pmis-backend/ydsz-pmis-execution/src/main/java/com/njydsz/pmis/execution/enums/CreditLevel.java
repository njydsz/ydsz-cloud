package com.njydsz.pmis.execution.enums;

/**
 * 客户信用等级
 *
 * <ul>
 *   <li>A - 优质客户（回款及时、合同稳定）</li>
 *   <li>B - 良好客户（偶有延期但可控）</li>
 *   <li>C - 一般客户（需关注回款节奏）</li>
 *   <li>D - 风险客户（需预付或担保）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum CreditLevel {
    A("A", "优质客户", 90, 100),
    B("B", "良好客户", 75, 89),
    C("C", "一般客户", 60, 74),
    D("D", "风险客户", 0, 59);

    private final String code;
    private final String desc;
    private final int minScore;
    private final int maxScore;

    CreditLevel(String code, String desc, int minScore, int maxScore) {
        this.code = code;
        this.desc = desc;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }

    /**
     * 根据信用分评估等级
     */
    public static CreditLevel fromScore(int score) {
        if (score < 0) score = 0;
        if (score >= A.minScore) return A;
        if (score >= B.minScore) return B;
        if (score >= C.minScore) return C;
        return D;
    }

    public static CreditLevel fromCode(String code) {
        if (code == null) return null;
        for (CreditLevel c : values()) {
            if (c.code.equalsIgnoreCase(code)) return c;
        }
        return null;
    }
}
