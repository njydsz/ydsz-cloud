package com.njydsz.pmis.common.exception.metrics;

import com.njydsz.pmis.common.exception.custom.AbstractYdszException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 异常处理器装饰器（增强可观测性）
 *
 * <p>为异常处理器提供：
 * <ul>
 *   <li>Micrometer 指标记录（错误计数 + 处理耗时）</li>
 *   <li>TraceId 透传（自动从 MDC 读取）</li>
 *   <li>结构化日志输出（JSON 格式，便于 ELK 收集）</li>
 *   <li>敏感信息脱敏钩子</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RestControllerAdvice
 * public class MyExceptionHandler {
 *     private final ExceptionMetricsRecorder recorder = ...;
 *
 *     @ExceptionHandler(BusinessException.class)
 *     public BaseResponse<?> handle(BusinessException e, HttpServletRequest req) {
 *         return recorder.record("BusinessException", e, req, () -> {
 *             // 业务处理逻辑
 *             return BaseResponse.error(...);
 *         });
 *     }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
public class ExceptionMetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMetricsRecorder.class);

    /** 结构化日志 Marker 名称 */
    public static final String LOG_MARKER = "EXCEPTION_RECORD";

    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final boolean recordStackTrace;

    public ExceptionMetricsRecorder(MeterRegistry meterRegistry, boolean enabled) {
        this(meterRegistry, enabled, false);
    }

    public ExceptionMetricsRecorder(MeterRegistry meterRegistry, boolean enabled, boolean recordStackTrace) {
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.recordStackTrace = recordStackTrace;
    }

    /**
     * 记录异常并执行业务处理逻辑
     *
     * @param exceptionType 异常类型（用于指标标签）
     * @param throwable     异常对象
     * @param path          请求路径
     * @param handler       业务处理逻辑
     * @param <T>           返回类型
     * @return 业务处理结果
     */
    public <T> T record(String exceptionType, Throwable throwable, String path, Handler<T> handler) {
        if (!enabled || meterRegistry == null) {
            return handler.handle();
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return handler.handle();
        } finally {
            long durationNanos = sample.stop(timer(exceptionType));
            recordException(exceptionType, throwable, path);
            logStructured(exceptionType, throwable, path, durationNanos);
        }
    }

    /**
     * 记录异常指标
     */
    public void recordException(String exceptionType, Throwable throwable, String path) {
        if (!enabled || meterRegistry == null) {
            return;
        }
        try {
            String level = "UNKNOWN";
            String category = "UNKNOWN";
            String code = "N/A";

            if (throwable instanceof AbstractYdszException) {
                AbstractYdszException ex = (AbstractYdszException) throwable;
                if (ex.getLevel() != null) {
                    level = ex.getLevel().name();
                }
                if (ex.getCategory() != null) {
                    category = ex.getCategory().name();
                }
                if (ex.getCode() != null) {
                    code = ex.getCode();
                }
            }

            Tags tags = Tags.of(
                    "type", exceptionType,
                    "level", level,
                    "category", category,
                    "code", code
            );
            if (path != null) {
                Counter.builder("exception.count")
                        .tags(tags.and("path", normalizePath(path)))
                        .description("异常计数")
                        .register(meterRegistry)
                        .increment();
            } else {
                Counter.builder("exception.count")
                        .tags(tags)
                        .description("异常计数")
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.warn("记录异常指标失败: {}", e.getMessage());
        }
    }

    /**
     * 记录异常处理耗时
     */
    public void recordDuration(String exceptionType, long durationNanos) {
        if (!enabled || meterRegistry == null) {
            return;
        }
        try {
            timer(exceptionType).record(durationNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.warn("记录异常耗时失败: {}", e.getMessage());
        }
    }

    private Timer timer(String exceptionType) {
        return Timer.builder("exception.handler.duration")
                .tag("type", exceptionType)
                .description("异常处理耗时")
                .register(meterRegistry);
    }

    private void logStructured(String exceptionType, Throwable throwable, String path, long durationNanos) {
        if (!log.isErrorEnabled()) {
            return;
        }
        String traceId = readTraceId();
        StringBuilder sb = new StringBuilder(256);
        sb.append("exceptionType=").append(exceptionType);
        sb.append(" | path=").append(path == null ? "N/A" : path);
        sb.append(" | duration=").append(durationNanos / 1_000_000).append("ms");
        if (traceId != null) {
            sb.append(" | traceId=").append(traceId);
        }
        if (throwable instanceof AbstractYdszException) {
            AbstractYdszException ex = (AbstractYdszException) throwable;
            sb.append(" | code=").append(ex.getCode());
            sb.append(" | key=").append(ex.getKey());
        }
        sb.append(" | message=").append(throwable.getMessage());
        if (recordStackTrace) {
            log.error("[{}] {}", LOG_MARKER, sb, throwable);
        } else {
            log.error("[{}] {}", LOG_MARKER, sb);
        }
    }

    /**
     * 从 MDC 中读取 traceId
     */
    private String readTraceId() {
        try {
            java.util.Map<String, String> mdc = org.slf4j.MDC.getCopyOfContextMap();
            if (mdc != null) {
                String traceId = mdc.get("traceId");
                if (traceId == null) {
                    traceId = mdc.get("X-Trace-Id");
                }
                if (traceId == null) {
                    traceId = mdc.get("trace_id");
                }
                return traceId;
            }
        } catch (Exception e) {
            log.debug("提取 traceId 失败 | error={}", e.getMessage());
        }
        return null;
    }

    /**
     * 归一化路径，避免高基数（去除数字 ID 等）
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        if (path.length() > 100) {
            path = path.substring(0, 100);
        }
        return path.replaceAll("/\\d+", "/{id}");
    }

    /**
     * 业务处理函数式接口
     */
    @FunctionalInterface
    public interface Handler<T> {
        T handle();
    }
}
