package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 岗位创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PostSaveDTO {

    /** 岗位 ID，更新时必填 */
    private String id;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称长度不能超过 64 个字符")
    private String postName;

    /** 岗位编码，全局唯一 */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过 64 个字符")
    private String postCode;

    /** 岗位描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    /** 排序序号 */
    private Integer sortOrder;
    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
