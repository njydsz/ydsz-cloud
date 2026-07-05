package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求体 DTO
 *
 * <p>P0-6 修复：原 changeMyPassword 用 Map<String,String> 接收，@Valid 完全失效；
 * 改为强类型 DTO + JSR-303 校验。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "修改密码请求体")
public class PasswordChangeDTO {

    /**
     * 原密码（明文，由 HTTPS 传输）
     */
    @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{validation.user.msg_2b7a520e}")
    @Size(min = 8, max = 64, message = "{validation.user.msg_8c2f1a}")
    private String oldPassword;

    /**
     * 新密码（明文，由 HTTPS 传输）
     */
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "NewPass@123")
    @NotBlank(message = "{validation.user.msg_4a3e2d}")
    @Size(min = 8, max = 64, message = "{validation.user.msg_8c2f1a}")
    private String newPassword;
}
