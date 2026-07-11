package com.njydsz.pmis.project.domain.enums;

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

    /** 类型编码（大小写不敏感） */
    private final String code;
    /** 类型中文描述 */
    private final String desc;

    ProjectType(String code, String desc) {
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
     * @param code 项目类型编码（大小写不敏感）
     * @return 枚举值；未匹配返回 null
     */
    public static ProjectType fromCode(String code) {
        if (code == null) return null;
        for (ProjectType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
