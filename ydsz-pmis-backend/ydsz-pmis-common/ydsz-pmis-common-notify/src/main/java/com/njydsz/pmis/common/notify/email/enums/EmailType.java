package com.njydsz.pmis.common.email.enums;

/**
 * 邮件类型枚举
 *
 * <p>定义了系统支持的各种邮件发送类型，包括文本邮件、HTML邮件、附件邮件�?
 * 内嵌资源邮件以及模板邮件（Thymeleaf/Freemarker）等�?/p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public enum EmailType {

    /** 纯文本邮�?*/
    TEXT("text", "文本邮件"),
    /** HTML 格式邮件 */
    HTML("html", "HTML邮件"),
    /** 带附件邮�?*/
    ATTACHMENT("attachment", "附件邮件"),
    /** 内嵌资源邮件 */
    INLINE("inline", "内嵌资源邮件"),
    /** Thymeleaf 模板邮件 */
    THYMELEAF("thymeleaf", "Thymeleaf模板邮件"),
    /** Freemarker 模板邮件 */
    FREEMARKER("freemarker", "Freemarker模板邮件");

    /** 类型编码 */
    private final String code;
    /** 类型描述 */
    private final String description;

    EmailType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    /**
     * 获取类型描述
     *
     * @return 类型描述
     */
    public String getDescription() {
        return description;
    }

    public static EmailType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (EmailType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}