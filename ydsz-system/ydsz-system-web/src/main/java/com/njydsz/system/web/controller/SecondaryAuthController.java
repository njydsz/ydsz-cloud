package com.njydsz.system.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.util.SecurityUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.domain.vo.SecondaryAuthVO;
import com.njydsz.system.server.service.SecondaryAuthService;

/**
 * 二次身份验证 Controller。
 *
 * <p>提供敏感操作前的密码二次确认能力。验证当前登录用户的密码后（通过 Feign 调用用户中心服务），
 * 颁发一个短期有效的二次认证令牌，后续请求头 {@code X-Secondary-Auth} 携带该令牌以通过校验。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>密码校验通过 Feign 调用用户中心服务，不在本服务存储密码哈希</li>
 *   <li>校验结果（令牌）存储在 Redis，TTL 30 分钟（可通过 {@code ydsz.system.secondary-auth.token-ttl-minutes} 调整）</li>
 *   <li>连续失败 5 次后锁定 15 分钟（可通过 {@code ydsz.system.secondary-auth.max-fail-count} 和
 *       {@code ydsz.system.secondary-auth.fail-lock-minutes} 调整）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@ApiVersion("26.09.08")
@Tag(name = "二次身份验证", description = "敏感操作前的密码确认与令牌颁发")
@Slf4j
@RestController
@RequestMapping("/auth/secondary-auth")
@RequiredArgsConstructor
public class SecondaryAuthController {

  private final SecondaryAuthService secondaryAuthService;

  /**
   * 发起二次身份验证。
   *
   * <p>校验当前登录用户的密码，通过后颁发一个短期有效的令牌。前端需在后续敏感操作请求头
   * {@code X-Secondary-Auth} 携带该令牌。
   *
   * @param body 验证请求体（含明文密码和场景）
   * @return 二次认证令牌（含 token、expiresIn、expiresAt）
   */
  @PostMapping
  @Operation(summary = "发起二次身份验证", description = "验证当前用户密码，返回短期认证令牌")
  public YdszResponse<SecondaryAuthVO> verify(@RequestBody @Valid SecondaryAuthRequest body) {
    String userId = SecurityUtils.getCurrentUserId();
    SecondaryAuthVO vo = secondaryAuthService.verify(userId, body.getPassword(), body.getScene());
    return YdszResponse.success(vo);
  }

  /**
   * 二次认证请求体。
   */
  @Data
  public static class SecondaryAuthRequest {
    /** 当前登录用户明文密码（HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 认证场景（如 config-edit / dict-delete），后端审计用 */
    private String scene;
  }
}
