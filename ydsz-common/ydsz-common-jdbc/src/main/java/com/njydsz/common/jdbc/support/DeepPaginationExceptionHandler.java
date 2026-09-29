package com.njydsz.common.jdbc.support;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.domain.query.DeepPaginationException;

/**
 * 深度分页拒绝异常处理器（YDIZ-DOMAIN-003 P2 落地，i18n 增强）。
 *
 * <p>处理 {@link SafeQueryInnerInterceptor} 抛出的 {@link DeepPaginationException}。
 * 由于 {@code ydsz-common-exception} 不依赖 {@code ydzs-common-domain}（层级隔离），
 * 此 handler 定义在 {@code ydsz-common-jdbc} 模块中（jdbc 依赖 domain）。
 *
 * <p>返回 HTTP 400 BAD_REQUEST，消息体包含从 i18n MessageSource 解析的本地化文案，
 * 以及 offset/threshold/pageNum/pageSize 等上下文参数辅助调试。
 *
 * <p><b>i18n 消息键</b>：{@code domain.deep.pagination.rejected}
 * <br><b>参数</b>：{0}=offset, {1}=threshold, {2}=pageNum, {3}=pageSize
 *
 * @author ydsz-team
 * @since 26.09.30
 * @see DeepPaginationException
 * @see com.njydsz.common.exception.handler.MvcExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DeepPaginationExceptionHandler {

  private final MessageSource messageSource;

  public DeepPaginationExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * 处理深度分页被拒绝异常。
   *
   * <p>当 offset 超过 {@code ydsz.domain.page.cursor-reject-threshold} 阈值时被 {@link
   * com.njydsz.common.jdbc.interceptor.SafeQueryInnerInterceptor} 抛出。
   *
   * @param e 深度分页拒绝异常
   * @param request HTTP 请求
   * @return 含 i18n 文案的标准化错误响应（HTTP 400）
   */
  @ExceptionHandler(DeepPaginationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<Void> handleDeepPaginationException(
      DeepPaginationException e, HttpServletRequest request) {
    log.warn(
        "深度分页被拒绝 | 路径: {} | offset={} threshold={} pageNum={} pageSize={}",
        request.getRequestURI(),
        e.getOffset(),
        e.getThreshold(),
        e.getPageNum(),
        e.getPageSize(),
        e);

    // 按请求 Locale 解析 i18n 文案
    String message = resolveI18nMessage(e);

    return YdszResponse.error("DEEP_PAGINATION_REJECTED", message);
  }

  /**
   * 按请求 Locale 解析深度分页拒绝异常的 i18n 文案。
   *
   * <p>从 Spring MessageSource 查找 {@code domain.deep.pagination.rejected} 键，
   * 使用异常内置的 getMessagesParams() 作为参数数组（[offset, threshold, pageNum, pageSize]）。
   * 找不到时回退到异常自带的 getMessage()（英文兜底）。
   *
   * @param e 深度分页拒绝异常
   * @return 解析后的本地化消息文本
   */
  private String resolveI18nMessage(DeepPaginationException e) {
    if (messageSource == null) {
      return e.getMessage();
    }
    try {
      Locale locale = LocaleContextHolder.getLocale();
      return messageSource.getMessage(
          DeepPaginationException.MESSAGE_KEY, e.getMessageParams(), e.getMessage(), locale);
    } catch (Exception ex) {
      return e.getMessage();
    }
  }
}
