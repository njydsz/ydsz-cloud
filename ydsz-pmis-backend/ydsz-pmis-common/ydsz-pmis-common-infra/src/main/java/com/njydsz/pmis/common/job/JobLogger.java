package com.njydsz.pmis.common.job;

/**
 * 任务执行日志器（P0-2 在线日志白屏化）。
 *
 * <p>业务侧在 {@link JobHandler#execute(String)} 内通过 {@link JobLoggerHolder#get()} 获取当前日志器，
 * 调用 {@code info/warn/error/debug} 写入日志行。日志行异步批量写入 {@code pmis_job_log_content} 表，
 * 前端通过 SSE 实时滚动展示，实现 XXL-JOB / PowerJob 级别的在线日志白屏化体验。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component("myJobHandler")
 * public class MyJobHandler implements JobHandler {
 *     @Override
 *     public Object execute(String paramsJson) throws Exception {
 *         JobLogger logger = JobLoggerHolder.get();
 *         if (logger != null) {
 *             logger.info("开始处理, params={}", paramsJson);
 *         }
 *         // 业务逻辑...
 *         if (logger != null) {
 *             logger.info("处理完成, 共处理 {} 条记录", count);
 *         }
 *         return result;
 *     }
 * }
 * }</pre>
 *
 * <p><b>线程安全</b>：每个任务执行线程绑定独立的 JobLogger 实例（ThreadLocal），
 * 日志行号在实例内自增，无需外部同步。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobLogger {

    /**
     * 写入 INFO 级别日志行。
     *
     * @param message 日志消息
     */
    void info(String message);

    /**
     * 写入 INFO 级别日志行（支持 SLF4J 风格占位符）。
     *
     * @param format 格式字符串（如 "处理 {} 条记录"）
     * @param args   占位参数
     */
    void info(String format, Object... args);

    /**
     * 写入 WARN 级别日志行。
     *
     * @param message 日志消息
     */
    void warn(String message);

    /**
     * 写入 WARN 级别日志行（支持占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void warn(String format, Object... args);

    /**
     * 写入 ERROR 级别日志行。
     *
     * @param message 日志消息
     */
    void error(String message);

    /**
     * 写入 ERROR 级别日志行（支持占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void error(String format, Object... args);

    /**
     * 写入 ERROR 级别日志行（含异常堆栈）。
     *
     * @param message 日志消息
     * @param t       异常对象（堆栈会被转为字符串写入日志）
     */
    void error(String message, Throwable t);

    /**
     * 写入 DEBUG 级别日志行。
     *
     * @param message 日志消息
     */
    void debug(String message);

    /**
     * 写入 DEBUG 级别日志行（支持占位符）。
     *
     * @param format 格式字符串
     * @param args   占位参数
     */
    void debug(String format, Object... args);

    /**
     * 立即刷新缓冲区中的日志到存储（DB + 文件）。
     *
     * <p>正常情况下由 {@code DefaultTaskDispatcher} 在任务执行完成后自动调用；
     * 业务侧一般无需手动调用。长任务可定期调用以保证日志实时性。
     */
    void flush();
}
