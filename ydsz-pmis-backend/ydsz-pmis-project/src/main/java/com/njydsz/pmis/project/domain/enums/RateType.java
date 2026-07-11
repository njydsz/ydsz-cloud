package com.njydsz.pmis.project.domain.enums;

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

    /** 类型编码（大小写不敏感） */
    private final String code;
    /** 类型中文描述 */
    private final String desc;

    RateType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取类型编码
     *
     * @return 类型编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取类型中文描述
     *
     * @return 类型中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举
     *
     * @param code 费率类型编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static RateType fromCode(String code) {
        if (code == null) return null;
        for (RateType r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }
}
