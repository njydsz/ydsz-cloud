package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 语言创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LanguageSaveDTO {

    /** 语言 ID，更新时必填 */
    private String id;

    /** 语言编码，如 zh-CN、en-US */
    @NotBlank(message = "语言编码不能为空")
    @Size(max = 20, message = "语言编码长度不能超过 20 个字符")
    private String languageCode;

    /** 语言名称 */
    @NotBlank(message = "语言名称不能为空")
    @Size(max = 64, message = "语言名称长度不能超过 64 个字符")
    private String languageName;

    /** 是否默认语言：1-是、0-否 */
    private Integer isDefault;
    /** 排序序号 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
