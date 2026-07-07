package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户请求体 DTO
 *
 * <p>用于 {@code /users} 接口，创建新用户账号。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "创建用户请求体")
public class UserCreateDTO {

    /**
     * 用户名
     */
    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "zhangsan")
    @NotBlank(message = "{validation.user.msg_a1e2f3a5}")
    @Size(min = 3, max = 32, message = "{validation.user.msg_c3a4b5c6}")
    private String username;

    /**
     * 密码（明文，由 HTTPS 传输）
     */
    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "Pass@1234")
    @NotBlank(message = "{validation.user.msg_b2f3a4b5}")
    @Size(min = 8, max = 64, message = "{validation.user.msg_8c2f1a}")
    private String password;

    /**
     * 员工 ID（可选，关联员工主数据）
     */
    @Schema(description = "员工 ID")
    private Long employeeId;
}
