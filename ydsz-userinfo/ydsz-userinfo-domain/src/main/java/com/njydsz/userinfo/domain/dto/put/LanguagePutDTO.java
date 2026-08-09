package com.njydsz.userinfo.domain.dto.put;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 语言修改请求 DTO。
 *
 * <p>对应后端 {@code PUT /api/v1/language} 请求体。
 * 修改时 {@link #id} 必填，修改 {@code isDefault} 时 Service 层自动处理新旧默认语言切换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LanguagePutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 语言 ID（必填） */
    @NotBlank(message = "ID不能为空")
    @Xss(message = "id包含非法内容")

    private String id;

    /** 语言编码（ISO 639-1） */
    @NotBlank(message = "语言编码不能为空")
    @Size(max = 20, message = "语言编码长度不能超过 20 个字符")
    @Xss(message = "languageCode包含非法内容")

    private String languageCode;

    /** 语言名称 */
    @NotBlank(message = "语言名称不能为空")
    @Size(max = 64, message = "语言名称长度不能超过 64 个字符")
    @Xss(message = "languageName包含非法内容")

    private String languageName;

    /** 是否默认语言（{@code 1=是}） */
    private Integer isDefault;

    /** 排序序号（升序） */
    private Integer sortOrder;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    @Xss(message = "status包含非法内容")

    private String status;

}
