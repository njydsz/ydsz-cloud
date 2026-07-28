package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 公司创建/更新 DTO（SaveDTO 共用模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanySaveDTO {

    /** 公司 ID，更新时必填 */
    private String id;

    /** 公司名称 */
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128, message = "公司名称长度不能超过 128 个字符")
    private String companyName;

    /** 公司编码，全局唯一 */
    @NotBlank(message = "公司编码不能为空")
    @Size(max = 64, message = "公司编码长度不能超过 64 个字符")
    private String companyCode;

    /** 父公司 ID */
    private String parentId;

    /** 联系人 */
    @Size(max = 64, message = "联系人长度不能超过 64 个字符")
    private String contactPerson;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话长度不能超过 20 个字符")
    private String contactPhone;

    /** 地址 */
    @Size(max = 255, message = "地址长度不能超过 255 个字符")
    private String address;

    /** 状态：ENABLE-启用、DISABLE-禁用 */
    private String status;
}
