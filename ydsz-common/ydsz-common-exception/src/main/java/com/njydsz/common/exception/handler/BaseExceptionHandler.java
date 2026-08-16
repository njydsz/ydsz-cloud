package com.njydsz.common.exception.handler;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.event.ExceptionHandledEvent;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.trace.OtelTraceInfo;
import com.njydsz.common.exception.trace.OtelTraceInfoExtractor;
import com.njydsz.common.exception.util.ExceptionDesensitizer;

/**
 * 异常处理器抽象基类
 *
 * <p>提供通用的异常处理逻辑，子类只需实现特定的日志前缀和响应格式定制。 支持国际化消息、异常链追踪、差异化环境处理（开发/生产）、 RFC 7807 ProblemDetail
 * 输出格式切换、统一指标记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BusinessException
 * @see BaseResponse
 * @see ProblemDetail
 */
@Slf4j
public abstract class BaseExceptionHandler {

  private final Environment environment;
  private ApplicationEventPublisher eventPublisher;

  /** 国际化消息源（由子类注入后通过 {@link #setMessageSource(MessageSource)} 设置） */
  private MessageSource messageSource;

  /**
   * 构造基类异常处理器（通过 Spring 注入 {@link Environment}）
   *
   * @param environment Spring 环境对象
   */
  protected BaseExceptionHandler(Environment environment) {
    this.environment = environment;
  }

  /** 异常模块配置属性 */
  private ExceptionProperties properties;

  private ExceptionMetrics exceptionMetrics;

  /**
   * 事件发布器（可选，由子类通过构造器注入）
   *
   * <p>当事件发布器可用时，异常处理完成后自动发布 {@link ExceptionHandledEvent}， 下游订阅者可用于告警通知、Sentry 上报等场景。
   *
   * @param publisher 事件发布器
   */
  protected void setEventPublisher(ApplicationEventPublisher publisher) {
    this.eventPublisher = publisher;
  }

  /**
   * 获取日志前缀，由子类实现以定制不同端的日志前缀
   *
   * @return 日志前缀字符串
   */
  protected abstract String getLogPrefix();

  /**
   * 设置异常模块配置属性（由 AutoConfiguration 注入）
   *
   * @param env Spring 环境对象
   * @param properties 异常模块配置属性
   */
  protected void setExceptionProperties(Environment env, ExceptionProperties properties) {
    this.properties = properties;
  }

  /**
   * 设置异常指标统计器（由 AutoConfiguration 注入）
   *
   * @param env Spring 环境对象
   * @param exceptionMetrics 异常指标统计器
   */
  protected void setExceptionMetrics(Environment env, ExceptionMetrics exceptionMetrics) {
    this.exceptionMetrics = exceptionMetrics;
  }

  /**
   * 设置国际化消息源（由子类注入后调用）
   *
   * @param messageSource Spring MessageSource
   */
  protected void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * 获取异常指标统计器
   *
   * @return 异常指标统计器；未注入时返回 null
   */
  protected ExceptionMetrics getExceptionMetrics() {
    return exceptionMetrics;
  }

  /**
   * 记录异常指标（统一入口，所有 handler 调用此方法）
   *
   * <p>如果异常指标统计器未注入或被禁用，此方法为空操作。
   *
   * @param throwable 异常对象
   */
  protected void recordMetrics(Throwable throwable) {
    if (exceptionMetrics != null) {
      exceptionMetrics.recordException(throwable);
    }
  }

  /**
   * 发布异常处理完成事件。
   *
   * <p>当 {@link ApplicationEventPublisher} 可用时，向 Spring 应用上下文发布 {@link
   * ExceptionHandledEvent}，下游订阅者可据此实现告警、审计、APM 上报等扩展。
   *
   * <p>事件包含：错误码、key、消息、HTTP 状态码、路径、traceId、类别、级别、异常类型。
   *
   * @param throwable 已处理的异常
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @param resolvedMsg 已解析的异常消息（i18n 文案或原始消息）
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
      // 事件发布失败不应影响主异常处理流程
      log.debug("发布异常处理事件失败: {}", e.getMessage());
    }
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
   * 是否需要包含 ExceptionInfo 详细信息
   *
   * <p>开发/测试环境返回 true，生产环境返回 false。 也可通过 {@code ydsz.exception.include-stack-trace} 配置强制开启。
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
   * @return true-使用 ProblemDetail 格式，false-使用 BaseResponse 格式
   */
  protected boolean useProblemDetail() {
    if (properties != null) {
      return properties.getResponseFormat() == ExceptionProperties.ResponseFormat.PROBLEM_DETAIL;
    }
    return false;
  }

  /**
   * 获取 ProblemDetail type URI 基础 URL
   *
   * @return 配置的基础 URL，未配置时返回 "about:blank"
   */
  protected String getProblemDetailTypeBaseUrl() {
    if (properties != null && properties.getProblemDetailTypeBaseUrl() != null) {
      return properties.getProblemDetailTypeBaseUrl();
    }
    return "about:blank";
  }

  /**
   * 是否在 Micrometer 指标中包含异常 code tag
   *
   * @return true-包含高基数 code tag，false-不包含
   */
  protected boolean metricsIncludeCodeTag() {
    return properties != null && properties.isMetricsIncludeCodeTag();
  }

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
   * <p>委托 {@link ExceptionDesensitizer#desensitizeStackTrace(Throwable)} 实现，
   * 对外输出前统一完成敏感信息（密码/Token/身份证/手机号/JDBC 连接串）脱敏， 避免敏感数据泄露到响应详情与日志。
   *
   * @param throwable 异常对象
   * @return 处理结果
   */
  protected static String getStackTraceString(Throwable throwable) {
    return ExceptionDesensitizer.desensitizeStackTrace(throwable);
  }

  /**
   * 从 Servlet 请求上下文提取 traceId（统一入口）。
   *
   * <p>优先级：RequestContext > MDC > Request Header（X-Trace-Id > X-Request-Id）。 MVC / Validation /
   * JDBC 处理器复用，消除重复实现。
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
   * 从 WebFlux 请求上下文提取 traceId（统一入口）。
   *
   * <p>优先级：RequestContext > MDC > Request Header（X-Trace-Id > X-Request-Id）。
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
   * @return true-生产环境，false-非生产环境；无法判断时返回 false
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

  /**
   * 构建异常信息对象
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID（可为 null）
   * @return 异常信息对象
   */
  protected ExceptionInfo buildExceptionInfo(Throwable throwable, String path, String traceId) {
    ExceptionInfo info = new ExceptionInfo();
    info.setPath(path);
    if (traceId != null) {
      info.setTraceId(traceId);
    }
    info.setTimestamp(LocalDateTime.now());

    if (throwable instanceof AbstractYdszException) {
      AbstractYdszException ex = (AbstractYdszException) throwable;
      info.setCode(ex.getCode());
      info.setKey(ex.getKey());
      info.setMessage(ex.getMessage());
      info.setHttpStatus(ex.getHttpStatus());
      if (includeExceptionInfo()) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stackTrace", getStackTraceString(throwable));
        if (ex.getExtData() != null) {
          ex.getExtData().forEach((k, v) -> details.put(k, v));
        }
        info.setDetails(details);
      }
    } else {
      info.setCode(CoreExceptionCode.INTERNAL_ERROR.getCode());
      info.setMessage(getRootCauseMessage(throwable));
      info.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      if (includeExceptionInfo()) {
        info.setDetails(Map.of("stackTrace", getStackTraceString(throwable)));
      }
    }

    return info;
  }

  /**
   * 构建 RFC 7807 ProblemDetail 对象（基于 Spring 标准实现）。
   *
   * <p>ydsz-common-core 精简后不再提供自定义 ProblemDetail， 改用 Spring {@link
   * org.springframework.http.ProblemDetail}， traceId / requestId / errorCode 等扩展字段通过 {@code
   * setProperty} 输出。
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID（可为 null）
   * @return ProblemDetail 对象
   */
  protected ProblemDetail buildProblemDetail(Throwable throwable, String path, String traceId) {
    String baseUrl = getProblemDetailTypeBaseUrl();
    ProblemDetail problem;

    if (throwable instanceof AbstractYdszException) {
      AbstractYdszException ex = (AbstractYdszException) throwable;
      problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatusCode.valueOf(ex.getHttpStatus()), ex.getMessage());
      problem.setTitle(ex.getClass().getSimpleName());
      problem.setType(URI.create(baseUrl + "/" + ex.getCategory().name().toLowerCase()));
      problem.setProperty("errorCode", ex.getCode());
      if (path != null) {
        problem.setInstance(URI.create(path));
      }
      if (ex.getExtData() != null) {
        ex.getExtData().forEach((k, v) -> problem.setProperty(k, v));
      }
    } else {
      problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatusCode.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
              getRootCauseMessage(throwable));
      problem.setTitle("System Error");
      problem.setType(URI.create(baseUrl + "/system"));
      problem.setProperty("errorCode", CoreExceptionCode.INTERNAL_ERROR.getCode());
      if (path != null) {
        problem.setInstance(URI.create(path));
      }
    }

    problem.setProperty("traceId", traceId);
    problem.setProperty("requestId", traceId);
    problem.setProperty("timestamp", Instant.now().toString());

    // 自动注入 OpenTelemetry traceId/spanId（当 OTel 可用时）
    injectOtelTraceContext(problem);

    return problem;
  }

  /**
   * 注入 OpenTelemetry 链路追踪上下文到 ProblemDetail。
   *
   * <p>当 classpath 中存在 OpenTelemetry API 时，自动将 traceId、spanId 注入到 ProblemDetail 的扩展属性中，便于与
   * APM（Grafana Tempo、Jaeger 等）关联。 当 OTel 未接入时本方法为静默空操作，对主流程零侵入。
   *
   * @param problem 待注入的 ProblemDetail
   */
  private void injectOtelTraceContext(ProblemDetail problem) {
    try {
      OtelTraceInfo otelTrace = OtelTraceInfoExtractor.currentTraceInfo();
      if (otelTrace.isValid()) {
        problem.setProperty("otelTraceId", otelTrace.traceId());
        problem.setProperty("otelSpanId", otelTrace.spanId());
        problem.setProperty("otelSampled", otelTrace.sampled());
        // 同时更新 traceId（OTel 的 traceId 与 header 中的一致，优先使用）
        problem.setProperty("traceId", otelTrace.traceId());
        problem.setProperty("requestId", otelTrace.traceId());
      }
    } catch (Exception e) {
      // OTel 反射调用异常时降级（不影响主流程）
      log.debug("[BaseExceptionHandler] OTel TraceContext 注入失败: {}", e.getMessage());
    }
  }

  /**
   * 构建统一错误响应（{@link BaseResponse} 格式，兼容 {@code BaseResponse.error(code, msg, data)} 旧语义）。
   *
   * <p>ydsz-common-core 精简后移除了三参数 {@code error} 静态方法， 此处统一通过 {@link BaseResponse#builder()} 构建，保持各
   * handler 输出结构一致。
   *
   * @param code 错误码
   * @param msg 错误消息
   * @param data 附加数据（可为 null，由 {@code @JsonInclude(NON_NULL)} 决定是否序列化）
   * @return 统一错误响应
   */
  protected static <T> BaseResponse<T> errorResponse(String code, String msg, T data) {
    return BaseResponse.<T>builder()
        .code(code)
        .msg(msg)
        .data(data)
        .timestamp(System.currentTimeMillis())
        .build();
  }

  /**
   * 构建统一异常响应（根据配置自动选择 BaseResponse 或 ProblemDetail 格式）
   *
   * <p>统一在此处记录异常处理耗时（{@code exception.handler.duration} Timer）， 使全部走响应构建链路的 handler 均纳入耗时监控。
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 响应对象（BaseResponse 或 ProblemDetail）
   */
  protected Object buildResponse(Throwable throwable, String path, String traceId) {
    long startNanos = System.nanoTime();
    try {
      return doBuildResponse(throwable, path, traceId);
    } finally {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      if (exceptionMetrics != null) {
        exceptionMetrics.recordHandlerDuration(durationMs, throwable);
      }
    }
  }

  /**
   * 构建统一异常响应的内部实现。
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 响应对象（BaseResponse 或 ProblemDetail）
   */
  private Object doBuildResponse(Throwable throwable, String path, String traceId) {
    if (useProblemDetail()) {
      return buildProblemDetail(throwable, path, traceId);
    }
    ExceptionInfo info = buildExceptionInfo(throwable, path, traceId);
    if (throwable instanceof AbstractYdszException) {
      AbstractYdszException ex = (AbstractYdszException) throwable;
      return errorResponse(ex.getCode(), ex.getMessage(), includeExceptionInfo() ? info : null);
    }
    return errorResponse(
        CoreExceptionCode.INTERNAL_ERROR.getCode(),
        info.getMessage(),
        includeExceptionInfo() ? info : null);
  }

  /**
   * 构建 ResponseEntity（动态 HTTP 状态码）
   *
   * @param body 响应体
   * @param throwable 异常对象
   * @return ResponseEntity
   */
  protected ResponseEntity<Object> buildResponseEntity(Object body, Throwable throwable) {
    int httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
    if (throwable instanceof AbstractYdszException) {
      httpStatus = ((AbstractYdszException) throwable).getHttpStatus();
    }
    return ResponseEntity.status(httpStatus).body(body);
  }

  /**
   * 对可恢复异常添加 {@code Retry-After} 响应头，引导客户端合理重试。
   *
   * <p>对标 Google API 的 {@code ErrorInfo.reason} 和 HTTP 标准 {@code Retry-After} 头， 仅当异常标记为 {@link
   * com.njydsz.common.exception.enums.ExceptionCode#retryable() retryable=true} 且 {@link
   * com.njydsz.common.exception.enums.ExceptionCode#retryAfterSeconds()} > 0 时生效。
   *
   * <p>Retry-After 头的值为建议等待秒数（相对时间），符合 RFC 7231 §7.1.3 标准。
   *
   * @param response HTTP 响应对象（可为 null，此时为空操作）
   * @param throwable 异常对象
   */
  protected void addRetryAfterHeader(
      jakarta.servlet.http.HttpServletResponse response, Throwable throwable) {
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
   * 按请求 Locale 解析国际化消息（统一入口）。
   *
   * <p>消除 MVC / WebFlux / JDBC 等处理器中重复的 {@code messageSource.getMessage(key, args, defaultMsg,
   * LocaleContextHolder.getLocale())} 四参数调用模板。
   *
   * @param key i18n 消息键
   * @param args 消息参数（可为 null）
   * @param defaultMsg 当 MessageSource 不可用或 key 未找到时的兜底文案
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
   * 构建标准错误响应（统一 {@link ExceptionInfo} + {@link BaseResponse} 组合）。
   *
   * <p>消除各处理器中重复的"new ExceptionInfo → setPath → errorResponse"三步模板。 开发/测试环境自动填充详细信息（path），生产环境仅返回
   * code + message。
   *
   * @param code 错误码字符串
   * @param key i18n 消息键（可为 null）
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @return 统一 BaseResponse
   */
  protected BaseResponse<?> buildStandardErrorResponse(
      String code, String key, String message, int httpStatus, String path) {
    if (!includeExceptionInfo()) {
      return errorResponse(code, message, null);
    }
    ExceptionInfo info = new ExceptionInfo(code, key, message, httpStatus);
    info.setPath(path);
    return errorResponse(code, message, info);
  }

  /**
   * 构建带详细信息的统一错误响应（强制包含 ExceptionInfo，便于客户端排障）。
   *
   * <p>与 {@link #buildStandardErrorResponse} 不同，本方法不受 {@link #includeExceptionInfo()} 开关控制，始终填充
   * ExceptionInfo。 适用于需要强制返回结构化错误详情的场景（如数据完整性异常分类）。
   *
   * @param code 错误码字符串
   * @param key i18n 消息键
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @return 统一 BaseResponse（始终包含 ExceptionInfo）
   */
  protected BaseResponse<?> buildWithInfo(
      String code, String key, String message, int httpStatus, String path) {
    ExceptionInfo info = new ExceptionInfo(code, key, message, httpStatus);
    info.setPath(path);
    return errorResponse(code, message, info);
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
  protected BaseResponse<?> buildValidationErrorResponse(
      CoreExceptionCode errorCode,
      String message,
      int httpStatus,
      String path,
      Throwable throwable) {
    log.error("{}校验异常 | 路径: {} | 消息: {}", getLogPrefix(), path, message, throwable);
    recordMetrics(throwable);

    ExceptionInfo info =
        new ExceptionInfo(errorCode.getCode(), errorCode.getKey(), message, httpStatus);
    info.setPath(path);
    return errorResponse(errorCode.getCode(), message, includeExceptionInfo() ? info : null);
  }
}
