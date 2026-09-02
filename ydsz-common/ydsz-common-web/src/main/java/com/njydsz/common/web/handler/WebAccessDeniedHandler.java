package com.njydsz.common.web.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.message.MessageUtils;

/**
 * Web 端权限不足统一处理入口点
 *
 * <p>实现 Spring Security 的 {@link AccessDeniedHandler} 接口， 当已认证用户访问无权限的资源时，返回统一的 403 响应。
 *
 * <p>响应格式：
 *
 * <ul>
 *   <li>HTTP 状态码：403 Forbidden
 *   <li>响应体：{@link YdszResponse} 标准格式
 *   <li>错误码：D01001（访问被拒绝）
 *   <li>消息：支持国际化（i18n）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class WebAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    log.warn("权限不足，请求路径: {}, 原因: {}", request.getRequestURI(), accessDeniedException.getMessage());

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ExceptionCode errorCode = SecurityExceptionCode.ACCESS_DENIED;
    String message = MessageUtils.getMessage(errorCode.getKey(), errorCode.getKey());

    YdszResponse<?> body = YdszResponse.error(errorCode.getCode(), message);
    // 触发 traceId 懒加载，确保序列化时包含链路追踪 ID
    body.getTraceId();
    response.getWriter().write(YdszJson.toJson(body));
  }
}
