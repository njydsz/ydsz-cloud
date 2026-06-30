package com.njydsz.pmis.execution.enums;

/**
 * 费率类型
 *
 * <ul>
 *   <li>EXTERNAL - 对外报价费率（Rate Card）</li>
 *   <li>INTERNAL - 对内成本费率（事业部内部核算）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum RateType {
    EXTERNAL("EXTERNAL", "对外报价费率"),
    INTERNAL("INTERNAL", "对内成本费率");

    private final String code;
    private final String desc;

    RateType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static RateType fromCode(String code) {
        if (code == null) return null;
        for (RateType r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }
}
