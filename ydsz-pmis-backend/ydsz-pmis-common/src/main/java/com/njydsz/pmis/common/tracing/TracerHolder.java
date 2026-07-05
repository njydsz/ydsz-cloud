package com.njydsz.pmis.common.tracing;

import brave.Span;
import brave.Tracer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Brave {@link Tracer} 静态持有器（P1-6）
 *
 * <p>设计目的：
 * <ul>
 *   <li>让纯工具类（如 {@code TraceIdUtil}）能在不依赖 Spring 注入的情况下
 *       读取当前 span 的 traceId，实现与 Brave/Micrometer Tracing 的桥接</li>
 *   <li>当 Brave 未启用或未配置上报端点时，自动降级到雪花算法（见 {@code TraceIdUtil}）</li>
 *   <li>启动时输出一行 INFO 日志，便于运维确认 Tracing 链路已就绪</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 *   Tracer tracer = TracerHolder.get();
 *   if (tracer != null) {
 *       Span span = tracer.currentSpan();
 *       if (span != null) {
 *           String traceId = span.context().traceIdString();
 *       }
 *   }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
@Component
public class TracerHolder {

    /** Tracer 单例（Spring 注入后写入静态字段，供工具类访问） */
    private static volatile Tracer INSTANCE;

    /** Spring 是否注入了 Tracer（用于区分"未注入"与"已注入但无当前 span"） */
    private static volatile boolean INITIALIZED = false;

    /**
     * 注入 Brave Tracer（可选依赖：当未引入 micrometer-tracing-bridge-brave 时为 null）
     *
     * @param tracer Brave Tracer（可空）
     */
    @Autowired
    public void setTracer(@Autowired(required = false) Tracer tracer) {
        TracerHolder.INSTANCE = tracer;
        TracerHolder.INITIALIZED = true;
    }

    /**
     * 启动后输出 Tracing 就绪日志
     */
    @PostConstruct
    public void init() {
        if (INSTANCE != null) {
            log.info("[Tracing] Brave Tracer 已就绪，traceId 将由 Micrometer Tracing 接管");
        } else {
            log.warn("[Tracing] Brave Tracer 未配置，traceId 降级使用雪花算法（SnowflakeIdGenerator）");
        }
    }

    /**
     * 获取当前 Spring 上下文中的 Brave Tracer
     *
     * @return Tracer 实例；未配置 Brave 时返回 null
     */
    public static Tracer get() {
        return INSTANCE;
    }

    /**
     * 判断 Tracer 是否已初始化（无论是否为 null）
     *
     * <p>用于区分 "Spring 未启动" 与 "未配置 Brave" 两种场景。
     *
     * @return true 表示 Spring 已完成注入
     */
    public static boolean isInitialized() {
        return INITIALIZED;
    }

    /**
     * 清理静态状态（仅用于单元测试）
     */
    static void resetForTest() {
        INSTANCE = null;
        INITIALIZED = false;
    }

    /**
     * 获取当前 span 的 traceId（便捷方法）
     *
     * @return 当前 traceId；无当前 span 或未配置 Tracer 时返回 null
     */
    public static String currentTraceId() {
        Tracer tracer = INSTANCE;
        if (tracer == null) {
            return null;
        }
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null) {
            return null;
        }
        return span.context().traceIdString();
    }
}
