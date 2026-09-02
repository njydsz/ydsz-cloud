package com.njydsz.common.exception.event;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEvent;

import com.njydsz.common.exception.enums.ExceptionCategory;

/**
 * 异常处理完成事件
 *
 * <p>当全局异常处理器成功捕获并处理异常后发布此事件，下游订阅者可用于：
 *
 * <ul>
 *   <li>自定义告警通知（钉钉/飞书/企业微信/短信）
 *   <li>异常计数熔断（基于频率的自动熔断）
 *   <li>Sentry / OpenTelemetry 上报关联
 *   <li>审计日志记录
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Component
 * public class ExceptionAlertListener {
 *     @EventListener
 *     public void onExceptionHandled(ExceptionHandledEvent event) {
 *         if ("FATAL".equals(event.getLevelName())) {
 *             sendFatalAlert(event);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ExceptionHandledEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  /** 错误码 */
  private final String code;

  /** i18n 消息键 */
  private final String key;

  /** 异常消息（已解析的 i18n 文案） */
  private final String message;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 请求路径 */
  private final String path;

  /** 追踪 ID */
  private final String traceId;

  /** 异常类别 */
  private final ExceptionCategory category;

  /** 异常级别名称 */
  private final String levelName;

  /** 异常类名（简单名） */
  private final String exceptionType;

  /** 事件发布时间 */
  private final LocalDateTime timestamp;

  /**
   * 构造异常处理完成事件
   *
   * @param source 事件源（通常是处理器实例）
   * @param code 错误码
   * @param key i18n 消息键
   * @param message 异常消息
   * @param httpStatus HTTP 状态码
   * @param path 请求路径
   * @param traceId 追踪 ID
   * @param category 异常类别
   * @param levelName 异常级别名称
   * @param exceptionType 异常类名
   */
  public ExceptionHandledEvent(
      Object source,
      String code,
      String key,
      String message,
      int httpStatus,
      String path,
      String traceId,
      ExceptionCategory category,
      String levelName,
      String exceptionType) {
    super(source);
    this.code = code;
    this.key = key;
    this.message = message;
    this.httpStatus = httpStatus;
    this.path = path;
    this.traceId = traceId;
    this.category = category;
    this.levelName = levelName;
    this.exceptionType = exceptionType;
    this.timestamp = LocalDateTime.now();
  }

  public String getCode() {
    return code;
  }

  public String getKey() {
    return key;
  }

  public String getMessage() {
    return message;
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getPath() {
    return path;
  }

  public String getTraceId() {
    return traceId;
  }

  public ExceptionCategory getCategory() {
    return category;
  }

  public String getLevelName() {
    return levelName;
  }

  public String getExceptionType() {
    return exceptionType;
  }

  /**
   * 获取事件发生时间。
   *
   * <p>父类 {@link org.springframework.context.ApplicationEvent#getTimestamp()} 为 final，
   * 本方法以业务语义命名，避免覆盖冲突。
   *
   * @return 事件发生时间（本地时间）
   */
  public LocalDateTime getEventTime() {
    return timestamp;
  }

  @Override
  public String toString() {
    return String.format(
        "ExceptionHandledEvent{code='%s', key='%s', httpStatus=%d, path='%s', "
          + "traceId='%s', category=%s, level='%s', type='%s'}",
        code, key, httpStatus, path, traceId, category, levelName, exceptionType);
  }
}
