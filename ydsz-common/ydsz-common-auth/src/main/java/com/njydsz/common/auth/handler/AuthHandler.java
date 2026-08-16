package com.njydsz.common.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.auth.model.AuthInfo;

/**
 * 认证信息处理接口
 *
 * <p>定义从 HTTP 请求中解析和构建认证信息的标准契约。
 * Web 端和 App 端各自实现此接口，适配不同的请求头解析逻辑。
 *
 * <p>自 v2.0.0 起从 util 层迁移至 common-auth 服务层，成为认证能力统一入口。
 * 已移除对旧版弃用接口的继承依赖。
 *
 * @author ydsz-team
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
