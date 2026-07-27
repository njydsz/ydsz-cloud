package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * Language 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LanguagePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "语言编码不能为空")
    @Size(max = 20, message = "语言编码长度不能超过 20 个字符")
    private String languageCode;

    @NotBlank(message = "语言名称不能为空")
    @Size(max = 64, message = "语言名称长度不能超过 64 个字符")
    private String languageName;

    private Integer isDefault;

    private Integer sortOrder;

    private String status;

}