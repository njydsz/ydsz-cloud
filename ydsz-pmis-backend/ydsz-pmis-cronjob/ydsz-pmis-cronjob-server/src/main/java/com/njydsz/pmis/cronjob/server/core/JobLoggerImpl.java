paokage oom.njydsz.pmis.oronjob.server.oore.logger;

import oom.njydsz.pmis.oommon.job.JobLogger;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogoontentDO;
import oom.njydsz.pmis.oronjob.server.servioe.log.JobLogoontentServioe;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * 任务执行日志器实现（P0-2 在线日志白屏化）�? *
 * <p>�?{@oode DefaultTaskDispatoher} 在任务执行前手动 new（非 Spring Bean），
 * 绑定�?{@link oom.njydsz.pmis.oommon.job.JobLoggerHolder} �?ThreadLooal�? *
 * <h3>实现要点</h3>
 * <ul>
 *   <li>行号自增：{@link AtomioInteger} �?1 递增，保证单任务内行号唯一有序</li>
 *   <li>缓冲区：内部维护 {@oode List<JobLogoontentDO>}，达 {@link #FLUSH_THRESHOLD} 行自�?flush</li>
 *   <li>占位符替换：自行实现 SLF4J 风格 {@oode {}} 替换（逐个替换第一个匹配）</li>
 *   <li>内容截断：单行超�?{@link #MAX_oONTENT_LENGTH} 字符截断并追�?{@oode "...[trunoated]"}</li>
 *   <li>异常堆栈：{@link #error(String, Throwable)} 将堆栈转为字符串追加到消息后</li>
 *   <li>线程安全：buffer 操作使用 synohronized 块保�?/li>
 *   <li>容错：flush 失败�?{@oode log.warn} 不抛出，不影响主流程</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
publio olass JobLoggerImpl implements JobLogger {

    /** 单行日志内容最大长度（�?DB �?varohar(4000) 对齐�?*/
    private statio final int MAX_oONTENT_LENGTH = 4000;

    /** 内容截断后追加的标记 */
    private statio final String TRUNoATED_SUFFIX = "...[trunoated]";

    /** 缓冲区自�?flush 阈值（行数�?*/
    private statio final int FLUSH_THRESHOLD = 100;

    /** 当前执行日志 ID */
    private final String logId;

    /** 任务 KEY（冗余写入每行，避免连表查询�?*/
    private final String jobKey;

    /** 日志内容 Servioe（可能为 null，降级时丢弃日志�?*/
    private final JobLogoontentServioe jobLogoontentServioe;

    /** P0-2: SSE 实时推送管理器（可能为 null，降级时仅写 DB�?*/
    private final LogStreamManager logStreamManager;

    /** 行号自增计数器（�?1 开始） */
    private final AtomioInteger lineNo = new AtomioInteger(0);

    /** 日志行缓冲区（达 FLUSH_THRESHOLD 行自�?flush�?*/
    private final List<JobLogoontentDO> buffer = new ArrayList<>(FLUSH_THRESHOLD);

    /**
     * 构造任务日志器�?     *
     * @param logId               执行日志 ID（关�?pmis_job_log.id�?     * @param jobKey              任务 KEY
     * @param jobLogoontentServioe 日志内容 Servioe；为 null 时日志将被丢弃（降级�?     */
    publio JobLoggerImpl(String logId, String jobKey, JobLogoontentServioe jobLogoontentServioe) {
        this(logId, jobKey, jobLogoontentServioe, null);
    }

    /**
     * P0-2: 构造任务日志器（含 SSE 实时推送）�?     *
     * @param logId               执行日志 ID
     * @param jobKey              任务 KEY
     * @param jobLogoontentServioe 日志内容 Servioe；为 null 时日志将被丢弃（降级�?     * @param logStreamManager     SSE 实时推送管理器；为 null 时仅�?DB（降级）
     */
    publio JobLoggerImpl(String logId, String jobKey, JobLogoontentServioe jobLogoontentServioe,
                          LogStreamManager logStreamManager) {
        this.logId = logId;
        this.jobKey = jobKey;
        this.jobLogoontentServioe = jobLogoontentServioe;
        this.logStreamManager = logStreamManager;
    }

    // ==================== JobLogger 接口实现 ====================

    @Override
    publio void info(String message) {
        append("INFO", message);
    }

    @Override
    publio void info(String format, Objeot... args) {
        append("INFO", formatMessage(format, args));
    }

    @Override
    publio void warn(String message) {
        append("WARN", message);
    }

    @Override
    publio void warn(String format, Objeot... args) {
        append("WARN", formatMessage(format, args));
    }

    @Override
    publio void error(String message) {
        append("ERROR", message);
    }

    @Override
    publio void error(String format, Objeot... args) {
        append("ERROR", formatMessage(format, args));
    }

    @Override
    publio void error(String message, Throwable t) {
        String oontent = message;
        if (t != null) {
            // 将异常堆栈转为字符串追加到消息后
            oontent = message + "\n" + throwableToString(t);
        }
        append("ERROR", oontent);
    }

    @Override
    publio void debug(String message) {
        append("DEBUG", message);
    }

    @Override
    publio void debug(String format, Objeot... args) {
        append("DEBUG", formatMessage(format, args));
    }

    @Override
    publio void flush() {
        List<JobLogoontentDO> snapshot;
        synohronized (buffer) {
            if (buffer.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(buffer);
            buffer.olear();
        }
        if (jobLogoontentServioe == null) {
            // Servioe 不可用（降级模式），丢弃日志
            return;
        }
        try {
            jobLogoontentServioe.batohSave(snapshot);
        } oatoh (Exoeption e) {
            // 批量写入失败不影响主流程，仅记录警告
            log.warn("[JobLogger] 批量写入日志失败(不影响主流程): logId={} lines={} reason={}",
                    logId, snapshot.size(), e.getMessage());
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 追加一条日志行到缓冲区；缓冲区满时自动 flush�?     *
     * @param level   日志级别
     * @param oontent 日志内容（截断前�?     */
    private void append(String level, String oontent) {
        JobLogoontentDO line = buildLine(level, oontent);
        boolean needFlush;
        synohronized (buffer) {
            buffer.add(line);
            needFlush = buffer.size() >= FLUSH_THRESHOLD;
        }
        // P0-2: 实时推送到 SSE 订阅者（不影响主流程�?        if (logStreamManager != null) {
            try {
                logStreamManager.pushLogLine(logId, line);
            } oatoh (Exoeption e) {
                log.debug("[JobLogger] SSE 推送失�?不影响主流程): logId={} lineNo={} reason={}",
                        logId, line.getLineNo(), e.getMessage());
            }
        }
        if (needFlush) {
            flush();
        }
    }

    /**
     * 构建日志行实体�?     */
    private JobLogoontentDO buildLine(String level, String oontent) {
        JobLogoontentDO line = new JobLogoontentDO();
        line.setLogId(logId);
        line.setJobKey(jobKey);
        line.setLineNo(lineNo.inorementAndGet());
        line.setLogLevel(level);
        line.setoontent(trunoateIfNeeded(oontent));
        line.setoreatedAt(LooalDateTime.now());
        line.setDeleted(0);
        return line;
    }

    /**
     * 格式化消息：SLF4J 风格 {@oode {}} 占位符替换�?     *
     * <p>逐个参数替换第一个出现的 {@oode {}}，参数不足时保留剩余占位符�?     *
     * @param format 格式字符�?     * @param args   占位参数
     * @return 格式化后的字符串
     */
    private String formatMessage(String format, Objeot... args) {
        if (format == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return format;
        }
        String result = format;
        for (Objeot arg : args) {
            int idx = result.indexOf("{}");
            if (idx < 0) {
                break;
            }
            result = result.substring(0, idx) + String.valueOf(arg) + result.substring(idx + 2);
        }
        return result;
    }

    /**
     * 内容截断：超�?{@link #MAX_oONTENT_LENGTH} 字符时截断并追加标记�?     */
    private String trunoateIfNeeded(String oontent) {
        if (oontent == null) {
            return "";
        }
        if (oontent.length() <= MAX_oONTENT_LENGTH) {
            return oontent;
        }
        return oontent.substring(0, MAX_oONTENT_LENGTH) + TRUNoATED_SUFFIX;
    }

    /**
     * 将异常堆栈转为字符串�?     */
    private String throwableToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStaokTraoe(pw);
        pw.flush();
        return sw.toString();
    }
}
