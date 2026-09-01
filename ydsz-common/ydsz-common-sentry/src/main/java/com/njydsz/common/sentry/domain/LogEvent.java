package com.njydsz.common.sentry.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 日志事件模型
 *
 * <p>统一的结构化日志事件，可同时发布到 ELK（Logstash）和 Loki。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class LogEvent {

  /** 时间戳 */
  @Builder.Default private Instant timestamp = Instant.now();

  /** 日志级别 */
  private LogLevel level;

  /** Logger 名称 */
  private String logger;

  /** 线程名 */
  private String thread;

  /** 日志消息 */
  private String message;

  /** 应用名 */
  private String appName;

  /** 主机名 */
  private String hostname;

  /** 环境（dev/sit/uat/prod） */
  private String profile;

  /** 追踪 ID */
  private String traceId;

  /** 用户 ID */
  private String userId;

  /** 用户名 */
  private String username;

  /** 附加字段 */
  @Builder.Default private Map<String, Object> extra = new LinkedHashMap<>();

  /** 异常堆栈 */
  private String stackTrace;

  /** 添加附加字段 */
  /**
   * add extra。
   * @param key 参数
   * @param value 参数
   * @return 结果
   */
  public LogEvent addExtra(String key, Object value) {
    if (extra == null) {
      extra = new LinkedHashMap<>();
    }
    extra.put(key, value);
    return this;
  }
}
