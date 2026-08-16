package com.njydsz.common.web.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.message.MessageUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 端认证失败统一处理入口点
 *
 * <p>实现 Spring Security 的 {@link AuthenticationEntryPoint} 接口，
 * 当未认证的请求访问需要认证的资源时，返回统一的 401 响应。
 *
 * <p>响应格式：
 * <ul>
 *   <li>HTTP 状态码：401 Unauthorized</li>
 *   <li>响应体：{@link BaseResponse} 标准格式</li>
 *   <li>错误码：A02001（未登录）</li>
 *   <li>消息：支持国际化（i18n）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class WebAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("认证失败，请求路径: {}, 原因: {}", request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ExceptionCode errorCode = SecurityExceptionCode.AUTHENTICATION_REQUIRED;
        String message = MessageUtils.getMessage(errorCode.getKey(), errorCode.getKey());

        BaseResponse<?> body = BaseResponse.error(errorCode.getCode(), message);
        // 触发 traceId 懒加载，确保序列化时包含链路追踪 ID
        body.getTraceId();
        response.getWriter().write(YdszJson.toJson(body));
    }
}
