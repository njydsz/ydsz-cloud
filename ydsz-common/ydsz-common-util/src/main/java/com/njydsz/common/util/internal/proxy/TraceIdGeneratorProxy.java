package com.njydsz.common.util.internal.proxy;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ydsz-common-core 模块中 {@code TraceIdGenerator} 的反射代理。
 *
 * <p>提供 TraceId 和 SpanId 的生成能力，以及 W3C traceparent 编解码。</p>
 *
 * <p>当 ydsz-common-core 不在 classpath 时，降级为基于 ThreadLocalRandom 的简易实现。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TraceIdGeneratorProxy {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGeneratorProxy.class);

    /** TraceIdGenerator 类全限定名 */
    private static final String GENERATOR_CLASS = "com.njydsz.common.core.trace.TraceIdGenerator";

    /** ParsedTraceparent 内部 Record 类全限定名 */
    private static final String PARSED_TRACEPARENT_CLASS = "com.njydsz.common.core.trace.TraceIdGenerator$ParsedTraceparent";

    /** 反射 Method 缓存 */
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /** 是否可用的标记 */
    private static volatile Boolean available;

    /** Trace ID 长度（32 位十六进制 = 128 bit） */
    private static final int TRACE_ID_LENGTH = 32;

    /** Span ID 长度（16 位十六进制 = 64 bit） */
    private static final int SPAN_ID_LENGTH = 16;

    private TraceIdGeneratorProxy() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 检查 TraceIdGenerator 是否可用。
     *
     * @return true 表示可用
     */
    public static boolean isAvailable() {
        if (available == null) {
            synchronized (TraceIdGeneratorProxy.class) {
                if (available == null) {
                    try {
                        Class.forName(GENERATOR_CLASS);
                        available = Boolean.TRUE;
                    } catch (ClassNotFoundException e) {
                        available = Boolean.FALSE;
                        log.debug("ydsz-common-core 不在 classpath 中，TraceId 将使用内置简易实现");
                    }
                }
            }
        }
        return available;
    }

    /**
     * 生成时间有序的 Trace ID（32 位十六进制）。
     *
     * <p>W3C TraceContext 兼容格式，与 SkyWalking、Zipkin 等主流规范互通。</p>
     *
     * @return Trace ID 字符串
     */
    public static String generateSortableTraceId() {
        if (isAvailable()) {
            try {
                Method method = getCachedMethod("generateSortableTraceId");
                return (String) method.invoke(null);
            } catch (Exception e) {
                log.debug("调用 TraceIdGenerator.generateSortableTraceId() 失败，使用降级实现: {}", e.getMessage());
            }
        }
        return fallbackGenerateHex(TRACE_ID_LENGTH);
    }

    /**
     * 生成 Span ID（16 位十六进制）。
     *
     * @return Span ID 字符串
     */
    public static String generateSpanId() {
        if (isAvailable()) {
            try {
                Method method = getCachedMethod("generateSpanId");
                return (String) method.invoke(null);
            } catch (Exception e) {
                log.debug("调用 TraceIdGenerator.generateSpanId() 失败，使用降级实现: {}", e.getMessage());
            }
        }
        return fallbackGenerateHex(SPAN_ID_LENGTH);
    }

    /**
     * 解析 W3C traceparent 头。
     *
     * @param traceparent W3C traceparent 字符串
     * @return 解析结果；解析失败返回 null
     */
    public static ParsedTraceparent parseTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isEmpty()) {
            return null;
        }
        if (isAvailable()) {
            try {
                Method method = getCachedMethod("parseTraceparent", String.class);
                Object result = method.invoke(null, traceparent);
                if (result == null) {
                    return null;
                }
                // 处理返回值为 core 模块 Record 类型的情况
                return convertToParsedTraceparent(result);
            } catch (Exception e) {
                log.debug("调用 TraceIdGenerator.parseTraceparent() 失败: {}", e.getMessage());
            }
        }
        // 降级实现：简单解析
        return fallbackParseTraceparent(traceparent);
    }

    /**
     * 从 core 模块的 Record 实例转换为本地 ParsedTraceparent。
     *
     * @param record core 模块的 ParsedTraceparent Record 实例
     * @return 本地 ParsedTraceparent；转换失败返回 null
     */
    private static ParsedTraceparent convertToParsedTraceparent(Object record) {
        try {
            Class<?> clazz = record.getClass();
            if (clazz.isRecord()) {
                // 尝试获取 record 的 component values
                java.lang.reflect.RecordComponent[] components = clazz.getRecordComponents();
                if (components.length >= 2) {
                    Object traceId = components[0].getAccessor().invoke(record);
                    Object spanId = components[1].getAccessor().invoke(record);
                    return new ParsedTraceparent(
                        traceId != null ? traceId.toString() : null,
                        spanId != null ? spanId.toString() : null
                    );
                }
            }
        } catch (Exception e) {
            log.debug("转换 ParsedTraceparent 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 降级实现：基于 ThreadLocalRandom 生成指定位数的十六进制字符串。
     *
     * @param length 十六进制字符串长度
     * @return 随机十六进制字符串
     */
    private static String fallbackGenerateHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(random.nextInt(16)));
        }
        return sb.toString();
    }

    /**
     * 降级实现：简单解析 W3C traceparent。
     *
     * <p>格式：00-{32hex}-{16hex}-{flags}</p>
     *
     * @param traceparent traceparent 字符串
     * @return ParsedTraceparent；解析失败返回 null
     */
    private static ParsedTraceparent fallbackParseTraceparent(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        if (parts.length >= 3 && parts[1].length() == TRACE_ID_LENGTH && parts[2].length() == SPAN_ID_LENGTH) {
            return new ParsedTraceparent(parts[1], parts[2]);
        }
        return null;
    }

    /**
     * 获取缓存的反射 Method。
     */
    private static Method getCachedMethod(String methodName, Class<?>... paramTypes) {
        String cacheKey = methodName + "_" + paramTypes.length;
        Method method = METHOD_CACHE.get(cacheKey);
        if (method == null) {
            synchronized (TraceIdGeneratorProxy.class) {
                method = METHOD_CACHE.get(cacheKey);
                if (method == null) {
                    try {
                        Class<?> clazz = Class.forName(GENERATOR_CLASS);
                        method = clazz.getMethod(methodName, paramTypes);
                        method.setAccessible(true);
                        METHOD_CACHE.put(cacheKey, method);
                    } catch (NoSuchMethodException | ClassNotFoundException e) {
                        return null;
                    }
                }
            }
        }
        return method;
    }
}
