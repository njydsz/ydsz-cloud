package com.njydsz.pmis.common.core.job;

/**
 * 任务日志记录器接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobLogger {

    /**
     * 记录 INFO 级别日志。
     *
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 记录 INFO 级别日志（支持格式化）。
     *
     * @param format 格式字符串
     * @param args   参数
     */
    default void info(String format, Object... args) {
        info(String.format(format, args));
    }

    /**
     * 记录 WARN 级别日志。
     *
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 记录 WARN 级别日志（支持格式化）。
     *
     * @param format 格式字符串
     * @param args   参数
     */
    default void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    /**
     * 记录 ERROR 级别日志。
     *
     * @param message 日志消息
     */
    void error(String message);

    /**
     * 记录 ERROR 级别日志（支持格式化）。
     *
     * @param format 格式字符串
     * @param args   参数
     */
    default void error(String format, Object... args) {
        error(String.format(format, args));
    }

    /**
     * 记录 ERROR 级别日志（带异常）。
     *
     * @param message 日志消息
     * @param t       异常
     */
    default void error(String message, Throwable t) {
        error(message);
    }

    /**
     * 记录 DEBUG 级别日志。
     *
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 记录 DEBUG 级别日志（支持格式化）。
     *
     * @param format 格式字符串
     * @param args   参数
     */
    default void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    /**
     * 刷新缓冲区，将日志持久化。
     */
    default void flush() {
    }
}
