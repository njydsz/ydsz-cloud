package com.njydsz.userinfo.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 用户更新 DTO，用于 PUT 请求。null 字段不更新（动态更新）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountUpdateDTO {

    /** 用户唯一标识 */
    @NotBlank(message = "用户 ID 不能为空")
    private String id;

    /** 真实姓名 */
    @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
    private String realName;

    /** 手机号码 */
    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;

    /** 邮箱地址 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    /** 头像 URL */
    @Size(max = 255, message = "头像 URL 长度不能超过 255 个字符")
    private String avatar;

    /** 账号状态：1-启用、0-停用 */
    private Integer status;
    /** 用户类型 */
    private String userType;
    /** 所属公司 ID */
    private String companyId;
}
