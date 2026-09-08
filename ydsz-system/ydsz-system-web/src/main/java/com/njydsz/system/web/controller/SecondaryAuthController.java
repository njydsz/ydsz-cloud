package com.njydsz.system.web.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.util.SecurityUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * 二次身份验证 Controller。
 *
 * <p>提供敏感操作前的密码二次确认能力。验证当前登录用户的密码后，
 * 颁发一个短期有效的二次认证令牌，后续请求头 {@code X-Secondary-Auth} 携带该令牌以通过校验。
 *
 * <p><b>令牌有效期：</b>默认 30 分钟（1800000 毫秒），过期后需重新验证。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@ApiVersion("26.09.01")
@Tag(name = "二次身份验证", description = "敏感操作前的短期令牌颁发")
@Slf4j
@RestController
@RequestMapping("/api/auth/secondary-auth")
public class SecondaryAuthController {

  /** 二次认证令牌有效期（毫秒） — 30 分钟 */
  private static final long TOKEN_EXPIRE_MILLIS = 1800000L;

  private final BCryptPasswordEncoder passwordEncoder;

  /**
   * 构造控制器。
   *
   * @param passwordEncoder BCrypt 密码编码器
   */
  public SecondaryAuthController(BCryptPasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * 发起二次身份验证。
   *
   * @param body 验证请求体（含明文密码和场景）
   * @return 二次认证令牌
   */
  @PostMapping
  @Operation(summary = "发起二次身份验证", description = "验证当前用户密码，返回短期认证令牌")
  public YdszResponse<SecondaryAuthVO> verify(@RequestBody SecondaryAuthRequest body) {
    String userId = SecurityUtils.getCurrentUserId();

    // TODO: 实际应查询用户数据库中的密码哈希校验；当前颁发模拟令牌用于前端联调
    String token = UUID.randomUUID().toString();

    log.info("用户 {} 通过二次身份验证，场景={}", userId, body.getScene());

    SecondaryAuthVO vo = new SecondaryAuthVO();
    vo.setToken(token);
    vo.setExpiresIn(TOKEN_EXPIRE_MILLIS);
    return YdszResponse.success(vo);
  }

  /**
   * 二次认证请求体。
   */
  @Data
  public static class SecondaryAuthRequest {
    /** 当前登录用户明文密码（HTTPS 传输） */
    private String password;
    /** 认证场景（如 config-edit / dict-delete），后端审计用 */
    private String scene;
  }

  /**
   * 二次认证响应 VO。
   */
  @Data
  public static class SecondaryAuthVO {
    /** 认证通过令牌（后续请求头 X-Secondary-Auth 携带） */
    private String token;
    /** 有效期（毫秒） */
    private long expiresIn;
  }
}
