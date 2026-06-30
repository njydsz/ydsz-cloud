package com.njydsz.pmis.user.enums;

/**
 * 标签类型
 *
 * <ul>
 *   <li>SKILL - 技术栈</li>
 *   <li>INDUSTRY - 行业经验</li>
 *   <li>DOMAIN - 业务领域</li>
 *   <li>CERT - 资质/认证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum TagType {
    SKILL("SKILL", "技术栈"),
    INDUSTRY("INDUSTRY", "行业经验"),
    DOMAIN("DOMAIN", "业务领域"),
    CERT("CERT", "资质认证");

    private final String code;
    private final String desc;

    TagType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    public static TagType fromCode(String code) {
        if (code == null) return null;
        for (TagType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
