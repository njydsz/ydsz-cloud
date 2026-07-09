package com.njydsz.pmis.project.enums.execution;

/**
 * 项目风险等级
 *
 * <ul>
 *   <li>LOW - 低风险</li>
 *   <li>MEDIUM - 中风险</li>
 *   <li>HIGH - 高风险</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RiskLevel {
    LOW("LOW", "低风险", 1),
    MEDIUM("MEDIUM", "中风险", 2),
    HIGH("HIGH", "高风险", 3);

    private final String code;
    private final String desc;
    private final int weight;

    RiskLevel(String code, String desc, int weight) {
        this.code = code;
        this.desc = desc;
        this.weight = weight;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
    public int getWeight() { return weight; }

    public static RiskLevel fromCode(String code) {
        if (code == null) return null;
        for (RiskLevel r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }

    public static RiskLevel fromScore(int score) {
        if (score >= 6) return HIGH;
        if (score >= 3) return MEDIUM;
        return LOW;
    }
}
