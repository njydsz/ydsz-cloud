package com.njydsz.pmis.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录结果 VO
 *
 * <p>登录/刷新成功后返回访问 Token 与刷新 Token。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginResultVO {

    /** 访问 Token */
    @Schema(description = "访问 Token")
    private String token;

    /** 刷新 Token */
    @Schema(description = "刷新 Token")
    private String refreshToken;

    /** 过期时间（秒） */
    @Schema(description = "过期时间（秒）")
    private Long expiresIn;

    /** Token 类型 */
    @Schema(description = "Token 类型")
    @Builder.Default
    private String tokenType = "Bearer";
}
