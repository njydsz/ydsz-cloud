package com.njydsz.common.util.http;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.util.string.StringUtils;

/**
 * CookieUtils - Cookie 操作工具类 (增强版)
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class CookieUtils {

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
     * 添加 cookie (默认开启 HttpOnly 和 Secure 校验)
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
            cookie.setSecure("https".equals(request.getScheme()));
        }
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 添加 cookie（可自定义配置）
     */
    public static void addCookie(String name, String value, int maxAge, String path,
                                  boolean httpOnly, boolean secure, HttpServletResponse response) {
        if (StringUtils.isEmpty(name) || value == null || response == null) {
            return;
        }

        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        cookie.setPath(path != null ? path : "/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        response.addCookie(cookie);
    }

    /**
     * 添加会话 cookie（浏览器关闭即失效）
     */
    public static void addSessionCookie(String name, String value, String path,
                                        HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie(name, URLEncoder.encode(value, StandardCharsets.UTF_8));
        cookie.setPath(path != null ? path : "/");
        if (request != null) {
            cookie.setSecure("https".equals(request.getScheme()));
        }
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * 清除 cookie
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
     */
    public static Map<String, String> getAllCookies(HttpServletRequest request) {
        Map<String, String> cookieMap = new HashMap<>();
        if (request == null) {
            return cookieMap;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookieMap.put(cookie.getName(), cookie.getValue());
            }
        }
        return cookieMap;
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
}
