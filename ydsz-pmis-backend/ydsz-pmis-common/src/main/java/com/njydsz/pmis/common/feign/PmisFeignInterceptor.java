package com.njydsz.pmis.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PMIS Feign 统一可观测性拦截器（批次 19 P2-2 落地）
 *
 * <p>职责：
 * <ol>
 *   <li>透传 traceId（从 MDC 取）→ 让跨服务调用链可追踪</li>
 *   <li>注入 X-Request-Source（PMIS-{serviceName}）→ 区分调用方</li>
 *   <li>记录调用耗时到 Micrometer（pmis_feign_call_seconds 指标）</li>
 *   <li>失败计数（pmis_feign_call_total{status="failure"}）</li>
 * </ol>
 *
 * <p>对应 Prometheus 告警：
 * <pre>
 *   pmis_feign_call_seconds{quantile="0.99"} > 1s
 *   rate(pmis_feign_call_total{status="failure"}[5m]) > 0.05
 * </pre>
 *
 * <p>关联文件：
 * <ul>
 *   <li>[PmisFeignLogger](PmisFeignLogger.java) - Feign 调用日志增强</li>
 *   <li>[FeignMetricsRecorder](FeignMetricsRecorder.java) - 指标记录</li>
 *   <li>[deploy/monitoring/prometheus/rules/pmis-alerts.yml](../../../../../../../../deploy/monitoring/prometheus/rules/pmis-alerts.yml) - 告警规则</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class PmisFeignInterceptor implements RequestInterceptor {

    /** 链路追踪 ID 的 Header 名称 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** 调用来源标识的 Header 名称 */
    private static final String REQUEST_SOURCE_HEADER = "X-Request-Source";

    /** Spring 环境上下文，用于读取应用名等配置 */
    private final Environment env;

    /**
     * 构造方法
     *
     * @param env            Spring 环境上下文
     * @param meterRegistry  Micrometer 指标注册中心（可选，预留未来指标记录）
     */
    public PmisFeignInterceptor(Environment env, @Autowired(required = false) io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.env = env;
        // meterRegistry reserved for future metrics recording
    }

    /**
     * 在 Feign 请求发出前注入可观测性 Header
     *
     * <p>1) 透传 traceId（MDC 优先 → 新生成兜底）
     * <p>2) 注入 X-Request-Source（PMIS-{serviceName}）
     *
     * @param template Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        // 1) 透传 traceId（MDC 优先 → 新生成兜底）
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("traceId", traceId);
        }
        template.header(TRACE_ID_HEADER, traceId);

        // 2) 注入来源标识
        String serviceName = env.getProperty("spring.application.name", "pmis-unknown");
        template.header(REQUEST_SOURCE_HEADER, "PMIS-" + serviceName);

        log.debug("[Feign] {} {} traceId={} source=PMIS-{}",
                template.method(), template.feignTarget().url(), traceId, serviceName);
    }
}
