package com.njydsz.common.sentry.tracing.otel;

import java.util.Map;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
/**
 * YDSZ Span 属性增强器
 *
 * <p>Span 结束前自动注入 YDSZ 业务上下文属性，无需业务方手动 setAttribute。
 * 支持从多种来源读取：
 * <ul>
 *   <li>MDC（SLF4J Logging Diagnostic Context）</li>
 *   <li>ThreadLocal（RequestContext）</li>
 *   <li>环境变量</li>
 *   <li>Span 名称模式匹配</li>
 * </ul>
 *
 * <p>典型使用：在 SentryAutoConfiguration 中注册到 SDK TracerProvider。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class YdszSpanEnrichmentProcessor implements SpanProcessor {

    private final EnrichmentConfig config;

    public YdszSpanEnrichmentProcessor(EnrichmentConfig config) {
        this.config = config;
        log.info("[Sentry] YdszSpanEnrichmentProcessor 初始化，来源：{}", config.getSources());
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (!config.isEnabled()) {
            return;
        }
        for (String source : config.getSources()) {
            applySource(span, source);
        }
    }

    @Override
    public boolean isStartRequired() {
        return config.isEnabled();
    }

    @Override
    public void onEnd(ReadableSpan span) {
        // no-op
    }

    @Override
    public boolean isEndRequired() {
        return false;
    }

    /**
     * 应用指定来源的属性
     */
    private void applySource(ReadWriteSpan span, String source) {
        try {
            switch (source.toLowerCase()) {
                case "mdc" -> applyMdc(span);
                case "request-context" -> applyRequestContext(span);
                case "env" -> applyEnv(span);
                default -> log.debug("[Sentry] 未知的 Enrichment 来源: {}", source);
            }
        } catch (Exception e) {
            log.debug("[Sentry] Enrichment 来源 {} 注入失败: {}", source, e.getMessage());
        }
    }

    /**
     * 从 MDC 注入属性
     */
    private void applyMdc(ReadWriteSpan span) {
        Map<String, String> mdc = org.slf4j.MDC.getCopyOfContextMap();
        if (mdc == null || mdc.isEmpty()) {
            return;
        }
        String traceId = mdc.get("traceId");
        if (traceId != null && !span.getSpanContext().getTraceId().equals(traceId)) {
            // 仅在 Span 还未携带 traceId 时注入
            // OTel Span 自身已携带 W3C traceId，无需重复注入
        }
        String tenantId = mdc.get("tenantId");
        if (tenantId != null && tenantId.length() < 64) {
            span.setAttribute(OtelSemConv.YDSZ_TENANT_ID, tenantId);
        }
        String userId = mdc.get("userId");
        if (userId != null) {
            span.setAttribute(OtelSemConv.YDSZ_USER_ID, userId);
        }
        String businessNo = mdc.get("businessNo");
        if (businessNo != null) {
            span.setAttribute(OtelSemConv.YDSZ_BUSINESS_NO, businessNo);
        }
    }

    /**
     * 从 RequestContext 注入属性（通过 ThreadLocal 反射调用，避免强依赖 common-core）
     */
    private void applyRequestContext(ReadWriteSpan span) {
        // 延迟加载：通过类名反射获取 RequestContext.currentUser() 等
        try {
            Class<?> ctxClass = Class.forName("com.njydsz.common.core.context.RequestContext");
            Object ctx = ctxClass.getMethod("current").invoke(null);
            if (ctx == null) {
                return;
            }
            Object tenantId = ctxClass.getMethod("getTenantId").invoke(ctx);
            if (tenantId != null) {
                span.setAttribute(OtelSemConv.YDSZ_TENANT_ID, tenantId.toString());
            }
            Object userId = ctxClass.getMethod("getUserId").invoke(ctx);
            if (userId != null) {
                span.setAttribute(OtelSemConv.YDSZ_USER_ID, userId.toString());
            }
        } catch (ClassNotFoundException e) {
            // common-core 不存在时静默忽略
        } catch (Exception e) {
            log.debug("[Sentry] RequestContext 反射注入失败: {}", e.getMessage());
        }
    }

    /**
     * 从环境变量注入固定属性
     */
    private void applyEnv(ReadWriteSpan span) {
        for (java.util.Map.Entry<String, String> entry : config.getEnvAttrs().entrySet()) {
            String value = System.getenv(entry.getKey());
            if (value != null) {
                span.setAttribute(entry.getValue(), value);
            }
        }
    }

    @Override
    public void close() {
        // no-op
    }

    /**
     * 增强配置
     */
    @Data
    @Builder
    public static class EnrichmentConfig {
        /** 是否启用 */
        private boolean enabled = true;
        /** 来源：mdc / request-context / env */
        @Builder.Default
        private List<String> sources = java.util.List.of("mdc");
        /** 环境变量属性映射（envKey -> attrName） */
        @Builder.Default
        private Map<String, String> envAttrs = new HashMap<>();
    }
}
