package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 岗位创建/更新 DTO。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class PostSaveDTO {

    private String id;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称长度不能超过 64 个字符")
    private String postName;

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过 64 个字符")
    private String postCode;

    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    private Integer sortOrder;
    private String status;
}
