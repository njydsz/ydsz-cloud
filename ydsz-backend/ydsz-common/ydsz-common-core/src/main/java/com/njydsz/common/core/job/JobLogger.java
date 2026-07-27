package com.njydsz.common.core.job;

/**
 * 任务执行日志器接口
 *
 * <p>供 CronJob 等调度模块在任务执行过程中写入在线日志，支持 SLF4J 风格占位符。
 * 与 SLF4J 的区别：本接口的日志同时持久化到数据库（{@code ydsz_job_log} 表），
 * 供前端「任务执行日志」页面实时展示，不依赖 ELK 等外部日志系统。
 *
 * <p><b>典型实现：</b>{@link JobLoggerHolder}（默认）写入内存队列 + 异步批量落库。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * JobLogger logger = JobLoggerHolder.getLogger();
 * logger.info("开始处理订单, count={}", orderList.size());
 * try {
 *     processOrders(orderList);
 *     logger.info("订单处理完成");
 * } catch (Exception e) {
 *     logger.error("订单处理失败", e);
 *     throw e;
 * }
 * logger.flush();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JobLoggerHolder
 */
public interface JobLogger {

    /**
     * 输出 INFO 级别日志
     *
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 输出 INFO 级别日志（带占位符）
     *
     * <p>采用 SLF4J 风格占位符（{@code "{}"}），由实现方格式化后写入数据库。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void info(String format, Object... args);

    /**
     * 输出 WARN 级别日志
     *
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 输出 WARN 级别日志（带占位符）
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void warn(String format, Object... args);

    /**
     * 输出 ERROR 级别日志
     *
     * @param message 日志消息
     */
    void error(String message);

    /**
     * 输出 ERROR 级别日志（带占位符）
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void error(String format, Object... args);

    /**
     * 输出 ERROR 级别日志（带异常）
     *
     * <p>异常堆栈会序列化存储，便于日志页查看完整堆栈。
     *
     * @param message 日志消息
     * @param t       异常
     */
    void error(String message, Throwable t);

    /**
     * 输出 DEBUG 级别日志
     *
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 输出 DEBUG 级别日志（带占位符）
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void debug(String format, Object... args);

    /**
     * 刷新缓冲区，将已写入的日志持久化
     *
     * <p>为提升性能，部分实现会先写入内存缓冲区并定时批量落库。
     * 在以下场景必须显式调用：① 任务执行结束前；② 抛出异常前；③ 任务耗时较长时定期调用。
     */
    void flush();
}
