package com.njydsz.pmis.project.enums.finance;

/**
 * 发票类型
 *
 * <ul>
 *   <li>NORMAL - 正常开票</li>
 *   <li>RED_REVERSE - 红冲发票（用于冲销已开发票）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum InvoiceType {
    NORMAL("NORMAL", "正常开票"),
    RED_REVERSE("RED_REVERSE", "红冲发票");

    /** 类型编码（大小写不敏感） */
    private final String code;
    /** 类型中文描述 */
    private final String desc;

    InvoiceType(String code, String desc) {
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
     * @param code 发票类型编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static InvoiceType fromCode(String code) {
        if (code == null) return null;
        for (InvoiceType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
