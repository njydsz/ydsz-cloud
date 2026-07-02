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

    /** Micrometer 指标注册中心，可为 null（无监控环境时） */
    private final MeterRegistry meterRegistry;

    /**
     * 构造方法
     *
     * @param meterRegistry Micrometer 指标注册中心（可选）
     */
    public PmisFeignLogger(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 声明 Feign 日志级别为 BASIC
     *
     * @return Feign 日志级别
     */
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

    /**
     * 记录 Feign 请求日志（BASIC 级别及以上输出）
     *
     * @param configKey Feign 配置键（target）
     * @param logLevel  当前日志级别
     * @param request   Feign 请求对象
     */
    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        if (logLevel.ordinal() >= Level.BASIC.ordinal()) {
            String traceId = firstHeader(request, "X-Trace-Id");
            log.info("[Feign Request] target={} method={} url={} traceId={}",
                    configKey, request.httpMethod(), request.url(), traceId);
        }
    }

    /**
     * 记录 Feign 响应日志并上报耗时指标
     *
     * @param configKey   Feign 配置键（target）
     * @param logLevel    当前日志级别
     * @param response    Feign 响应对象
     * @param elapsedTime 调用耗时（毫秒）
     * @return 原响应对象（重新缓冲后）
     * @throws IOException 读取响应体时可能抛出
     */
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

    /**
     * 记录 Feign 调用异常日志并上报失败指标
     *
     * @param configKey   Feign 配置键（target）
     * @param logLevel    当前日志级别
     * @param ioe         触发的 IOException
     * @param elapsedTime 调用耗时（毫秒）
     * @return 原异常对象（向上抛出）
     */
    @Override
    protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
        recordMetric(configKey, false, elapsedTime);
        log.error("[Feign IOException] target={} elapsed={}ms msg={}",
                configKey, elapsedTime, ioe.getMessage());
        return ioe;
    }

    /**
     * 获取请求中指定 Header 的首个值
     *
     * @param request Feign 请求对象
     * @param name    Header 名称
     * @return Header 值，不存在时返回空字符串
     */
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

    /**
     * 上报 Feign 调用耗时与状态指标到 Micrometer
     *
     * @param target      Feign 配置键（target），作为 tag
     * @param success     是否调用成功
     * @param elapsedTime 调用耗时（毫秒）
     */
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
