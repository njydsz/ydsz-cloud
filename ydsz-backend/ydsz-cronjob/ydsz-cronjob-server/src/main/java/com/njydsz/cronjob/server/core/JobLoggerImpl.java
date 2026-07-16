package com.njydsz.cronjob.server.core.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.njydsz.common.core.job.JobLogger;
import com.njydsz.cronjob.domain.entity.log.JobLogContentDO;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行日志器实现（P0-2 在线日志白屏化）。
 *
 * <p>由 {@code DefaultTaskDispatcher} 在任务执行前手动 new（非 Spring Bean），
 * 绑定到 {@link com.njydsz.common.job.JobLoggerHolder} 的 ThreadLocal。
 *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>行号自增：{@link AtomicInteger} 从 1 递增，保证单任务内行号唯一有序</li>
 *   <li>缓冲区：内部维护 {@code List<JobLogContentDO>}，达 {@link #FLUSH_THRESHOLD} 行自动 flush</li>
 *   <li>占位符替换：自行实现 SLF4J 风格 {@code {}} 替换（逐个替换第一个匹配）</li>
 *   <li>内容截断：单行超过 {@link #MAX_CONTENT_LENGTH} 字符截断并追加 {@code "...[truncated]"}</li>
 *   <li>异常堆栈：{@link #error(String, Throwable)} 将堆栈转为字符串追加到消息后</li>
 *   <li>线程安全：buffer 操作使用 synchronized 块保护</li>
 *   <li>容错：flush 失败仅 {@code log.warn} 不抛出，不影响主流程</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobLoggerImpl implements JobLogger {

    /** 单行日志内容最大长度（与 DB 列 varchar(4000) 对齐） */
    private static final int MAX_CONTENT_LENGTH = 4000;

    /** 内容截断后追加的标记 */
    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    /** 缓冲区自动 flush 阈值（行数） */
    private static final int FLUSH_THRESHOLD = 100;

    /** 当前执行日志 ID */
    private final String logId;

    /** 任务 KEY（冗余写入每行，避免连表查询） */
    private final String jobKey;

    /** 日志内容 Service（可能为 null，降级时丢弃日志） */
    private final JobLogContentService jobLogContentService;

    /** P0-2: SSE 实时推送管理器（可能为 null，降级时仅写 DB） */
    private final LogStreamManager logStreamManager;

    /** 行号自增计数器（从 1 开始） */
    private final AtomicInteger lineNo = new AtomicInteger(0);

    /** 日志行缓冲区（达 FLUSH_THRESHOLD 行自动 flush） */
    private final List<JobLogContentDO> buffer = new ArrayList<>(FLUSH_THRESHOLD);

    /**
     * 构造任务日志器。
     *
     * @param logId               执行日志 ID（关联 ydsz_job_log.id）
     * @param jobKey              任务 KEY
     * @param jobLogContentService 日志内容 Service；为 null 时日志将被丢弃（降级）
     */
    public JobLoggerImpl(String logId, String jobKey, JobLogContentService jobLogContentService) {
        this(logId, jobKey, jobLogContentService, null);
    }

    /**
     * P0-2: 构造任务日志器（含 SSE 实时推送）。
     *
     * @param logId               执行日志 ID
     * @param jobKey              任务 KEY
     * @param jobLogContentService 日志内容 Service；为 null 时日志将被丢弃（降级）
     * @param logStreamManager     SSE 实时推送管理器；为 null 时仅写 DB（降级）
     */
    public JobLoggerImpl(String logId, String jobKey, JobLogContentService jobLogContentService,
                          LogStreamManager logStreamManager) {
        this.logId = logId;
        this.jobKey = jobKey;
        this.jobLogContentService = jobLogContentService;
        this.logStreamManager = logStreamManager;
    }

    // ==================== JobLogger 接口实现 ====================

    @Override
    public void info(String message) {
        append("INFO", message);
    }

    @Override
    public void info(String format, Object... args) {
        append("INFO", formatMessage(format, args));
    }

    @Override
    public void warn(String message) {
        append("WARN", message);
    }

    @Override
    public void warn(String format, Object... args) {
        append("WARN", formatMessage(format, args));
    }

    @Override
    public void error(String message) {
        append("ERROR", message);
    }

    @Override
    public void error(String format, Object... args) {
        append("ERROR", formatMessage(format, args));
    }

    @Override
    public void error(String message, Throwable t) {
        String content = message;
        if (t != null) {
            // 将异常堆栈转为字符串追加到消息后
            content = message + "\n" + throwableToString(t);
        }
        append("ERROR", content);
    }

    @Override
    public void debug(String message) {
        append("DEBUG", message);
    }

    @Override
    public void debug(String format, Object... args) {
        append("DEBUG", formatMessage(format, args));
    }

    @Override
    public void flush() {
        List<JobLogContentDO> snapshot;
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(buffer);
            buffer.clear();
        }
        if (jobLogContentService == null) {
            // Service 不可用（降级模式），丢弃日志
            return;
        }
        try {
            jobLogContentService.batchSave(snapshot);
        } catch (Exception e) {
            // 批量写入失败不影响主流程，仅记录警告
            log.warn("[JobLogger] 批量写入日志失败(不影响主流程): logId={} lines={} reason={}",
                    logId, snapshot.size(), e.getMessage());
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 追加一条日志行到缓冲区；缓冲区满时自动 flush。
     *
     * @param level   日志级别
     * @param content 日志内容（截断前）
     */
    private void append(String level, String content) {
        JobLogContentDO line = buildLine(level, content);
        boolean needFlush;
        synchronized (buffer) {
            buffer.add(line);
            needFlush = buffer.size() >= FLUSH_THRESHOLD;
        }
        // P0-2: 实时推送到 SSE 订阅者（不影响主流程）
        if (logStreamManager != null) {
            try {
                logStreamManager.pushLogLine(logId, line);
            } catch (Exception e) {
                log.debug("[JobLogger] SSE 推送失败(不影响主流程): logId={} lineNo={} reason={}",
                        logId, line.getLineNo(), e.getMessage());
            }
        }
        if (needFlush) {
            flush();
        }
    }

    /**
     * 构建日志行实体。
     */
    private JobLogContentDO buildLine(String level, String content) {
        JobLogContentDO line = new JobLogContentDO();
        line.setLogId(logId);
        line.setJobKey(jobKey);
        line.setLineNo(lineNo.incrementAndGet());
        line.setLogLevel(level);
        line.setContent(truncateIfNeeded(content));
        line.setCreatedAt(LocalDateTime.now());
        line.setDeleted(0);
        return line;
    }

    /**
     * 格式化消息：SLF4J 风格 {@code {}} 占位符替换。
     *
     * <p>逐个参数替换第一个出现的 {@code {}}，参数不足时保留剩余占位符。
     *
     * @param format 格式字符串
     * @param args   占位参数
     * @return 格式化后的字符串
     */
    private String formatMessage(String format, Object... args) {
        if (format == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return format;
        }
        String result = format;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx < 0) {
                break;
            }
            result = result.substring(0, idx) + String.valueOf(arg) + result.substring(idx + 2);
        }
        return result;
    }

    /**
     * 内容截断：超过 {@link #MAX_CONTENT_LENGTH} 字符时截断并追加标记。
     */
    private String truncateIfNeeded(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH) + TRUNCATED_SUFFIX;
    }

    /**
     * 将异常堆栈转为字符串。
     */
    private String throwableToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
