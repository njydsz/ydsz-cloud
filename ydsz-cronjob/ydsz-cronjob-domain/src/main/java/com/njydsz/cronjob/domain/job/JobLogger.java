package com.njydsz.cronjob.domain.job;

/**
 * 任务执行日志器接口
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 由 common-domain 迁入 cronjob-domain
 */
public interface JobLogger {

  void info(String message);

  void info(String format, Object... args);

  void warn(String message);

  void warn(String format, Object... args);

  void error(String message);

  void error(String format, Object... args);

  void error(String message, Throwable t);

  void debug(String message);

  void debug(String format, Object... args);

  void flush();
}
