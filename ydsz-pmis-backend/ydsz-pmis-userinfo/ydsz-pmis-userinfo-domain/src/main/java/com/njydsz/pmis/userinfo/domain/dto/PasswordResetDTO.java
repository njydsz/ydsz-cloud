package com.njydsz.pmis.userinfo.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 重置密码请求体 DTO
 *
 * <p>P0-6 修复：密码不再走 URL query string (@RequestParam)，改为 @RequestBody，
 * 避免密码被 Nginx access log / 浏览器历史 / APM 链路追踪记录。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "重置密码请求体")
public class PasswordResetDTO {

    /**
     * 新密码（明文，由 HTTPS 传输）
     */
    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "NewPass@123")
    @NotBlank(message = "{validation.user.msg_4a3e2d}")
    @Size(min = 8, max = 64, message = "{validation.user.msg_8c2f1a}")
    private String newPassword;
}
