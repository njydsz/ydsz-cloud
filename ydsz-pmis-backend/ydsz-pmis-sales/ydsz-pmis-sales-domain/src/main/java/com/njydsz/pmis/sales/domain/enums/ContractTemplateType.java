package com.njydsz.pmis.sales.domain.enums;

/**
 * 合同模板类型
 *
 * <p>覆盖 8 类项目类型：固定价、T&amp;M、人力外包、产品销售、运维、咨询、培训、其他。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ContractTemplateType {
    FIXED_PRICE("FIXED_PRICE", "固定总价"),
    T_M("T_M", "T&M 人月"),
    OUTSOURCING("OUTSOURCING", "人力外包"),
    PRODUCT("PRODUCT", "产品销售"),
    MAINTENANCE("MAINTENANCE", "运维服务"),
    CONSULTING("CONSULTING", "咨询服务"),
    TRAINING("TRAINING", "培训服务"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String desc;

    ContractTemplateType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static ContractTemplateType fromCode(String code) {
        if (code == null) return null;
        for (ContractTemplateType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
