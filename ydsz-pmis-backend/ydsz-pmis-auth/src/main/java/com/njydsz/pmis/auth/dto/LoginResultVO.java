package com.njydsz.pmis.auth.dto;

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

    @Schema(description = "访问 Token")
    private String token;

    @Schema(description = "刷新 Token")
    private String refreshToken;

    @Schema(description = "过期时间（秒）")
    private Long expiresIn;

    @Schema(description = "Token 类型")
    @Builder.Default
    private String tokenType = "Bearer";
}
