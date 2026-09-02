package com.njydsz.common.web.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.excel.exception.ExcelException;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.exception.ExcelWriteException;

/**
 * Excel 模块异常处理器
 *
 * <p>在 Web 层桥接 Excel 自包含异常体系到全局统一响应格式。 设置 {@code @Order(Ordered.HIGHEST_PRECEDENCE + 10)} 确保在
 * {@link com.njydsz.common.exception.handler.MvcExceptionHandler} 之前拦截， 避免被通用 {@code
 * Exception.class} 处理器兜底。
 *
 * <p><b>设计意图：</b>
 *
 * <ul>
 *   <li>Excel 模块保持零反向依赖，不感知全局异常体系
 *   <li>Web 层作为上层模块，同时依赖 excel 和 exception，负责桥接
 *   <li>如需 Excel 异常注册到全局错误码表，可在 web 层通过适配器实现
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
  // CHECKSTYLE.ON: RegexpSinglelineJava
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
public class ExcelMvcExceptionHandler {

  /**
   * 处理 Excel 读取异常
   *
   * @param e Excel 读取异常
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 统一错误响应
   */
  @ExceptionHandler(ExcelReadException.class)
  public YdszResponse<?> handleExcelReadException(
      ExcelReadException e, HttpServletRequest request, HttpServletResponse response) {
    return handleExcelException(e, request, response, "读取");
  }

  /**
   * 处理 Excel 写入异常
   *
   * @param e Excel 写入异常
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 统一错误响应
   */
  @ExceptionHandler(ExcelWriteException.class)
  public YdszResponse<?> handleExcelWriteException(
      ExcelWriteException e, HttpServletRequest request, HttpServletResponse response) {
    return handleExcelException(e, request, response, "写入");
  }

  /**
   * 处理 Excel 基础异常（兜底）
   *
   * @param e Excel 基础异常
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 统一错误响应
   */
  @ExceptionHandler(ExcelException.class)
  public YdszResponse<?> handleExcelException(
      ExcelException e, HttpServletRequest request, HttpServletResponse response) {
    return handleExcelException(e, request, response, "处理");
  }

  /**
   * 统一处理 Excel 异常
   *
   * @param e Excel 异常
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @param operation 操作描述（读取/写入/处理）
   * @return 统一错误响应
   */
  private YdszResponse<?> handleExcelException(
      ExcelException e,
      HttpServletRequest request,
      HttpServletResponse response,
      String operation) {

    String code = e.getCode();
    String message = e.getMessage();
    int httpStatus = e.getHttpStatus();

    // 设置 HTTP 状态码
    if (response != null) {
      response.setStatus(httpStatus);
    }

    // 日志记录
    if (httpStatus >= 500) {
      log.error(
          "Excel{}异常 | 路径: {} | 错误码: {} | 消息: {}",
          operation,
          request.getRequestURI(),
          code,
          message,
          e);
    } else {
      log.warn(
          "Excel{}异常 | 路径: {} | 错误码: {} | 消息: {}",
          operation,
          request.getRequestURI(),
          code,
          message);
    }

    return YdszResponse.builder()
        .code(code)
        .msg(message)
        .timestamp(System.currentTimeMillis())
        .build();
  }
}
