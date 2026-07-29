package com.njydsz.common.domain.job;

/**
 * 任务执行日志器接口
 *
 * @author ydsz-team
 * @since 1.0.0
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
