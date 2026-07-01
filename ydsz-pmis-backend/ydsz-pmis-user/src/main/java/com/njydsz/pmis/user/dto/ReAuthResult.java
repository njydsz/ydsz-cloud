package com.njydsz.pmis.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 二次认证 token 颁发结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@Schema(description = "二次认证颁发结果")
public class ReAuthResult {

    /** 二次认证 token（一次性，写入 X-Re-Auth-Token 请求头） */
    @Schema(description = "二次认证 token，需放入 X-Re-Auth-Token 请求头")
    private String token;

    /** token 剩余有效期（秒） */
    @Schema(description = "token 剩余有效期（秒）")
    private Integer ttlSeconds;

    /** 实际使用的凭据类型 */
    @Schema(description = "实际凭据类型")
    private String method;

    /** 操作码 */
    @Schema(description = "操作码")
    private String operationCode;
}
