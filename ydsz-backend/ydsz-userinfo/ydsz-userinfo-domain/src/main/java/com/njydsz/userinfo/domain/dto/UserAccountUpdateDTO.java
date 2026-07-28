package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户更新请求 DTO。
 *
 * <p>更新时 {@code id} 必填，其余字段按需填写，未传字段保持原值不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "ID不能为空")
    private String id;

    @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
    private String realName;

    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String phone;

    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    @Size(max = 255, message = "头像URL长度不能超过 255 个字符")
    private String avatar;

    private String status;
    private String userType;
    private String companyId;
    private String deptId;
    private String leaderId;
    private String positionCode;
}
