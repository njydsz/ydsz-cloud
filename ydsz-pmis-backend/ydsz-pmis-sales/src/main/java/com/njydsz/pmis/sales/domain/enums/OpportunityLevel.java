package com.njydsz.pmis.sales.domain.enums;

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

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 或解析失败时返回 C（默认最低级）
     * @return 匹配到的枚举值；未匹配返回 C
     */
    public static OpportunityLevel fromCode(String code) {
        if (code == null) return C;
        try {
            return OpportunityLevel.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return C;
        }
    }
}
