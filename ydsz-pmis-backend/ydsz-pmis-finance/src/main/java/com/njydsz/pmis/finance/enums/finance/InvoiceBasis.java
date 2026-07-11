package com.njydsz.pmis.finance.enums.finance;

/**
 * 发票开票依据
 *
 * <ul>
 *   <li>MILESTONE - 里程碑（需验收报告）</li>
 *   <li>OUTSOURCING - 人力外包（需客户确认人天单）</li>
 *   <li>MONTHLY - 月度结算</li>
 *   <li>FINAL - 终验/尾款</li>
 *   <li>OTHER - 其他</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum InvoiceBasis {
    MILESTONE("MILESTONE", "里程碑"),
    OUTSOURCING("OUTSOURCING", "人力外包"),
    MONTHLY("MONTHLY", "月度结算"),
    FINAL("FINAL", "终验/尾款"),
    OTHER("OTHER", "其他");

    /** 开票依据编码（大小写不敏感） */
    private final String code;
    /** 开票依据中文描述 */
    private final String desc;

    InvoiceBasis(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取开票依据编码
     *
     * @return 开票依据编码字符串
     */
    public String getCode() { return code; }

    /**
     * 获取开票依据中文描述
     *
     * @return 开票依据中文描述
     */
    public String getDesc() { return desc; }

    /**
     * 根据编码反查枚举
     *
     * @param code 开票依据编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static InvoiceBasis fromCode(String code) {
        if (code == null) return null;
        for (InvoiceBasis b : values()) {
            if (b.code.equalsIgnoreCase(code)) return b;
        }
        return null;
    }
}
