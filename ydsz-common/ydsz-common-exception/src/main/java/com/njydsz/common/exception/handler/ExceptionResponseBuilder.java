package com.njydsz.common.exception.handler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.trace.OtelTraceInfo;
import com.njydsz.common.exception.trace.OtelTraceInfoExtractor;
import com.njydsz.common.exception.util.ExceptionDesensitizer;

/**
 * 异常响应构建器（26.09.19 从 {@link BaseExceptionHandler} 拆分）。
 *
 * <p>负责构建 {@link ProblemDetail}（RFC 7807）与 {@link YdszResponse} 两种统一响应格式，
 * 以及 {@link ExceptionInfo} 结构化详情对象的组装。所有与环境/指标/事件相关的状态由
 * {@link BaseExceptionHandler} 提供，本类仅关注响应结构与序列化。
 *
 * <p><b>设计动机：</b>原始 {@link BaseExceptionHandler} 超过 750 行，混合了响应构建、
 * 指标记录、事件发布、i18n 解析四类职责。拆分后单元测试可独立 mock 各策略：
 *
 * <ul>
 *   <li>本类注入 {@link Environment} / {@link ExceptionMetrics} / 类型信息，构建响应
 *   <li>{@link BaseExceptionHandler} 持有本类实例 + 事件发布 + 国际化，编排整体流程
 * </ul>
 *
 * <p><b>线程安全：</b>无状态（依赖注入的 {@link ExceptionProperties} 初始化后不变），可安全委托。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see BaseExceptionHandler
 */
final class ExceptionResponseBuilder {

  private final Environment environment;
  private final ExceptionProperties properties;
  private final ExceptionMetrics exceptionMetrics;

  /**
   * 构造响应构建器
   *
   * @param environment Spring 环境对象
   * @param properties 异常模块配置属性（可为 null）
   * @param exceptionMetrics 异常指标统计器（可为 null）
   */
  ExceptionResponseBuilder(
      Environment environment, ExceptionProperties properties, ExceptionMetrics exceptionMetrics) {
    this.environment = environment;
    this.properties = properties;
    this.exceptionMetrics = exceptionMetrics;
  }

  /**
   * 构建异常信息对象（供 {@link BaseExceptionHandler#buildExceptionInfo} 委托）。
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @param includeInfo 是否包含详细信息（开发/测试环境开启）
   * @return 异常信息对象
   */
  ExceptionInfo buildExceptionInfo(Throwable throwable, String path, String traceId, boolean includeInfo) {
    ExceptionInfo info = new ExceptionInfo();
    info.setPath(path);
    if (traceId != null) {
      info.setTraceId(traceId);
    }
    info.setTimestamp(java.time.LocalDateTime.now());

    if (throwable instanceof AbstractYdszException ex) {
      info.setCode(ex.getCode());
      info.setKey(ex.getKey());
      info.setMessage(ex.getMessage());
      info.setHttpStatus(ex.getHttpStatus());
      if (ex.getLevel() != null) {
        info.setLevel(ex.getLevel().name());
      }
      if (includeInfo) {
        Map<String, Object> details = new LinkedHashMap<>(16);
        details.put("stackTrace", ExceptionDesensitizer.desensitizeStackTrace(throwable));
        if (ex.getExtData() != null) {
          ex.getExtData().forEach(details::put);
        }
        info.setDetails(details);
      }
    } else {
      info.setCode(CoreExceptionCode.INTERNAL_ERROR.getCode());
      info.setMessage(getRootCauseMessage(throwable));
      info.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      info.setLevel(ExceptionLevel.ERROR.name());
      info.setFingerprint(BaseExceptionHandler.generateFingerprint(throwable));
      if (includeInfo) {
        info.setDetails(
            Map.of(
                "stackTrace",
                ExceptionDesensitizer.desensitizeStackTrace(throwable),
                "fingerprint",
                info.getFingerprint()));
      }
    }
    return info;
  }

  /**
   * 构建 RFC 7807 ProblemDetail 对象
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return ProblemDetail 对象
   */
  ProblemDetail buildProblemDetail(Throwable throwable, String path, String traceId) {
    String baseUrl = getProblemDetailTypeBaseUrl();
    ProblemDetail problem;

    if (throwable instanceof AbstractYdszException ex) {
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
        ex.getExtData().forEach(problem::setProperty);
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
    String requestId = RequestContext.getRequestId();
    problem.setProperty("requestId", requestId != null ? requestId : traceId);
    problem.setProperty("timestamp", Instant.now().toString());

    // 透传异常级别
    String levelName = ExceptionLevel.ERROR.name();
    if (throwable instanceof AbstractYdszException ex && ex.getLevel() != null) {
      levelName = ex.getLevel().name();
    }
    problem.setProperty("level", levelName);

    // 自动注入 OpenTelemetry traceId/spanId（当 OTel 可用时）
    injectOtelTraceContext(problem);

    return problem;
  }

  /**
   * 注入 OpenTelemetry 链路追踪上下文到 ProblemDetail
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
        problem.setProperty("traceId", otelTrace.traceId());
        String requestId = RequestContext.getRequestId();
        problem.setProperty("requestId", requestId != null ? requestId : otelTrace.traceId());
      }
    } catch (Exception e) {
      // OTel 反射调用异常时降级（不影响主流程）
    }
  }

  /**
   * 构建统一异常响应（根据配置自动选择格式并记录耗时）
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 响应对象
   */
  Object buildResponse(Throwable throwable, String path, String traceId) {
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
   * 构建统一异常响应的内部实现
   *
   * @param throwable 异常对象
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @return 响应对象
   */
  private Object doBuildResponse(Throwable throwable, String path, String traceId) {
    if (useProblemDetail()) {
      return buildProblemDetail(throwable, path, traceId);
    }
    ExceptionInfo info = buildExceptionInfo(throwable, path, traceId, includeExceptionInfo());
    if (throwable instanceof AbstractYdszException ex) {
      return errorResponse(
          ex.getCode(),
          ex.getMessage(),
          includeExceptionInfo() ? info : null,
          ex.getLevel() != null ? ex.getLevel() : ExceptionLevel.ERROR);
    }
    return errorResponse(
        CoreExceptionCode.INTERNAL_ERROR.getCode(),
        info.getMessage(),
        includeExceptionInfo() ? info : null,
        ExceptionLevel.ERROR);
  }

  /**
   * 构建 ResponseEntity（动态 HTTP 状态码）
   *
   * @param body 响应体
   * @param throwable 异常对象
   * @return ResponseEntity
   */
  org.springframework.http.ResponseEntity<Object> buildResponseEntity(
      Object body, Throwable throwable) {
    int httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
    if (throwable instanceof AbstractYdszException ex) {
      httpStatus = ex.getHttpStatus();
    }
    return org.springframework.http.ResponseEntity.status(httpStatus).body(body);
  }

  /**
   * 是否使用 ProblemDetail (RFC 7807) 响应格式
   *
   * @return true-使用 ProblemDetail 格式
   */
  boolean useProblemDetail() {
    if (properties != null) {
      return properties.getResponseFormat() == ExceptionProperties.ResponseFormat.PROBLEM_DETAIL;
    }
    return false;
  }

  /**
   * 获取 ProblemDetail type URI 基础 URL
   *
   * @return 配置的基础 URL
   */
  String getProblemDetailTypeBaseUrl() {
    if (properties != null && properties.getProblemDetailTypeBaseUrl() != null) {
      return properties.getProblemDetailTypeBaseUrl();
    }
    return "about:blank";
  }

  /**
   * 是否需要包含 ExceptionInfo 详细信息
   *
   * @return true-包含详细信息
   */
  boolean includeExceptionInfo() {
    boolean configFlag = properties != null && properties.isIncludeStackTrace();
    return configFlag || isDevOrTestProfile();
  }

  /**
   * 判断是否为开发/测试环境
   *
   * @return 如果当前 profile 为 dev/test 返回 true
   */
  private boolean isDevOrTestProfile() {
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
   * 构建统一错误响应（{@link YdszResponse} 格式）
   *
   * @param code 错误码
   * @param msg 错误消息
   * @param data 附加数据
   * @param level 异常级别
   * @return 统一错误响应
   */
  <T> YdszResponse<T> errorResponse(String code, String msg, T data, ExceptionLevel level) {
    YdszResponse<T> response = new YdszResponse<>(code, msg, data);
    response.setTimestamp(System.currentTimeMillis());
    response.setLevel(level != null ? level.name() : null);
    return response;
  }

  /**
   * 构建标准错误响应（统一 {@link ExceptionInfo} + {@link YdszResponse} 组合）
   *
   * @param code 错误码
   * @param key i18n 消息键
   * @param message 已解析的错误消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @param level 异常级别
   * @return 统一 YdszResponse
   */
  YdszResponse<?> buildStandardErrorResponse(
      String code, String key, String message, int httpStatus, String path, ExceptionLevel level) {
    if (!includeExceptionInfo()) {
      return errorResponse(code, message, null, level);
    }
    ExceptionInfo info = new ExceptionInfo(code, key, message, httpStatus);
    info.setPath(path);
    if (level != null) {
      info.setLevel(level.name());
    }
    return errorResponse(code, message, info, level);
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
  YdszResponse<?> buildWithInfo(
      String code, String key, String message, int httpStatus, String path) {
    ExceptionInfo info = new ExceptionInfo(code, key, message, httpStatus);
    info.setPath(path);
    return errorResponse(code, message, info, ExceptionLevel.ERROR);
  }

  /**
   * 构建校验错误响应（不记录日志，兼容子类 5 参数调用）。
   *
   * @param errorCode 异常码
   * @param message 异常消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @param throwable 原始异常
   * @return 统一响应格式
   */
  YdszResponse<?> buildValidationErrorResponse(
      CoreExceptionCode errorCode,
      String message,
      int httpStatus,
      String path,
      Throwable throwable) {
    ExceptionLevel level = errorCode.getLevel();
    if (!includeExceptionInfo()) {
      return errorResponse(errorCode.getCode(), message, null, level);
    }
    ExceptionInfo info =
        new ExceptionInfo(errorCode.getCode(), errorCode.getKey(), message, httpStatus);
    info.setPath(path);
    if (level != null) {
      info.setLevel(level.name());
    }
    return errorResponse(errorCode.getCode(), message, info, level);
  }

  /**
   * 获取根本原因的消息
   *
   * @param throwable 异常对象
   * @return 处理结果
   */
  private static String getRootCauseMessage(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    Throwable root = throwable;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage();
  }

  // ==================== 静态工具方法（供 BaseExceptionHandler 静态委托） ====================

  /**
   * 为异常生成聚合指纹（静态版本）。
   *
   * <p>基于异常类名 + 堆栈前 5 个元素生成 16 位 hash，便于 ELK/Sentry 聚合同类根因。
   *
   * @param throwable 异常对象
   * @return 16 位十六进制指纹字符串
   */
  static String generateFingerprint(Throwable throwable) {
    if (throwable == null) {
      return "null";
    }
    StackTraceElement[] stackTrace = throwable.getStackTrace();
    if (stackTrace == null || stackTrace.length == 0) {
      return hexHash(throwable.getClass().getName());
    }
    int depth = Math.min(5, stackTrace.length);
    StringBuilder seed = new StringBuilder(128);
    seed.append(throwable.getClass().getName());
    for (int i = 0; i < depth; i++) {
      StackTraceElement element = stackTrace[i];
      seed.append('|').append(element.getClassName()).append('.').append(element.getMethodName());
    }
    return hexHash(seed.toString());
  }

  /**
   * 生成字符串的 16 位十六进制 hash（基于 SHA-256 截断）
   *
   * @param input 输入字符串
   * @return 16 位十六进制 hash
   */
  private static String hexHash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", hash[i]));
      }
      return hex.toString();
    } catch (Exception e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  /**
   * 构建统一错误响应（静态工厂，供子类静态调用）
   *
   * @param code 错误码
   * @param msg 错误消息
   * @param data 附加数据
   * @param level 异常级别
   * @return 统一错误响应
   */
  static <T> YdszResponse<T> buildErrorResponse(
      String code, String msg, T data, ExceptionLevel level) {
    YdszResponse<T> response = new YdszResponse<>(code, msg, data);
    response.setTimestamp(System.currentTimeMillis());
    response.setLevel(level != null ? level.name() : null);
    return response;
  }
}
