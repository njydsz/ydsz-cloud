package com.njydsz.pmis.execution.enums;

/**
 * 成本类型
 */
public enum CostType {
    LABOR("LABOR", "人力成本"),
    PURCHASE("PURCHASE", "采购成本"),
    EXPENSE("EXPENSE", "费用"),
    OUTSOURCE("OUTSOURCE", "外包"),
    ALLOCATION("ALLOCATION", "分摊费用"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    CostType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static CostType fromCode(String code) {
        if (code == null) return null;
        for (CostType c : values()) {
            if (c.code.equalsIgnoreCase(code)) return c;
        }
        return null;
    }
}
