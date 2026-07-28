package com.njydsz.userinfo.domain.dto;

import java.io.Serializable;
import java.io.Serial;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserAccountCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 个字符之间")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
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
    private List<String> roleIds;

    private String tenantId;
}
