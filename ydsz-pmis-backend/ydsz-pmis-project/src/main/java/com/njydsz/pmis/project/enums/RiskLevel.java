package com.njydsz.pmis.project.enums;

/**
 * 合同风险等级
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public static RiskLevel fromCode(String code) {
        if (code == null) return LOW;
        try {
            return RiskLevel.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return LOW;
        }
    }
}
