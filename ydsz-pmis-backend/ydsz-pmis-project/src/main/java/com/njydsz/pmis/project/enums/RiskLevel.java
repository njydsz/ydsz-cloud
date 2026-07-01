package com.njydsz.pmis.project.enums;

/**
 * 合同风险等级
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RiskLevel {
    LOW("LOW", "低风险"),
    MEDIUM("MEDIUM", "中风险"),
    HIGH("HIGH", "高风险");

    private final String code;
    private final String desc;

    RiskLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 或解析失败时返回 LOW
     * @return 匹配到的枚举值；未匹配返回 LOW
     */
    public static RiskLevel fromCode(String code) {
        if (code == null) return LOW;
        try {
            return RiskLevel.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return LOW;
        }
    }
}
