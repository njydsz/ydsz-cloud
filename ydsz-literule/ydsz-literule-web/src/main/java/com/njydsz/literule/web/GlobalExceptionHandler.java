package com.njydsz.literule.web;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;

/**
 * 全局异常处理器（规则引擎 Web 层）
 *
 * <p>统一拦截 Controller 层抛出的异常，将各类异常转换为标准的 {@link YdszResponse} 错误响应，
 * 避免直接抛出 {@code 500 Internal Server Error} 暴露内部实现细节。
 *
 * <p><b>处理优先级（由高到低）：</b>
 *
 * <ol>
 *   <li>参数校验异常：{@link MethodArgumentNotValidException}、{@link BindException} — 400
 *   <li>业务参数异常：{@link MissingServletRequestParameterException}、{@link
 *       MissingRequestHeaderException}、{@link HttpMessageNotReadableException}、{@link
 *       MethodArgumentTypeMismatchException} — 400
 *   <li>HTTP 协议异常：{@link HttpRequestMethodNotSupportedException}、{@link
 *       HttpMediaTypeNotSupportedException}、{@link NoHandlerFoundException} — 405/404/415
 *   <li>业务异常兜底：{@link IllegalArgumentException}、{@link IllegalStateException} — 400
 * </ol>
 *
 * <p>所有异常均返回 {@link YdszResultCode#VALIDATION_FAILED} 或 {@link
 * LiteruleExceptionCode#RULE_STATUS_INVALID} 等明确错误码，前端可据此进行友好提示。
 *
 * <p><b>日志级别：</b>
 *
 * <ul>
 *   <li>参数校验异常：WARN（可预期）
 *   <li>协议/路由异常：INFO（外部请求不匹配）
 *   <li>业务参数异常：WARN（客户端数据错误）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

  /**
   * 处理 @RequestBody @Valid 校验失败
   *
   * <p>提取所有字段校验错误，拼接为单条可读消息返回。
   *
   * @param e 校验异常
   * @return 标准错误响应
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
    log.warn("[LiteRule] 参数校验失败: {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理表单绑定校验失败（非 @RequestBody 场景）
   *
   * @param e 绑定异常
   * @return 标准错误响应
   */
  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleBindException(BindException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.joining("; "));
    log.warn("[LiteRule] 参数绑定失败: {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理缺少必填请求参数（@RequestParam）
   *
   * @param e 缺少参数异常
   * @return 标准错误响应
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
    String message = "缺少必填参数: " + e.getParameterName() + " (" + e.getParameterType() + ")";
    log.warn("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理缺少必填请求头（@RequestHeader）
   *
   * @param e 缺少请求头异常
   * @return 标准错误响应
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleMissingHeader(MissingRequestHeaderException e) {
    String message = "缺少必填请求头: " + e.getHeaderName();
    log.warn("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理请求体 JSON 解析失败
   *
   * @param e HTTP 消息不可读异常
   * @return 标准错误响应
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
    String message = "请求体格式错误: " + e.getMessage();
    log.warn("[LiteRule] 请求体解析失败: {}", e.getMessage());
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理参数类型不匹配（如传入字符串到整型参数）
   *
   * @param e 参数类型不匹配异常
   * @return 标准错误响应
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
    String message =
        String.format("参数[%s]类型不匹配，期望=%s，实际值=%s",
            e.getName(), e.getRequiredType(), e.getValue());
    log.warn("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理非法方法请求（如 POST 接口收到 GET 请求）
   *
   * @param e HTTP 请求方法不支持异常
   * @return 标准错误响应
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public YdszResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    String message = "不支持的请求方法: " + e.getMethod();
    log.info("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理不支持的 Content-Type
   *
   * @param e HTTP 媒体类型不支持异常
   * @return 标准错误响应
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
  public YdszResponse<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
    String message = "不支持的 Content-Type: " + e.getContentType();
    log.info("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理 404 未匹配到路由
   *
   * @param e 无处理器异常
   * @return 标准错误响应
   */
  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public YdszResponse<Void> handleNoHandlerFound(NoHandlerFoundException e) {
    String message = "接口不存在: " + e.getHttpMethod() + " " + e.getRequestURL();
    log.info("[LiteRule] {}", message);
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, message);
  }

  /**
   * 处理 IllegalArgumentException（如非法参数值）
   *
   * <p>兜底处理业务代码中直接抛出的 IllegalArgumentException，返回 400 而非 500。
   *
   * @param e 非法参数异常
   * @return 标准错误响应
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("[LiteRule] 非法参数: {}", e.getMessage());
    return YdszResponse.error(YdszResultCode.VALIDATION_FAILED, e.getMessage());
  }

  /**
   * 处理 IllegalStateException（如非法状态）
   *
   * @param e 非法状态异常
   * @return 标准错误响应
   */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleIllegalState(IllegalStateException e) {
    log.warn("[LiteRule] 非法状态: {}", e.getMessage());
    return YdszResponse.error(LiteruleExceptionCode.RULE_STATUS_INVALID, e.getMessage());
  }

  /**
   * 格式化字段错误为可读消息
   *
   * @param fe 字段错误
   * @return 格式化后消息
   */
  private String formatFieldError(FieldError fe) {
    return fe.getField() + ": " + fe.getDefaultMessage() + " (实际值: " + fe.getRejectedValue() + ")";
  }
}

