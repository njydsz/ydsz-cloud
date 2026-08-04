package com.njydsz.common.util.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * CookieUtils - Cookie 操作工具类 (增强版)
 *
 * <p>支持 SameSite 属性（Strict / Lax / None）与反代场景下的 Secure 自动检测
 * （读取 {@code X-Forwarded-Proto} 头）。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Slf4j
public class CookieUtils {

    /**
     * SameSite 属性枚举，用于防御 CSRF 与跨站追踪。
     *
     * @see <a href="https://developer.mozilla.org/docs/Web/HTTP/Headers/Set-Cookie/SameSite">MDN SameSite</a>
     */
    public enum SameSite {
        /** 严格模式：跨站请求一律不发送 Cookie（最安全，可能影响用户体验） */
        STRICT("Strict"),
        /** 宽松模式：顶级导航和 GET 请求发送 Cookie（默认推荐） */
        LAX("Lax"),
        /** 无限制：跨站请求均发送 Cookie（必须同时设置 Secure） */
        NONE("None");

        private final String attribute;

        SameSite(String attribute) {
            this.attribute = attribute;
        }

        public String attribute() {
            return attribute;
        }
    }

    /**
     * 按名称获取 cookie
     *
     * @param name    Cookie 名称
     * @param request HTTP 请求
     * @return Cookie 对象，未找到返回 null
     */
    public static Cookie getCookie(String name, HttpServletRequest request) {
        if (StringUtils.isEmpty(name) || request == null) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie;
                }
            }
        }
        return null;
    }

    /**
     * 按名称获取 cookie 中的值
     *
     * @param name    Cookie 名称
     * @param request HTTP 请求
     * @return Cookie 值，未找到返回 null
     */
    public static String getCookieValue(String name, HttpServletRequest request) {
        Cookie cookie = getCookie(name, request);
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * 按名称获取 cookie 中的值（支持 URL 解码）
     */
    public static String getCookieValueDecoded(String name, HttpServletRequest request) {
        String value = getCookieValue(name, request);
        return StringUtils.isEmpty(value) ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 添加 cookie (默认开启 HttpOnly + SameSite=Lax，Secure 校验支持反代)
     *
     * <p>Secure 判断优先级：{@code X-Forwarded-Proto} 头 → {@code request.getScheme()}，
     * 兼容 Nginx / SLB 等反向代理场景。
     */
    public static void addCookie(String name, String value, String path, HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.isEmpty(name) || value == null || response == null) {
            return;
        }

        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        if (path != null) {
            cookie.setPath(path);
        }
        if (request != null) {
            cookie.setSecure(isSecureRequest(request));
        } else {
            log.warn("addCookie -> request 为 null，无法设置 Secure 标志");
        }
        cookie.setHttpOnly(true);
        appendSameSiteAndAdd(cookie, SameSite.LAX, response);
    }

    /**
     * 添加 cookie（可自定义配置，含 SameSite 属性）。
     *
     * @param name     Cookie 名称
     * @param value    Cookie 值
     * @param maxAge   有效期（秒）
     * @param path     路径
     * @param httpOnly 是否仅 HTTP
     * @param secure   是否仅 HTTPS
     * @param sameSite SameSite 策略；null 表示不写入 SameSite 属性
     * @param response HTTP 响应
     */
    public static void addCookie(String name, String value, int maxAge, String path,
                                  boolean httpOnly, boolean secure, SameSite sameSite,
                                  HttpServletResponse response) {
        if (StringUtils.isEmpty(name) || value == null || response == null) {
            return;
        }

        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        cookie.setPath(path != null ? path : "/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        appendSameSiteAndAdd(cookie, sameSite, response);
    }

    /**
     * 添加会话 cookie（浏览器关闭即失效，默认 SameSite=Lax）
     *
     * @param name     Cookie 名称
     * @param value    Cookie 值
     * @param path     路径
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    public static void addSessionCookie(String name, String value, String path,
                                        HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        cookie.setPath(path != null ? path : "/");
        if (request != null) {
            cookie.setSecure(isSecureRequest(request));
        }
        cookie.setHttpOnly(true);
        appendSameSiteAndAdd(cookie, SameSite.LAX, response);
    }

    /**
     * 清除指定名称的 cookie
     *
     * @param name     Cookie 名称
     * @param path     路径
     * @param response HTTP 响应
     */
    public static void removeCookie(String name, String path, HttpServletResponse response) {
        if (StringUtils.isEmpty(name) || response == null) {
            return;
        }
        Cookie cookie = new Cookie(name, null);
        cookie.setPath(path != null ? path : "/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 清除所有 cookie
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    public static void removeAllCookies(HttpServletRequest request, HttpServletResponse response) {
        if (request == null || response == null) {
            return;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Arrays.stream(cookies)
                    .map(Cookie::getName)
                    .forEach(name -> removeCookie(name, "/", response));
        }
    }

    /**
     * 获取所有 cookie
     *
     * <p>返回不可变 Map，防止调用方意外修改内部结构。
     */
    public static Map<String, String> getAllCookies(HttpServletRequest request) {
        Map<String, String> cookieMap = new HashMap<>();
        if (request == null) {
            return Collections.unmodifiableMap(cookieMap);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        }
        return Collections.unmodifiableMap(cookieMap);
    }

    /**
     * 检查 cookie 是否存在
     */
    public static boolean hasCookie(String name, HttpServletRequest request) {
        return getCookie(name, request) != null;
    }

    /**
     * 批量添加 cookie
     */
    public static void addCookies(Map<String, String> cookies, String path,
                                  HttpServletRequest request, HttpServletResponse response) {
        if (cookies == null || cookies.isEmpty() || response == null) {
            return;
        }
        cookies.forEach((name, value) -> addCookie(name, value, path, request, response));
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 判断请求是否为 HTTPS（兼容反代场景）。
     *
     * <p>仅当直连对端为可信代理（{@link ServletUtils#isTrustedProxy(HttpServletRequest)}）时，
     * 才信任 {@code X-Forwarded-Proto} 头；否则忽略该头直接使用 {@code request.isSecure()}，
     * 防止客户端通过伪造转发头使 Cookie 缺失 Secure 标志。
     */
    private static boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        // 仅当直连对端为可信代理时，才信任 X-Forwarded-Proto 头，防止客户端伪造
        if (ServletUtils.isTrustedProxy(request.getRemoteAddr())) {
            String forwardedProto = request.getHeader("X-Forwarded-Proto");
            if ("https".equalsIgnoreCase(forwardedProto)) {
                return true;
            }
        }
        return request.isSecure();
    }

    /**
     * 写入 Cookie 并附加 SameSite 属性。
     *
     * <p>Servlet 6.0 的 {@link Cookie} API 不直接支持 SameSite，
     * 需通过 {@code Set-Cookie} 响应头手动附加。
     * 当 sameSite 为 null 时不附加该属性。
     */
    private static void appendSameSiteAndAdd(Cookie cookie, SameSite sameSite, HttpServletResponse response) {
        if (sameSite == null) {
            response.addCookie(cookie);
            return;
        }
        // 使用 setHeader 写入完整 Set-Cookie，确保 SameSite 属性生效
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append('=').append(cookie.getValue());
        if (cookie.getPath() != null) {
            sb.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getMaxAge() >= 0) {
            sb.append("; Max-Age=").append(cookie.getMaxAge());
        }
        if (cookie.getSecure()) {
            sb.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            sb.append("; HttpOnly");
        }
        sb.append("; SameSite=").append(sameSite.attribute());
        response.addHeader("Set-Cookie", sb.toString());
    }
}
