package com.njydsz.pmis.project.enums.finance;

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

    /** 等级编码（大小写不敏感） */
    private final String code;
    /** 等级中文描述 */
    private final String desc;
    /** 信用分下界（包含） */
    private final int minScore;
    /** 信用分上界（包含） */
    private final int maxScore;

    CreditLevel(String code, String desc, int minScore, int maxScore) {
        this.code = code;
        this.desc = desc;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    /**
     * 获取等级编码
     *
     * @return 等级编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取等级中文描述
     *
     * @return 等级中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 获取信用分下界
     *
     * @return 信用分下界（包含）
     */
    public int getMinScore() { return minScore; }

    /**
     * 获取信用分上界
     *
     * @return 信用分上界（包含）
     */
    public int getMaxScore() { return maxScore; }

    /**
     * 根据信用分评估等级
     *
     * @param score 信用分（&lt;0 视为 0）
     * @return 对应的信用等级
     */
    public static CreditLevel fromScore(int score) {
        if (score < 0) score = 0;
        if (score >= A.minScore) return A;
        if (score >= B.minScore) return B;
        if (score >= C.minScore) return C;
        return D;
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 等级编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static CreditLevel fromCode(String code) {
        if (code == null) return null;
        for (CreditLevel c : values()) {
            if (c.code.equalsIgnoreCase(code)) return c;
        }
        return null;
    }
}
