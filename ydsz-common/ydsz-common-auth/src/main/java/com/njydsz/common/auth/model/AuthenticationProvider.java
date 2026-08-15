package com.njydsz.common.auth.model;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.util.auth.AuthInfo;

/**
 * 认证提供者接口（策略模式）
 *
 * <p>认证逻辑的抽象接口，允许业务方通过 SPI 或 Spring
 * {@code @ConditionalOnBean} 注入不同的认证实现。
 *
 * <p><b>废弃原因：</b>与 {@link com.njydsz.common.auth.handler.AuthHandler} 功能重叠，
 * 认证逻辑统一通过 AuthHandler 处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             迁移目标：{@link com.njydsz.common.auth.handler.AuthHandler}。
 */
@Deprecated(forRemoval = true, since = "3.0.0")
@FunctionalInterface
public interface AuthenticationProvider {

    /**
     * 执行认证逻辑
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @return 认证信息
     */
    AuthInfo authenticate(HttpServletRequest request, HttpServletResponse response);
}
