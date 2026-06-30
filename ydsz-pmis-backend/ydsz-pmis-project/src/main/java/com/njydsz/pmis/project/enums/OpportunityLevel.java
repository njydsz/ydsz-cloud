package com.njydsz.pmis.project.enums;

/**
 * 商机分级
 *
 * <p>A: 战略级，500万+
 * <p>B: 重点级，100万-500万
 * <p>C: 一般级，100万以下
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum OpportunityLevel {
    A, B, C;

    public static OpportunityLevel fromCode(String code) {
        if (code == null) return C;
        try {
            return OpportunityLevel.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return C;
        }
    }
}
