package com.njydsz.userinfo.web.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.web.version.ApiVersion;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.CrossDomainTokenService;
import com.njydsz.userinfo.server.auth.SessionManager;
import com.njydsz.userinfo.server.config.CrossDomainSsoProperties;

/**
 * 跨域 SSO 令牌交换端点。
 *
 * <p>为微前端子应用提供跨域 Token 生命周期管理：
 *
 * <ul>
 *   <li><b>令牌交换：</b>子应用用父域 Cookie 中的 Token 换取本域独立 Session（含新的 access_token）</li>
 *   <li><b>Token 验证：</b>子应用调用检查当前登录态是否有效</li>
 *   <li><b>登出通知：</b>父域登出时通知子域清除本地状态</li>
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /api/v1/sso}
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>所有端点校验请求来源 Origin 白名单</li>
 *   <li>令牌交换需验证父域 Token 有效性后签发新 Token</li>
 *   <li>登出通知需验证 Token 合法性后吊销对应会话</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see CrossDomainSsoProperties 跨域 SSO 配置
 * @see CrossDomainTokenService 跨域 Token 服务
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sso")
@RequiredArgsConstructor
@Tag(name = "跨域 SSO", description = "令牌交换/验证/登出通知")
@ApiVersion("1")
public class TokenExchangeController {

  private final CrossDomainSsoProperties ssoProperties;
  private final CrossDomainTokenService crossDomainTokenService;
  private final TokenService tokenService;
  private final SessionManager sessionManager;

  /**
   * 令牌交换：用父域 Token 换取子域可用 Token。
   *
   * <p>子应用通过跨域 Cookie 或 postMessage 获取父域 access_token 后，调用此端点换取新的 access_token。
   * 新 Token 与父域 Token 独立，拥有独立的 Session 生命周期。
   *
   * <p><b>请求来源校验：</b>仅允许 {@code ydsz.userinfo.sso.trusted-domains} 白名单中的 Origin 调用。
   *
   * @param request HTTP 请求（用于提取 Origin 头）
   * @param body 请求体（含父域 token）
   * @return 新的 access_token 和 refresh_token
   */
  @PostMapping("/token-exchange")
  @Operation(summary = "令牌交换", description = "用父域 Token 换取子域可用 Token")
  public BaseResponse<TokenExchangeVO> tokenExchange(
      HttpServletRequest request, @RequestBody TokenExchangeRequest body) {
    // 校验请求来源 Origin 白名单
    String origin = request.getHeader("Origin");
    if (!crossDomainTokenService.isTrustedDomain(origin, ssoProperties.getTrustedDomains())) {
      throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
    }

    String parentToken = body.getToken();
    if (parentToken == null || parentToken.isBlank()) {
      throw new BusinessException(UserInfoExceptionCode.SSO_TOKEN_EXCHANGE_FAILED);
    }

    // 验证父域 Token 有效性
    if (!tokenService.validateAccessToken(parentToken)) {
      throw new BusinessException(UserInfoExceptionCode.SSO_TOKEN_EXCHANGE_FAILED);
    }

    // 解析用户信息并签发新 Token
    UserInfo userInfo = tokenService.parseAccessToken(parentToken);
    if (userInfo == null) {
      throw new BusinessException(UserInfoExceptionCode.SSO_TOKEN_EXCHANGE_FAILED);
    }

    String newAccessToken = tokenService.issueAccessToken(userInfo);
    String newRefreshToken = tokenService.issueRefreshToken(userInfo);

    log.info(
        "Token exchanged successfully for user: {}, origin: {}",
        userInfo.getUsername(),
        origin);

    TokenExchangeVO vo = new TokenExchangeVO();
    vo.setAccessToken(newAccessToken);
    vo.setRefreshToken(newRefreshToken);
    vo.setTokenType("Bearer");
    return BaseResponse.success(vo);
  }

  /**
   * 验证 Token 有效性。
   *
   * <p>子应用调用此端点检查当前 access_token 是否仍然有效，用于判断用户登录态。
   * 支持从查询参数或 Authorization 头获取 Token。
   *
   * <p><b>请求来源校验：</b>仅允许白名单中的 Origin 调用。
   *
   * @param request HTTP 请求（用于提取 Origin 头）
   * @param token 访问令牌（查询参数，可选）
   * @param authorization Authorization 头（Bearer xxx，可选）
   * @return Token 验证结果
   */
  @GetMapping("/validate")
  @Operation(summary = "验证 Token 有效性", description = "子应用调用检查当前登录态")
  public BaseResponse<TokenValidateVO> validate(
      HttpServletRequest request,
      @RequestParam(value = "token", required = false) String token,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    // 校验请求来源 Origin 白名单
    String origin = request.getHeader("Origin");
    if (!crossDomainTokenService.isTrustedDomain(origin, ssoProperties.getTrustedDomains())) {
      throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
    }

    // 优先使用查询参数中的 token，其次从 Authorization 头提取
    String accessToken = token;
    if (accessToken == null && authorization != null && authorization.startsWith("Bearer ")) {
      accessToken = authorization.substring(7);
    }

    TokenValidateVO vo = new TokenValidateVO();
    if (accessToken == null || accessToken.isBlank()) {
      vo.setValid(false);
      vo.setMessage("Token is empty");
      return BaseResponse.success(vo);
    }

    boolean valid = tokenService.validateAccessToken(accessToken);
    vo.setValid(valid);
    if (valid) {
      UserInfo userInfo = tokenService.parseAccessToken(accessToken);
      if (userInfo != null) {
        vo.setUserId(userInfo.getUserId());
        vo.setUsername(userInfo.getUsername());
      }
      vo.setMessage("Token is valid");
    } else {
      vo.setMessage("Token is invalid or expired");
    }
    return BaseResponse.success(vo);
  }

  /**
   * 登出通知：父域登出时通知子域清除状态。
   *
   * <p>父域用户登出时，调用此端点吊销对应的 access_token，使子域 Session 同步失效。
   * 子应用在收到此通知后应清除本地存储的 Token。
   *
   * <p><b>请求来源校验：</b>仅允许白名单中的 Origin 调用。
   *
   * @param request HTTP 请求（用于提取 Origin 头）
   * @param body 请求体（含要吊销的 token）
   * @return 是否成功
   */
  @PostMapping("/logout-notify")
  @Operation(summary = "登出通知", description = "父域登出通知子域清除状态")
  public BaseResponse<LogoutNotifyVO> logoutNotify(
      HttpServletRequest request, @RequestBody LogoutNotifyRequest body) {
    // 校验请求来源 Origin 白名单
    String origin = request.getHeader("Origin");
    if (!crossDomainTokenService.isTrustedDomain(origin, ssoProperties.getTrustedDomains())) {
      throw new BusinessException(UserInfoExceptionCode.SSO_DOMAIN_NOT_TRUSTED);
    }

    String token = body.getToken();
    LogoutNotifyVO vo = new LogoutNotifyVO();

    if (token == null || token.isBlank()) {
      vo.setSuccess(false);
      vo.setMessage("Token is empty");
      return BaseResponse.success(vo);
    }

    try {
      sessionManager.revokeSession(token);
      vo.setSuccess(true);
      vo.setMessage("Session revoked successfully");
      log.info("Logout notification processed for token, origin: {}", origin);
    } catch (Exception e) {
      log.warn("Failed to process logout notification: {}", e.getMessage());
      vo.setSuccess(false);
      vo.setMessage("Failed to revoke session");
    }
    return BaseResponse.success(vo);
  }

  // ==================== 请求/响应 DTO ====================

  /**
   * 令牌交换请求体。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class TokenExchangeRequest {

    /** 父域 access_token（通过跨域 Cookie 或 postMessage 获取） */
    @NotBlank(message = "token must not be blank")
    private String token;
  }

  /**
   * 令牌交换响应体。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class TokenExchangeVO {

    /** 新的访问令牌 */
    private String accessToken;

    /** 新的刷新令牌 */
    private String refreshToken;

    /** 令牌类型 */
    private String tokenType;
  }

  /**
   * Token 验证响应体。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class TokenValidateVO {

    /** Token 是否有效 */
    private boolean valid;

    /** 用户 ID（Token 有效时返回） */
    private String userId;

    /** 用户名（Token 有效时返回） */
    private String username;

    /** 验证结果描述 */
    private String message;
  }

  /**
   * 登出通知请求体。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class LogoutNotifyRequest {

    /** 要吊销的 access_token */
    @NotBlank(message = "token must not be blank")
    private String token;
  }

  /**
   * 登出通知响应体。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @Data
  public static class LogoutNotifyVO {

    /** 是否成功吊销 */
    private boolean success;

    /** 结果描述 */
    private String message;
  }
}
