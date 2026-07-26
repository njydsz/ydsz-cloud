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
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component
 * public class JwtAuthenticationProvider implements AuthenticationProvider {
 *     @Override
 *     public AuthInfo authenticate(HttpServletRequest request, HttpServletResponse response) {
 *         // JWT 解析逻辑
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
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
