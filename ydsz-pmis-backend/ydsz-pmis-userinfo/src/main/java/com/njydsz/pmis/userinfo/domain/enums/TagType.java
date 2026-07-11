package com.njydsz.pmis.userinfo.domain.enums.user;

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

    /** 枚举编码 */
    private final String code;
    /** 枚举描述 */
    private final String desc;

    TagType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }

    /**
     * 根据编码解析枚举
     *
     * @param code 枚举编码（大小写不敏感）
     * @return 匹配的枚举值；code 为 null 或无匹配时返回 null
     */
    public static TagType fromCode(String code) {
        if (code == null) return null;
        for (TagType t : values()) {
            if (t.code.equalsIgnoreCase(code)) return t;
        }
        return null;
    }
}
