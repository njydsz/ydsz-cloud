package com.njydsz.pmis.execution.enums;

/**
 * 满意度评价等级
 *
 * <ul>
 *   <li>VERY_SATISFIED - 非常满意（5 星）</li>
 *   <li>SATISFIED - 满意（4 星）</li>
 *   <li>NEUTRAL - 一般（3 星）</li>
 *   <li>DISSATISFIED - 不满意（2 星）</li>
 *   <li>VERY_DISSATISFIED - 非常不满意（1 星）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum SatisfactionLevel {
    VERY_SATISFIED("VERY_SATISFIED", "非常满意", 5),
    SATISFIED("SATISFIED", "满意", 4),
    NEUTRAL("NEUTRAL", "一般", 3),
    DISSATISFIED("DISSATISFIED", "不满意", 2),
    VERY_DISSATISFIED("VERY_DISSATISFIED", "非常不满意", 1);

    private final String code;
    private final String desc;
    private final int score;

    SatisfactionLevel(String code, String desc, int score) {
        this.code = code;
        this.desc = desc;
        this.score = score;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public int getScore() { return score; }

    /**
     * 根据评分反查枚举
     *
     * @param s 评分（1-5）
     * @return 对应的满意度等级；非 1-5 返回 null
     */
    public static SatisfactionLevel fromScore(Integer s) {
        if (s == null) return null;
        return switch (s) {
            case 5 -> VERY_SATISFIED;
            case 4 -> SATISFIED;
            case 3 -> NEUTRAL;
            case 2 -> DISSATISFIED;
            case 1 -> VERY_DISSATISFIED;
            default -> null;
        };
    }

    /**
     * 根据编码反查枚举
     *
     * @param code 等级编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static SatisfactionLevel fromCode(String code) {
        if (code == null) return null;
        for (SatisfactionLevel l : values()) {
            if (l.code.equalsIgnoreCase(code)) return l;
        }
        return null;
    }
}
