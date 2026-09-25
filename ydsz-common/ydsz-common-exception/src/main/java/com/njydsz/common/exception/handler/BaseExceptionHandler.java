package com.njydsz.common.exception.handler;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.event.ExceptionHandledEvent;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.util.ExceptionDesensitizer;

/**
 * 异常处理器抽象基类（26.09.19 重构）。
 *
 * <p>职责定位 — 编排与状态管理：
 *
 * <ul>
 *   <li>环境/配置/指标/事件源状态维护</li>
 *   <li>{@link ExceptionHandledEvent} 发布编排</li>
 *   <li>国际化消息解析</li>
 *   <li>子类模板方法（{@link #getLogPrefix()}）</li>
 * </ul>
 *
 * <p>响应构建（ProblemDetail / YdszResponse / ExceptionInfo）已拆分委托给内部静态类 {@link
 * ExceptionResponseBuilder}。本类的 protected 方法保持原有签名，子类（Mvc / WebFlux / Validation）无需任何修改。
 *
 * <p><b>为什么拆分：</b>原始单文件超过 750 行，混合了响应构建、指标记录、事件发布、i18n 解析四类职责，
 * 导致单元测试需 mock 整个基类。拆分后 {@link ExceptionResponseBuilder} 可独立测试响应结构。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.exception.custom.BusinessException
 * @see YdszResponse
 * @see ProblemDetail
 */
@Slf4j
public abstract class BaseExceptionHandler {

  /** Spring 环境（profile 与属性读取） */
  private final Environment environment;

  /** 应用事件发布器（可选） */
  private ApplicationEventPublisher eventPublisher;

  /** 国际化消息源（由子类注入） */
  private MessageSource messageSource;

  /** 异常模块配置属性 */
  private ExceptionProperties properties;

  /** 异常指标统计器 */
  private ExceptionMetrics exceptionMetrics;

  /**
   * 响应构建器委托。
   *
   * <p>承载 ProblemDetail / YdszResponse / ExceptionInfo 构造逻辑，使本基类聚焦于编排与状态管理。
   */
  private ExceptionResponseBuilder responseBuilder;

  /**
   * 构造基类异常处理器
   *
   * @param environment Spring 环境对象
   */
  protected BaseExceptionHandler(Environment environment) {
    this.environment = environment;
    this.responseBuilder = new ExceptionResponseBuilder(environment, null, null);
  }

  // ==================== Setter 装配（由 AutoConfiguration 注入） ====================

  /**
   * 设置事件发布器（可选）
   *
   * @param publisher 事件发布器
   */
  protected void setEventPublisher(ApplicationEventPublisher publisher) {
    this.eventPublisher = publisher;
  }

  /**
   * 设置异常模块配置属性
   *
   * @param env Spring 环境对象
   * @param properties 异常模块配置属性
   */
  protected void setExceptionProperties(Environment env, ExceptionProperties properties) {
    this.properties = properties;
    this.responseBuilder = new ExceptionResponseBuilder(environment, properties, exceptionMetrics);
  }

  /**
   * 设置异常指标统计器
   *
   * @param env Spring 环境对象
   * @param exceptionMetrics 异常指标统计器
   */
  protected void setExceptionMetrics(Environment env, ExceptionMetrics exceptionMetrics) {
    this.exceptionMetrics = exceptionMetrics;
    this.responseBuilder = new ExceptionResponseBuilder(environment, properties, exceptionMetrics);
  }

  /**
   * 设置国际化消息源
   *
   * @param messageSource Spring MessageSource
   */
  protected void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  // ==================== 子类模板方法 ====================

  /**
   * 获取日志前缀，由子类实现以定制不同端的日志前缀
   *
   * @return 日志前缀字符串
   */
  protected abstract String getLogPrefix();

  // ==================== 状态查询（子类可复用） ====================

  /**
   * 获取异常指标统计器
   *
   * @return 异常指标统计器；未注入时返回 null
   */
  protected ExceptionMetrics getExceptionMetrics() {
    return exceptionMetrics;
  }

  /**
   * 获取当前激活的 profile 名称
   *
   * @return 当前激活的 profile；无 profile 时返回 null
   */
  protected String[] getActiveProfiles() {
    return environment != null ? environment.getActiveProfiles() : new String[0];
  }

  /**
   * 判断是否为开发/测试环境
   *
   * @return 如果当前 profile 为 dev/test 返回 true，否则 false
   */
  protected boolean isDevOrTestProfile() {
    if (environment == null) {
      return false;
    }
    for (String profile : environment.getActiveProfiles()) {
      if ("dev".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 是否需要包含 ExceptionInfo 详细信息（开发/测试环境或配置强制开启）
   *
   * @return true-包含详细信息，false-不包含
   */
  protected boolean includeExceptionInfo() {
    boolean configFlag = properties != null && properties.isIncludeStackTrace();
    return configFlag || isDevOrTestProfile();
  }

  /**
   * 是否使用 ProblemDetail (RFC 7807) 响应格式
   *
   * @return true-使用 ProblemDetail 格式，false-使用 YdszResponse 格式
   */
  protected boolean useProblemDetail() {
    return responseBuilder.useProblemDetail();
  }

  /**
   * 获取 ProblemDetail type URI 基础 URL
   *
   * @return 配置的基础 URL，未配置时返回 "about:blank"
   */
  protected String getProblemDetailTypeBaseUrl() {
    return responseBuilder.getProblemDetailTypeBaseUrl();
  }

  /**
   * 是否在 Micrometer 指标中包含异常 code tag
   *
   * @return true-包含高基数 code tag，false-不包含
   */
  protected boolean metricsIncludeCodeTag() {
    return properties != null && properties.isMetricsIncludeCodeTag();
  }

  // ==================== 静态工具方法（供子类直接调用，零依赖） ====================

  /**
   * 获取根本原因的消息
   *
   * @param throwable 异常对象
   * @return 处理结果
   */
  protected static String getRootCauseMessage(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable root = throwable;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage();
  }

  /**
   * 获取脱敏后的堆栈跟踪字符串
   *
   * <p>委托 {@link ExceptionDesensitizer#desensitizeStackTrace(Throwable)} 实现。
   *
   * @param throwable 异常对象
   * @return 脱敏后的堆栈字符串
   */
  protected static String getStackTraceString(Throwable throwable) {
    return ExceptionDesensitizer.desensitizeStackTrace(throwable);
  }

  /**
   * 从 Servlet 请求上下文提取 traceId
   *
   * <p>优先级：RequestContext > MDC > Request Header（X-Trace-Id > X-Request-Id）。
   *
   * @param request Servlet 请求，可为 null
   * @return traceId，未提取到时返回 null
   */
  protected static String extractTraceId(HttpServletRequest request) {
    String traceId = RequestContext.getTraceId();
    if (traceId == null || traceId.isBlank()) {
      traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }
    if ((traceId == null || traceId.isBlank()) && request != null) {
      traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
      if (traceId == null) {
        traceId = request.getHeader(HeaderConstants.X_REQUEST_ID);
      }
    }
    return traceId;
  }

  /**
   * 从 WebFlux 请求上下文提取 traceId
   *
   * @param exchange WebFlux 请求上下文，可为 null
   * @return traceId，未提取到时返回 null
   */
  protected static String extractTraceId(ServerWebExchange exchange) {
    String traceId = RequestContext.getTraceId();
    if (traceId == null || traceId.isBlank()) {
      traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }
    if ((traceId == null || traceId.isBlank()) && exchange != null) {
      traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACE_ID_HEADER);
      if (traceId == null) {
        traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.X_REQUEST_ID);
      }
    }
    return traceId;
  }

  /**
   * 判断是否为生产环境
   *
   * @return true-生产环境，false-非生产环境
   */
  protected boolean isProductionEnvironment() {
    if (environment == null) {
      return false;
    }
    for (String profile : environment.getActiveProfiles()) {
      if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
        return true;
      }
    }
    return false;
  }

  // ==================== 指标 / 事件 ====================

  /**
   * 记录异常指标（统一入口）
   *
   * @param throwable 异常对象
   */
  protected void recordMetrics(Throwable throwable) {
    if (exceptionMetrics != null) {
      exceptionMetrics.recordException(throwable);
    }
  }

  /**
   * 发布异常处理完成事件
   *
   * @param throwable 已处理的异常
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @param resolvedMsg 已解析的异常消息
   */
  protected void publishExceptionEvent(
      Throwable throwable, String path, String traceId, String resolvedMsg) {
    if (eventPublisher == null) {
      return;
    }
    try {
      String code = CoreExceptionCode.INTERNAL_ERROR.getCode();
      String key = null;
      int httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
      ExceptionCategory category = ExceptionCategory.SYSTEM;
      String levelName = ExceptionLevel.ERROR.name();

      if (throwable instanceof AbstractYdszException ex) {
        code = ex.getCode();
        key = ex.getKey();
        httpStatus = ex.getHttpStatus();
        if (ex.getCategory() != null) {
          category = ex.getCategory();
        }
        if (ex.getLevel() != null) {
          levelName = ex.getLevel().name();
        }
      }

      ExceptionHandledEvent event =
          new ExceptionHandledEvent(
              this,
              code,
              key,
              resolvedMsg,
              httpStatus,
              path,
              traceId,
              category,
              levelName,
              throwable.getClass().getSimpleName());
      eventPublisher.publishEvent(event);
    } catch (Exception e) {
      log.debug("发布异常处理事件失败: {}", e.getMessage());
    }
  }

  // ==================== 响应构建委托（子类调用 → 内部转发给 ExceptionResponseBuilder） ====================

  /**
   * 构建异常信息对象
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 异常信息对象
   */
  protected ExceptionInfo buildExceptionInfo(Throwable throwable, String path, String traceId) {
    return responseBuilder.buildExceptionInfo(throwable, path, traceId, includeExceptionInfo());
  }

  /**
   * 构建 RFC 7807 ProblemDetail 对象
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return ProblemDetail 对象
   */
  protected ProblemDetail buildProblemDetail(Throwable throwable, String path, String traceId) {
    return responseBuilder.buildProblemDetail(throwable, path, traceId);
  }

  /**
   * 构建统一错误响应（YdszResponse 格式）
   *
   * @param code 错误码
   * @param msg 错误消息
   * @param data 附加数据
   * @param level 异常级别
   * @return 统一错误响应
   */
  protected static <T> YdszResponse<T> errorResponse(
      String code, String msg, T data, ExceptionLevel level) {
    return ExceptionResponseBuilder.buildErrorResponse(code, msg, data, level);
  }

  /**
   * 构建统一错误响应（默认 ERROR 级别）
   *
   * @param code 错误码
   * @param msg 错误消息
   * @param data 附加数据
   * @return 统一错误响应
   */
  protected static <T> YdszResponse<T> errorResponse(String code, String msg, T data) {
    return errorResponse(code, msg, data, ExceptionLevel.ERROR);
  }

  /**
   * 构建统一异常响应（根据配置自动选择格式并记录耗时）
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 响应对象
   */
  protected Object buildResponse(Throwable throwable, String path, String traceId) {
    return responseBuilder.buildResponse(throwable, path, traceId);
  }

  /**
   * 构建 ResponseEntity（动态 HTTP 状态码）
   *
   * @param body 响应体
   * @param throwable 异常对象
   * @return ResponseEntity
   */
  protected ResponseEntity<Object> buildResponseEntity(Object body, Throwable throwable) {
    return responseBuilder.buildResponseEntity(body, throwable);
  }

  /**
   * 构建标准错误响应（含 ExceptionInfo 详情，受 includeExceptionInfo 开关控制）
   *
   * @param code 错误码
   * @param key i18n 消息键
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @param level 异常级别
   * @return 统一 YdszResponse
   */
  protected YdszResponse<?> buildStandardErrorResponse(
      String code, String key, String message, int httpStatus, String path, ExceptionLevel level) {
    return responseBuilder.buildStandardErrorResponse(code, key, message, httpStatus, path, level);
  }

  /**
   * 构建标准错误响应（默认 ERROR 级别）
   *
   * @param code 错误码
   * @param key i18n 消息键
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @return 统一 YdszResponse
   */
  protected YdszResponse<?> buildStandardErrorResponse(
      String code, String key, String message, int httpStatus, String path) {
    return buildStandardErrorResponse(code, key, message, httpStatus, path, ExceptionLevel.ERROR);
  }

  /**
   * 构建带详细信息的统一错误响应（强制包含 ExceptionInfo）
   *
   * @param code 错误码
   * @param key i18n 消息键
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @return 统一 YdszResponse（始终包含 ExceptionInfo）
   */
  protected YdszResponse<?> buildWithInfo(
      String code, String key, String message, int httpStatus, String path) {
    return responseBuilder.buildWithInfo(code, key, message, httpStatus, path);
  }

  /**
   * 构建校验错误响应
   *
   * @param errorCode 异常码
   * @param message 异常消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @param throwable 原始异常
   * @return 统一响应格式
   */
  protected YdszResponse<?> buildValidationErrorResponse(
      CoreExceptionCode errorCode,
      String message,
      int httpStatus,
      String path,
      Throwable throwable) {
    // 按 ExceptionLevel 分流日志：校验异常默认 ERROR，但可通过 resolveDefaultLevel 覆盖
    // SecurityException/AuthException 未来映射 FATAL，此处使用 ERROR 作为校验异常默认值
    logByLevel(ExceptionLevel.ERROR, "{}校验异常 | 路径: {} | 消息: {}", getLogPrefix(), path, message, throwable);
    recordMetrics(throwable);
    return responseBuilder.buildValidationErrorResponse(
        errorCode, message, httpStatus, path, throwable);
  }

  // ==================== 异常级别自动映射 / 级别感知日志 ====================
  // TODO 26.09.25 日志级别按 ExceptionLevel 分流规划：
  //   BusinessException → WARN（子类 Mvc/WebFlux handler 已实现 log.warn）
  //   SysException / LlmException → ERROR（子类已实现 log.error）
  //   SecurityException / AuthException → FATAL（最高级，事件订阅者应触发即时告警）
  //   本工具方法 logByLevel / resolveDefaultLevel 为基类提供统一入口，供子类或未来扩展使用

  /**
   * 根据异常类型自动解析对应的 ExceptionLevel（用于日志分流和事件元数据）。
   *
   * <p>映射规则：
   *
   * <ul>
   *   <li>{@link com.njydsz.common.exception.custom.BusinessException} → {@link ExceptionLevel#WARN}
   *   <li>{@link com.njydsz.common.exception.custom.SysException} → {@link ExceptionLevel#ERROR}
   *   <li>SecException / AuthException（待引入） → {@link ExceptionLevel#FATAL}
   *   <li>其他未知异常 → {@link ExceptionLevel#ERROR}（默认）
   * </ul>
   *
   * <p>对于实现了 {@link com.njydsz.common.exception.custom.AbstractYdszException} 的异常，
   * 优先使用异常自身携带的 level（由构造时设置），未提供时再按类型默认推断。
   *
   * @param throwable 异常对象
   * @return 对应的 ExceptionLevel，永不为 {@code null}
   */
  protected ExceptionLevel resolveDefaultLevel(Throwable throwable) {
    if (throwable instanceof AbstractYdszException ex && ex.getLevel() != null) {
      return ex.getLevel();
    }
    if (throwable instanceof BusinessException) {
      return ExceptionLevel.WARN;
    }
    if (throwable instanceof SysException) {
      return ExceptionLevel.ERROR;
    }
    // TODO: 待 SecurityException / AuthException / LlmException 引入后增加对应分支
    // if (throwable instanceof SecurityException || throwable instanceof AuthException) {
    //   return ExceptionLevel.FATAL;
    // }
    // if (throwable instanceof LlmException) {
    //   return ExceptionLevel.ERROR;
    // }
    return ExceptionLevel.ERROR;
  }

  /**
   * 按 ExceptionLevel 分流日志输出级别。
   *
   * <p>替代直接调用 {@code log.error()} / {@code log.warn()}，根据级别自动选择 SLF4J 日志方法，
   * 确保日志级别与 {@link ExceptionLevel} 元数据一致（便于日志告警规则按级别过滤）。
   *
   * <p><b>级别映射：</b>
   *
   * <ul>
   *   <li>{@link ExceptionLevel#INFO} → {@code log.info()}
   *   <li>{@link ExceptionLevel#WARN} → {@code log.warn()}
   *   <li>{@link ExceptionLevel#ERROR} / {@link ExceptionLevel#FATAL} → {@code log.error()}
   * </ul>
   *
   * <p><b>注意：</b>如果可变参数最后一个参数为 {@link Throwable}，SLF4J 会自动将其作为异常对象输出堆栈。
   *
   * @param level 异常级别
   * @param format SLF4J 格式化字符串
   * @param args 格式化参数（最后一位可传 Throwable 以输出堆栈）
   */
  protected void logByLevel(ExceptionLevel level, String format, Object... args) {
    if (level == null) {
      level = ExceptionLevel.ERROR;
    }
    switch (level) {
      case INFO -> log.info(format, args);
      case WARN -> log.warn(format, args);
      case ERROR, FATAL -> log.error(format, args);
    }
  }

  // ==================== 可恢复性 / 国际化 ====================

  /**
   * 对可恢复异常添加 {@code Retry-After} 响应头
   *
   * @param response HTTP 响应对象
   * @param throwable 异常对象
   */
  protected void addRetryAfterHeader(HttpServletResponse response, Throwable throwable) {
    if (response == null || !(throwable instanceof AbstractYdszException ex)) {
      return;
    }
    if (!(ex.resultCode() instanceof ExceptionCode exceptionCode)) {
      return;
    }
    if (!exceptionCode.retryable()) {
      return;
    }
    int seconds = exceptionCode.retryAfterSeconds();
    if (seconds > 0) {
      response.setHeader("Retry-After", String.valueOf(seconds));
    }
  }

  /**
   * 按请求 Locale 解析国际化消息
   *
   * @param key i18n 消息键
   * @param args 消息参数
   * @param defaultMsg 兜底文案
   * @return 解析后的消息文本
   */
  protected String resolveMessage(String key, Object[] args, String defaultMsg) {
    if (messageSource == null) {
      return defaultMsg;
    }
    try {
      return messageSource.getMessage(key, args, defaultMsg, LocaleContextHolder.getLocale());
    } catch (Exception e) {
      return defaultMsg;
    }
  }

  /**
   * 为异常生成聚合指纹（26.09.19 新增）
   *
   * @param throwable 异常对象
   * @return 16 位十六进制指纹字符串
   */
  protected static String generateFingerprint(Throwable throwable) {
    return ExceptionResponseBuilder.generateFingerprint(throwable);
  }
}
