package com.njydsz.pmis.common.web.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.util.message.MessageUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 端权限不足统一处理入口点
 *
 * <p>实现 Spring Security 的 {@link AccessDeniedHandler} 接口，
 * 当已认证用户访问无权限的资源时，返回统一的 403 响应。
 *
 * <p>响应格式：
 * <ul>
 *   <li>HTTP 状态码：403 Forbidden</li>
 *   <li>响应体：{@link BaseResponse} 标准格式</li>
 *   <li>错误码：D01001（访问被拒绝）</li>
 *   <li>消息：支持国际化（i18n）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Slf4j
public class WebAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("权限不足，请求路径: {}, 原因: {}", request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ExceptionCode errorCode = UnifiedExceptionCode.ACCESS_DENIED;
        String message = MessageUtils.getMessage(errorCode.getKey(), errorCode.getKey());

        BaseResponse<?> body = BaseResponse.error(errorCode.getCode(), message);
        response.getWriter().write(Json.toJson(body));
    }
}
