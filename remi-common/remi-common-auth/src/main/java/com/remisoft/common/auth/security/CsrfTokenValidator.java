package com.remisoft.common.auth.security;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.common.util.string.StringUtils;
import com.remisoft.common.util.id.IdGenerator;

/**
 * CSRF Token 验证器（双重提交 Cookie 模式）。
 *
 * <p>采用双重提交 Cookie（Double Submit Cookie）模式防御 CSRF 攻击：
 * <ul>
 *   <li>客户端在 Cookie 中存储 CSRF Token</li>
 *   <li>客户端在请求头 X-CSRF-Token 中携带相同的 Token</li>
 *   <li>服务端比较两者是否一致</li>
 * </ul>
 *
 * <p>对于 Token-based 认证（JWT in Authorization Header），CSRF 风险较低，
 * 因为攻击者无法跨域读取 JWT Token。但作为纵深防御措施仍建议启用。
 *
 * @author remi-team
 * @since 1.0.0

 */
public class CsrfTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenValidator.class);

    /**
     * CSRF Token 请求头名称
     */
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    /**
     * CSRF Token Cookie 名称
     */
    public static final String CSRF_COOKIE_NAME = "remi-csrf-token";

    private final boolean enabled;

    public CsrfTokenValidator(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 生成新的 CSRF Token。
     *
     * @return UUID 格式的 CSRF Token
     */
    public String generateToken() {
        return IdGenerator.nextIdStr();
    }

    /**
     * 设置 CSRF Cookie 到响应中。
     *
     * @param response HTTP 响应
     * @param token   CSRF Token
     */
    public void setCsrfCookie(HttpServletResponse response, String token) {
        if (!enabled || token == null) {
            return;
        }
        String cookie = String.format("%s=%s; Path=/; SameSite=Strict; Secure; HttpOnly",
                CSRF_COOKIE_NAME, token);
        response.setHeader("Set-Cookie", cookie);
    }

    /**
     * 校验 CSRF Token。
     *
     * <p>比较请求头中的 Token 与 Cookie 中的 Token 是否一致。
     *
     * @param request HTTP 请求
     * @return 校验通过返回 true，未启用或校验失败返回 false
     */
    public boolean validate(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }

        String headerToken = request.getHeader(CSRF_HEADER_NAME);
        String cookieToken = getCookieValue(request, CSRF_COOKIE_NAME);

        if (StringUtils.isBlank(headerToken) || StringUtils.isBlank(cookieToken)) {
            log.debug("CSRF Token 缺失: header={}, cookie={}",
                    headerToken != null ? "present" : "missing",
                    cookieToken != null ? "present" : "missing");
            return false;
        }

        if (!constantTimeEquals(headerToken, cookieToken)) {
            log.warn("CSRF Token 不匹配");
            return false;
        }

        return true;
    }

    /**
     * 判断 CSRF 防护是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 恒定时间比较，防止时序攻击。
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
