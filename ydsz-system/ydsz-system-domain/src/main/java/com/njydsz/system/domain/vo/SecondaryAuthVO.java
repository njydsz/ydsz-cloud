package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 二次认证响应 VO。
 *
 * <p>密码校验通过后颁发的短期令牌，前端在后续敏感操作请求头 {@code X-Secondary-Auth} 中携带该令牌。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@Schema(description = "二次认证响应")
public class SecondaryAuthVO {

  /** 认证通过令牌（后续请求头 X-Secondary-Auth 携带） */
  @Schema(description = "认证令牌")
  private String token;

  /** 有效期（毫秒） */
  @Schema(description = "有效期（毫秒）")
  private long expiresIn;

  /** 过期时间戳（Unix 毫秒） */
  @Schema(description = "过期时间戳（Unix 毫秒）")
  private long expiresAt;
}
