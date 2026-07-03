package com.njydsz.pmis.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 二次认证请求 DTO
 *
 * <p>支持三种凭据：
 * <ul>
 *   <li>{@code method = PASSWORD}：用 {@link #password} 校验当前登录用户密码</li>
 *   <li>{@code method = TOTP}：用 {@link #otp} 校验 TOTP 动态码</li>
 *   <li>{@code method = BACKUP_CODE}：用 {@link #backupCode} 校验一次性备份码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "敏感操作二次认证请求")
public class ReAuthRequest {

    /** 操作码（与后端 @RequireReAuth.code() 一致） */
    @NotBlank
    @Schema(description = "操作码（与 @RequireReAuth.code() 一致）", example = "USER_DELETE")
    private String operationCode;

    /** PASSWORD / TOTP / BACKUP_CODE */
    @NotBlank
    @Schema(description = "凭据类型", example = "PASSWORD", allowableValues = {"PASSWORD", "TOTP", "BACKUP_CODE"})
    private String method;

    @Schema(description = "当前密码（PASSWORD 时必填）")
    private String password;

    @Schema(description = "6 位 TOTP 动态码（TOTP 时必填）")
    private String otp;

    @Schema(description = "8 位备份码（BACKUP_CODE 时必填）")
    private String backupCode;

    /** TTL（秒），默认 300，最长 1800 */
    @Schema(description = "token 有效期（秒），默认 300，最长 1800")
    private Integer ttlSeconds;
}
