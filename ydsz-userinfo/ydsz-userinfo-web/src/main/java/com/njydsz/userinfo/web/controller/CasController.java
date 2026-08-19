package com.njydsz.userinfo.web.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.CasService;
import com.njydsz.userinfo.server.config.CasProperties;

/**
 * CAS 协议控制器。
 *
 * <p>实现 CAS 2.0/3.0 协议的标准端点，提供单点登录/登出能力。
 *
 * <p><b>接口路径：</b>{@code /cas}
 *
 * <p><b>CAS 协议流程：</b>
 *
 * <ol>
 *   <li>客户端应用将用户重定向到 {@code /cas/login?service=...}</li>
 *   <li>用户输入用户名密码完成认证</li>
 *   <li>认证成功后，服务端签发 TGT（存入 Cookie）和 ST（重定向回客户端）</li>
 *   <li>客户端应用调用 {@code /cas/serviceValidate?ticket=...&amp;service=...} 验证 ST</li>
 *   <li>验证成功，客户端应用建立本地会话</li>
 * </ol>
 *
 * <p><b>单点登出：</b>用户访问 {@code /cas/logout} 时，清除 TGT Cookie 并删除 Redis 中的 TGT。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@RestController
@RequestMapping("/cas")
@RequiredArgsConstructor
public class CasController {

  /** TGT Cookie 名称 */
  private static final String TGT_COOKIE_NAME = "TGC";

  /** TGT Cookie 路径 */
  private static final String TGT_COOKIE_PATH = "/cas";

  private final CasService casService;
  private final AuthService authService;
  private final CasProperties casProperties;

  /**
   * CAS 登录端点。
   *
   * <p>处理 CAS 协议的登录请求。如果用户已有有效的 TGT（通过 Cookie），则直接签发 ST 并重定向回服务；
   * 否则返回登录页面。
   *
   * <p><b>请求参数：</b>
   *
   * <ul>
   *   <li>{@code service} — 客户端应用的服务 URL（登录成功后重定向的目标）</li>
   *   <li>{@code username} — 用户名（登录表单提交）</li>
   *   <li>{@code password} — 密码（登录表单提交）</li>
   * </ul>
   *
   * @param service 服务 URL（必填）
   * @param username 用户名（登录时提交）
   * @param password 密码（登录时提交）
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 重定向到服务 URL（含 ST）或登录页面
   */
  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> login(
      @RequestParam("service") String service,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String password,
      HttpServletRequest request,
      HttpServletResponse response) {

    // 1. 检查是否已有有效的 TGT（通过 Cookie）
    String tgtId = extractTgtFromCookie(request);
    if (tgtId != null && username == null) {
      // 有 TGT 且未提交登录表单，直接签发 ST 并重定向
      return redirectWithServiceTicket(tgtId, service, response);
    }

    // 2. 如果提交了登录表单，进行认证
    if (username != null && password != null) {
      return handleLogin(username, password, service, response);
    }

    // 3. 返回登录页面
    return ResponseEntity.ok(buildLoginPageHtml(service));
  }

  /**
   * CAS 登录端点（POST 表单提交）。
   *
   * <p>处理登录表单的 POST 请求。
   *
   * @param service 服务 URL
   * @param username 用户名
   * @param password 密码
   * @param response HTTP 响应
   * @return 重定向到服务 URL（含 ST）
   */
  @PostMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> loginPost(
      @RequestParam("service") String service,
      @RequestParam("username") String username,
      @RequestParam("password") String password,
      HttpServletResponse response) {
    return handleLogin(username, password, service, response);
  }

  /**
   * CAS Service Ticket 校验端点（CAS 2.0）。
   *
   * <p>客户端应用通过此端点验证 ST 的有效性，获取用户信息。
   *
   * <p><b>请求参数：</b>
   *
   * <ul>
   *   <li>{@code ticket} — Service Ticket</li>
   *   <li>{@code service} — 服务 URL（必须与签发时一致）</li>
   * </ul>
   *
   * <p><b>响应格式：</b>CAS 2.0 XML 格式
   *
   * @param ticket Service Ticket
   * @param service 服务 URL
   * @return CAS 2.0 XML 响应
   */
  @GetMapping(value = "/serviceValidate", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> serviceValidate(
      @RequestParam("ticket") String ticket,
      @RequestParam("service") String service) {
    try {
      CasService.CasServiceTicketValidationResult result =
          casService.validateServiceTicket(ticket, service);

      // 构建 CAS 2.0 成功响应
      String xmlResponse = String.format(
          """
              <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                <cas:authenticationSuccess>
                  <cas:user>%s</cas:user>
                </cas:authenticationSuccess>
              </cas:serviceResponse>
              """,
          escapeXml(result.getUsername()));

      return ResponseEntity.ok(xmlResponse);
    } catch (Exception e) {
      // 构建 CAS 2.0 失败响应
      String xmlResponse = """
          <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
            <cas:authenticationFailure code="INVALID_TICKET">
              Ticket not recognized
            </cas:authenticationFailure>
          </cas:serviceResponse>
          """;

      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(xmlResponse);
    }
  }

  /**
   * CAS Service Ticket 校验端点（CAS 3.0 / JSON）。
   *
   * <p>与 {@link #serviceValidate} 功能相同，但返回 JSON 格式。
   *
   * @param ticket Service Ticket
   * @param service 服务 URL
   * @return JSON 响应
   */
  @GetMapping(value = "/p3/serviceValidate", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<YdszResponse<CasValidateResponse>> serviceValidateJson(
      @RequestParam("ticket") String ticket,
      @RequestParam("service") String service) {
    try {
      CasService.CasServiceTicketValidationResult result =
          casService.validateServiceTicket(ticket, service);

      CasValidateResponse validateResponse = new CasValidateResponse();
      validateResponse.setUsername(result.getUsername());
      validateResponse.setUserId(result.getUserId());

      return ResponseEntity.ok(YdszResponse.success(validateResponse));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(YdszResponse.error("INVALID_TICKET", "Ticket not recognized"));
    }
  }

  /**
   * CAS 登出端点。
   *
   * <p>处理 CAS 协议的登出请求，清除 TGT Cookie 并删除 Redis 中的 TGT。
   *
   * <p><b>请求参数：</b>
   *
   * <ul>
   *   <li>{@code service} — 登出后重定向的服务 URL（可选）</li>
   * </ul>
   *
   * @param service 登出后重定向的服务 URL（可选）
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 重定向到服务 URL 或登出成功页面
   */
  @GetMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestParam(required = false) String service,
      HttpServletRequest request,
      HttpServletResponse response) {

    // 1. 获取 TGT
    String tgtId = extractTgtFromCookie(request);
    if (tgtId != null) {
      // 2. 删除 Redis 中的 TGT
      casService.logout(tgtId);

      // 3. 清除 TGT Cookie
      clearTgtCookie(response);
    }

    // 4. 重定向到服务 URL 或返回成功
    if (service != null && !service.isBlank()) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .header("Location", service)
          .build();
    }

    return ResponseEntity.ok().build();
  }

  /**
   * 处理登录请求。
   *
   * @param username 用户名
   * @param password 密码
   * @param service 服务 URL
   * @param response HTTP 响应
   * @return 重定向到服务 URL（含 ST）
   */
  private ResponseEntity<String> handleLogin(
      String username, String password, String service, HttpServletResponse response) {
    try {
      // 1. 构建登录 DTO
      com.njydsz.userinfo.domain.dto.LoginDTO loginDTO =
          new com.njydsz.userinfo.domain.dto.LoginDTO();
      loginDTO.setUsername(username);
      loginDTO.setPassword(password);

      // 2. 认证用户
      com.njydsz.userinfo.domain.vo.LoginVO loginVO = authService.login(loginDTO, response);
      if (loginVO == null || loginVO.getUserInfo() == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(buildLoginPageHtml(service, "用户名或密码错误"));
      }

      // 3. 构建用户 VO
      UserAccountVO userVO = new UserAccountVO();
      userVO.setId(loginVO.getUserInfo().getUserId());
      userVO.setUsername(loginVO.getUserInfo().getUsername());

      // 4. 签发 TGT
      String tgtId = casService.issueTicketGrantingTicket(userVO);

      // 5. 设置 TGT Cookie
      setTgtCookie(response, tgtId);

      // 6. 签发 ST 并重定向
      return redirectWithServiceTicket(tgtId, service, response);
    } catch (Exception e) {
      log.error("CAS 登录失败: username={}, error={}", username, e.getMessage());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(buildLoginPageHtml(service, "登录失败，请重试"));
    }
  }

  /**
   * 签发 ST 并重定向到服务 URL。
   *
   * @param tgtId TGT ID
   * @param service 服务 URL
   * @param response HTTP 响应
   * @return 重定向响应
   */
  private ResponseEntity<String> redirectWithServiceTicket(
      String tgtId, String service, HttpServletResponse response) {
    // 1. 签发 ST
    String stId = casService.issueServiceTicket(tgtId, service);

    // 2. 构建重定向 URL
    String redirectUrl = service + (service.contains("?") ? "&" : "?") + "ticket=" + stId;

    // 3. 返回重定向
    return ResponseEntity.status(HttpStatus.FOUND)
        .header("Location", redirectUrl)
        .build();
  }

  /**
   * 从 Cookie 中提取 TGT。
   *
   * @param request HTTP 请求
   * @return TGT ID，不存在返回 null
   */
  private String extractTgtFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (TGT_COOKIE_NAME.equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  /**
   * 设置 TGT Cookie。
   *
   * @param response HTTP 响应
   * @param tgtId TGT ID
   */
  private void setTgtCookie(HttpServletResponse response, String tgtId) {
    Cookie cookie = new Cookie(TGT_COOKIE_NAME, tgtId);
    cookie.setPath(TGT_COOKIE_PATH);
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setMaxAge((int) casProperties.getTicketGrantingTicketTtl().getSeconds());
    response.addCookie(cookie);
  }

  /**
   * 清除 TGT Cookie。
   *
   * @param response HTTP 响应
   */
  private void clearTgtCookie(HttpServletResponse response) {
    Cookie cookie = new Cookie(TGT_COOKIE_NAME, null);
    cookie.setPath(TGT_COOKIE_PATH);
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setMaxAge(0);
    response.addCookie(cookie);
  }

  /**
   * 构建登录页面 HTML。
   *
   * @param service 服务 URL
   * @return HTML 字符串
   */
  private String buildLoginPageHtml(String service) {
    return buildLoginPageHtml(service, null);
  }

  /**
   * 构建登录页面 HTML（含错误消息）。
   *
   * @param service 服务 URL
   * @param errorMessage 错误消息（可选）
   * @return HTML 字符串
   */
  private String buildLoginPageHtml(String service, String errorMessage) {
    String encodedService = URLEncoder.encode(service, StandardCharsets.UTF_8);
    String errorHtml = errorMessage != null
        ? "<p style=\"color: red;\">" + escapeHtml(errorMessage) + "</p>"
        : "";

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <title>CAS Login</title>
        </head>
        <body>
          <h2>统一身份认证</h2>
        """
        + errorHtml
        + """
          <form method="post" action="/cas/login?service="""
        + encodedService
        + """">
            <input type="hidden" name="service" value="""
        + escapeHtml(service)
        + """">
            <p>
              <label>用户名: <input type="text" name="username" required></label>
            </p>
            <p>
              <label>密码: <input type="password" name="password" required></label>
            </p>
            <p>
              <button type="submit">登录</button>
            </p>
          </form>
        </body>
        </html>
        """;
  }

  /**
   * XML 转义。
   *
   * @param input 输入字符串
   * @return 转义后的字符串
   */
  private String escapeXml(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /**
   * HTML 转义。
   *
   * @param input 输入字符串
   * @return 转义后的字符串
   */
  private String escapeHtml(String input) {
    return escapeXml(input);
  }

  /**
   * CAS 校验响应。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @lombok.Data
  public static class CasValidateResponse {

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;
  }
}
