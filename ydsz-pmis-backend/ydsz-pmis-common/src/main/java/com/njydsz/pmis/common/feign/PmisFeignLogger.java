package com.njydsz.pmis.common.feign;

import feign.Logger;
import feign.Request;
import feign.Response;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * PMIS Feign 日志 + 指标记录器（批次 19 P2-2 落地）
 *
 * <p>实现 feign.Logger 的 BASIC 风格日志记录，只记录 traceId + 耗时 + 状态码，
 * 避免 FULL 级别把请求体也打到日志（敏感信息泄露风险）。
 *
 * <p>同时通过 Micrometer 上报：
 * <ul>
 *   <li>{@code pmis_feign_call_seconds} - 调用耗时直方图</li>
 *   <li>{@code pmis_feign_call_total{status="success|failure"}} - 调用计数</li>
 * </ul>
 *
 * <p>Feign 13.x 适配说明:
 * <ul>
 *   <li>抽象方法 {@code log(String, String, Object...)} 由父类默认实现, 本类重写为
 *       静默 (由 logRequest/logAndRebufferResponse/logIOException 负责实际输出,
 *       避免 BASIC 级别下的重复日志)</li>
 *   <li>{@code logIOException} 返回 {@link IOException} 而非 void</li>
 *   <li>{@code Request.header(String)} 已废弃, 改用 {@code headers().get(name)}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class PmisFeignLogger extends Logger {

    private final MeterRegistry meterRegistry;

    public PmisFeignLogger(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Logger.Level pmisFeignLogLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * 重写抽象方法: 静默, 由 logRequest/logAndRebufferResponse 输出, 避免重复日志
     */
    @Override
    protected void log(String configKey, String format, Object... args) {
        // no-op: BASIC 级别下, 我们只关心 request/response 摘要, 不要全量日志
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        if (logLevel.ordinal() >= Level.BASIC.ordinal()) {
            String traceId = firstHeader(request, "X-Trace-Id");
            log.info("[Feign Request] target={} method={} url={} traceId={}",
                    configKey, request.httpMethod(), request.url(), traceId);
        }
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response,
                                              long elapsedTime) throws IOException {
        boolean success = response.status() >= 200 && response.status() < 500;
        recordMetric(configKey, success, elapsedTime);

        if (logLevel.ordinal() >= Level.BASIC.ordinal()) {
            String traceId = firstHeader(response.request(), "X-Trace-Id");
            log.info("[Feign Response] target={} status={} elapsed={}ms traceId={}",
                    configKey, response.status(), elapsedTime, traceId);
        }
        return response;
    }

    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        recordMetric(configKey, false, elapsedTime);
        log.error("[Feign IOException] target={} elapsed={}ms msg={}",
                configKey, elapsedTime, ioe.getMessage());
        return ioe;
    }

    private String firstHeader(Request request, String name) {
        if (request == null || request.headers() == null) {
            return "";
        }
        Collection<String> values = request.headers().get(name);
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.iterator().next();
    }

    private void recordMetric(String target, boolean success, long elapsedTime) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("target", target, "status", success ? "success" : "failure");
        Timer.builder("pmis_feign_call_seconds")
                .description("Feign 跨服务调用耗时")
                .tags(tags)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(elapsedTime, TimeUnit.MILLISECONDS);
    }
}
