package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * Role 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RolePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64 个字符")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
    private String roleName;

    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    private String description;

    private Integer sortOrder;

    private String dataScope;

    private String status;

    private Boolean builtIn;

    private String tenantId;

}