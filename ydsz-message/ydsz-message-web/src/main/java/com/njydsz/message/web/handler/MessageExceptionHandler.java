package com.njydsz.message.web.handler;

import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.enums.MessageExceptionCode;

/**
 * 消息中心模块全局异常处理器。
 *
 * <p>拦截消息模块抛出的 {@link SysException} / {@link BusinessException}，当异常码为 {@link
 * MessageExceptionCode} 时，构造分层错误消息（userMessage / developerMessage / retryAfter）写入
 * {@link MessageResult}，返回 {@code YdszResponse<MessageResult>} 供前端解析。
 *
 * <p>分层规则：
 *
 * <ul>
 *   <li>{@code userMessage} — 取自 {@link AbstractYdszException#getMessage()}，已走 i18n 解析，前端直接展示
 *   <li>{@code developerMessage} — 异常类名 + 详情 + cause 链，供开发者调试
 *   <li>{@code retryAfter} — 取自 {@link MessageExceptionCode#retryAfterSeconds()}，0 表示不可重试
 * </ul>
 *
 * <p>非 {@link MessageExceptionCode} 异常不拦截，交由 common 模块的 {@code MvcExceptionHandler} 处理。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 10，在 common {@code MvcExceptionHandler}
 * 之后执行，仅捕获其中会透传到 HTTP 响应的 {@link MessageExceptionCode} 异常。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see MessageExceptionCode
 * @see MessageResult#fail(String, String, String, Integer)
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MessageExceptionHandler {

  /**
   * 处理消息模块的业务异常 / 系统异常。
   *
   * <p>仅当异常码为 {@link MessageExceptionCode} 时构造分层 {@link MessageResult} 响应；其他异常类型不处理， 交由上层
   * {@code MvcExceptionHandler} 兜底。
   *
   * @param e 消息模块抛出的异常（{@link SysException} 或 {@link BusinessException}）
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 分层错误响应；非 {@link MessageExceptionCode} 异常返回 null 由上层处理
   */
  @ExceptionHandler({SysException.class, BusinessException.class})
  public YdszResponse<MessageResult> handleMessageException(
      AbstractYdszException e, HttpServletRequest request, HttpServletResponse response) {

    // 仅处理 MessageExceptionCode 类型的异常（通过 key 匹配，因为 resultCode() 返回匿名 ResultCode）
    Optional<MessageExceptionCode> matched = matchMessageExceptionCode(e);
    if (matched.isEmpty()) {
      return null;
    }
    MessageExceptionCode messageCode = matched.get();

    String userMessage = e.getMessage();
    String developerMessage = buildDeveloperMessage(e);
    Integer retryAfter = messageCode.retryAfterSeconds();

    log.error(
        "[MessageHandler] 消息业务异常 | 路径: {} | 错误码: {} | userMessage: {} | developerMessage: {} | retryAfter: {}s",
        request.getRequestURI(),
        messageCode.getCode(),
        userMessage,
        developerMessage,
        retryAfter,
        e);

    // 设置 HTTP 状态码
    response.setStatus(e.getHttpStatus());

    // 可恢复异常添加 Retry-After 响应头，引导客户端合理重试
    if (retryAfter != null && retryAfter > 0) {
      response.setHeader("Retry-After", String.valueOf(retryAfter));
    }

    // 构造分层 MessageResult 响应
    MessageResult result =
        MessageResult.fail(
            messageCode.getCode(), userMessage, developerMessage, retryAfter);

    YdszResponse<MessageResult> responseBody = YdszResponse.success(result);
    responseBody.setMsg(userMessage);
    return responseBody;
  }

  /**
   * 将异常匹配到对应的 {@link MessageExceptionCode}。
   *
   * <p>由于 {@link AbstractYdszException#resultCode()} 返回匿名 {@code ResultCode} 而非原始枚举， 无法使用 {@code instanceof} 判断。
   * 本方法通过比对异常的 {@code key} 与 {@code code} 字段与 {@link MessageExceptionCode} 枚举值进行匹配。
   *
   * @param e 异常对象
   * @return 匹配到的 {@link MessageExceptionCode}；未匹配返回 {@link Optional#empty()}
   */
  private Optional<MessageExceptionCode> matchMessageExceptionCode(AbstractYdszException e) {
    String key = e.getKey();
    String code = e.getCode();
    return Arrays.stream(MessageExceptionCode.values())
        .filter(mc -> mc.getKey().equals(key) || mc.getCode().equals(code))
        .findFirst();
  }

  /**
   * 构造开发者调试信息。
   *
   * <p>格式： {@code SimpleName: message | cause: RootCauseSimpleName: rootCauseMessage}
   *
   * @param e 异常对象
   * @return 开发者调试信息字符串
   */
  private String buildDeveloperMessage(AbstractYdszException e) {
    StringBuilder sb = new StringBuilder();
    sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());

    // 提取根本原因
    Throwable cause = e.getCause();
    if (cause != null) {
      sb.append(" | cause: ")
          .append(cause.getClass().getSimpleName())
          .append(": ")
          .append(cause.getMessage());
    }

    return sb.toString();
  }
}
