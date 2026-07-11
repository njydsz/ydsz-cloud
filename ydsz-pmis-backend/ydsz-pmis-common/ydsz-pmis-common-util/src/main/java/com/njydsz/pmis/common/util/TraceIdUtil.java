package com.njydsz.pmis.common.util;

import com.njydsz.pmis.common.tracing.TracerHolder;
import org.slf4j.MDC;

/**
 * 链路追踪 ID 工具（P1-6 桥接 Brave/Micrometer Tracing）
 *
 * <p>traceId 来源优先级：
 * <ol>
 *   <li>Brave {@code Tracer.currentSpan().context().traceIdString()}（Micrometer Tracing 自动写入）</li>
 *   <li>MDC 中已有的 traceId（由 {@code Slf4jCurrentTraceContext} 或本工具写入）</li>
 *   <li>雪花算法 16 位 hex（降级，避免 null）</li>
 * </ol>
 *
 * <p>说明：
 * <ul>
 *   <li>Brave 接管后，{@code Slf4jCurrentTraceContext} 会自动将 traceId 写入 MDC，
 *       与 logback {@code %X{traceId:-}} 自动联动，无需手工 set/clear</li>
 *   <li>{@code TraceIdFilter} 仍保留 HIGHEST_PRECEDENCE，用于兼容旧客户端的 {@code X-Trace-Id} header</li>
 *   <li>未配置 Brave（如单元测试）时，自动降级到 {@link SnowflakeIdGenerator#nextTraceId()}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class TraceIdUtil {

    /** MDC 中 traceId 的 key（与 Brave {@code Slf4jCurrentTraceContext} 默认值一致） */
    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
    }

    /**
     * 生成 traceId：优先从 Brave Tracer 获取当前 span，降级雪花算法
     *
     * <p>注意：本方法不写入 MDC。如需同步写入 MDC，请使用 {@link #getOrCreate()}。
     *
     * @return 非空 traceId 字符串（16/32 位 hex）
     */
    public static String generate() {
        // 1) 优先从 Brave Tracer 获取当前 span traceId
        String braveTraceId = TracerHolder.currentTraceId();
        if (braveTraceId != null && !braveTraceId.isEmpty()) {
            return braveTraceId;
        }
        // 2) 降级：雪花算法 16 位 hex（兼容无 Brave 场景，如单元测试）
        return SnowflakeIdGenerator.nextTraceId();
    }

    /**
     * 获取当前线程的 traceId
     *
     * <p>优先读取 MDC（Brave {@code Slf4jCurrentTraceContext} 已写入），再降级到 Tracer。
     *
     * @return traceId；未设置时返回空字符串
     */
    public static String get() {
        // 1) MDC 优先（Brave 已写入）
        String id = MDC.get(TRACE_ID_KEY);
        if (id != null && !id.isEmpty()) {
            return id;
        }
        // 2) 降级：从 Brave Tracer 获取
        String braveTraceId = TracerHolder.currentTraceId();
        if (braveTraceId != null && !braveTraceId.isEmpty()) {
            return braveTraceId;
        }
        // 3) 都没有：返回空字符串
        return "";
    }

    /**
     * 获取或创建：未设置时自动生成并写入 MDC
     *
     * @return 非空 traceId
     */
    public static String getOrCreate() {
        String id = get();
        if (id == null || id.isEmpty()) {
            id = generate();
            MDC.put(TRACE_ID_KEY, id);
        }
        return id;
    }

    /**
     * 设置 traceId 到当前线程 MDC
     *
     * <p>仅用于兼容自研 {@code TraceIdFilter}，Brave 接管时无需手工调用。
     *
     * @param traceId traceId
     */
    public static void set(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清除当前线程的 traceId
     *
     * <p>仅用于兼容自研 {@code TraceIdFilter}，Brave 接管时由其自行管理。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
