package com.njydsz.common.core.job;

/**
 * 任务执行日志器接口。
 *
 * <p>供 CronJob 等调度模块在任务执行过程中写入在线日志，支持 SLF4J 风格占位符。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogger {

    /**
     * 输出 INFO 级别日志。
     *
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 输出 INFO 级别日志（带占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void info(String format, Object... args);

    /**
     * 输出 WARN 级别日志。
     *
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 输出 WARN 级别日志（带占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void warn(String format, Object... args);

    /**
     * 输出 ERROR 级别日志。
     *
     * @param message 日志消息
     */
    void error(String message);

    /**
     * 输出 ERROR 级别日志（带占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void error(String format, Object... args);

    /**
     * 输出 ERROR 级别日志（带异常）。
     *
     * @param message 日志消息
     * @param t       异常
     */
    void error(String message, Throwable t);

    /**
     * 输出 DEBUG 级别日志。
     *
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 输出 DEBUG 级别日志（带占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void debug(String format, Object... args);

    /**
     * 刷新缓冲区，将已写入的日志持久化。
     */
    void flush();
}
