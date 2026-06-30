package com.njydsz.pmis.execution.enums;

/**
 * 项目类型
 *
 * <p>用于匹配交付物标准。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ProjectType {
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

    ProjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static ProjectType fromCode(String code) {
        if (code == null) return null;
        for (ProjectType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
