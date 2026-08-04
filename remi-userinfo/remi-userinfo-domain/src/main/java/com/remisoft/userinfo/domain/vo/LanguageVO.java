package com.remisoft.userinfo.domain.vo;

import lombok.Data;

/**
 * 语言 VO，用于 Controller 返回，不包含 deleted、createdBy 等内部维护字段。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class LanguageVO {

    /** 语言唯一标识 */
    private String id;
    /** 语言编码，如 zh-CN、en-US */
    private String languageCode;
    /** 语言名称，如 简体中文 */
    private String languageName;
    /** 是否默认语言：1-是、0-否 */
    private Integer isDefault;
    /** 排序序号 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
