package com.njydsz.agent.web.handler;

// JDK
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 第三方
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 本项目
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.common.exception.handler.BaseExceptionHandler;

/**
 * AI 代理模块全局异常处理器，统一处理 AI 代理相关接口的异常响应，
 * 并额外处理 {@link LlmException}，携带错误类型信息记录日志。
 *
 * @author ydsz
 * @since 2025/11/06
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentExceptionHandler extends BaseExceptionHandler {

  protected AgentExceptionHandler(Environment environment) {
    super(environment);
  }

  @Override
  protected String getLogPrefix() {
    return "【Agent】";
  }

  /**
   * 处理 LLM 调用异常，额外记录错误类型信息。
   *
   * <p>当 LLM API 调用失败（网络超时、认证失败、模型不可用等）时，
   * 本方法拦截异常并记录详细的错误类型，便于问题排查。
   * 响应构建委托给 {@link #buildResponse(Throwable, String, String)}。
   *
   * @param e LLM 调用异常
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 标准错误响应
   */
  @ExceptionHandler(LlmException.class)
  public Object handleLlmException(
      LlmException e, HttpServletRequest request, HttpServletResponse response) {
    recordMetrics(e);
    log.warn(
        "{}LLM 调用异常 | 路径: {} | 错误类型: {} | 消息: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getErrorType(),
        e.getMessage(),
        e);

    response.setStatus(e.getHttpStatus());
    addRetryAfterHeader(response, e);
    String traceId = extractTraceId(request);
    String resolvedMsg = e.getMessage();
    publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
    return buildResponse(e, request.getRequestURI(), traceId);
  }
}
