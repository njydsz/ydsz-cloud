package com.njydsz.pmis.common.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.pmis.common.util.auth.AuthInfo;

/**
 * 认证信息处理接口
 *
 * <p>定义从 HTTP 请求中解析和构建认证信息的标准契约。
 * Web 端和 App 端各自实现此接口，适配不同的请求头解析逻辑。
 *
 * <p>从 {@code com.njydsz.pmis.common.util.auth.AuthHandler} 迁移而来，
 * 已移除对旧版弃用接口的继承依赖。
 *
 * @since 1.0.0
 * 
 * @see AbstractAuthHandler
 */
public interface AuthHandler {

    /**
     * 从 HTTP 请求中解析认证信息
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 解析后的认证信息
     */
    AuthInfo getAuthInfo(HttpServletRequest request, HttpServletResponse response);
}
