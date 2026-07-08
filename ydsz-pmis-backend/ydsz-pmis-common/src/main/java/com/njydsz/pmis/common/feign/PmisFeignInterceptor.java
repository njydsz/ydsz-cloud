package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.util.TraceIdUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * PMIS Feign 统一可观测性拦截器（批次 19 P2-2 落地，P0-5 增强跨服务上下文透传）
 *
 * <p>职责：
 * <ol>
 *   <li>透传 traceId（从 MDC 取）→ 让跨服务调用链可追踪</li>
 *   <li>透传 W3C {@code traceparent} 标准头 → 兼容标准分布式追踪系统</li>
 *   <li>透传用户上下文头（X-User-Id / X-Username / X-User-Roles / X-User-Permissions）
 *       → 下游服务可获取调用方身份，避免重复鉴权失败</li>
 *   <li>透传内部签名头（X-Internal-Sig / X-Internal-Ts）→ 下游可校验请求合法性</li>
 *   <li>透传 Accept-Language → 下游 i18n 消息与调用方保持一致</li>
 *   <li>注入 X-Request-Source（PMIS-{serviceName}）→ 区分调用方</li>
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
 *   <li>[TraceIdFilter](../filter/TraceIdFilter.java) - 请求入口 TraceId 注入</li>
 *   <li>[AuthGlobalFilter](../../../gateway/filter/AuthGlobalFilter.java) - 网关鉴权 + 头注入</li>
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
    /** W3C Trace Context 标准头 */
    private static final String TRACEPARENT_HEADER = "traceparent";
    /** 调用来源标识的 Header 名称 */
    private static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
    /** Accept-Language 头 */
    private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";

    /** 需要从入站请求透传到出站 Feign 请求的内部头清单 */
    private static final List<String> PROPAGATED_HEADERS = List.of(
            CommonConstants.HEADER_USER_ID,
            CommonConstants.HEADER_USERNAME,
            CommonConstants.HEADER_USER_DEPT,
            CommonConstants.HEADER_USER_ROLES,
            CommonConstants.HEADER_USER_PERMISSIONS,
            CommonConstants.HEADER_INTERNAL_SIG,
            CommonConstants.HEADER_INTERNAL_TS,
            TRACEPARENT_HEADER,
            ACCEPT_LANGUAGE_HEADER
    );

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
     * 在 Feign 请求发出前注入可观测性 Header + 用户上下文头
     *
     * <p>1) 透传 traceId：Brave {@code TracingClient} 会自动注入 {@code traceparent}/{@code b3}
     *          header；本拦截器仅补充兼容性 {@code X-Trace-Id}，便于老网关/前端关联
     * <p>2) 透传用户上下文头：从当前 HttpServletRequest 读取网关注入的 X-User-* 系列头，
     *          透传到下游 Feign 调用，确保下游服务可获取调用方身份
     * <p>3) 注入 X-Request-Source（PMIS-{serviceName}）：标识调用方
     *
     * @param template Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        // 1) 补充 X-Trace-Id（兼容性 header；Brave 已自动注入 traceparent/b3）
        String traceId = TraceIdUtil.get();
        if (traceId == null || traceId.isEmpty()) {
            // 当前线程无 span（如异步调用）：生成兜底 traceId
            traceId = TraceIdUtil.generate();
            TraceIdUtil.set(traceId);
        }
        template.header(TRACE_ID_HEADER, traceId);

        // 2) 从当前 HttpServletRequest 透传用户上下文头与内部签名头
        propagateIncomingHeaders(template);

        // 3) 注入来源标识
        String serviceName = env.getProperty("spring.application.name", "pmis-unknown");
        template.header(REQUEST_SOURCE_HEADER, "PMIS-" + serviceName);

        log.debug("[Feign] {} {} traceId={} source=PMIS-{}",
                template.method(), template.feignTarget().url(), traceId, serviceName);
    }

    /**
     * 从当前线程绑定的 HttpServletRequest 中读取网关注入的内部头，
     * 透传到出站 Feign 请求，确保下游服务可获取调用方身份与签名。
     *
     * <p>当当前线程无 HttpServletRequest 绑定（如异步线程、定时任务）时静默跳过，
     * 由调用方自行通过 SecurityContext 或其他机制传递身份。
     *
     * @param template Feign 请求模板
     */
    private void propagateIncomingHeaders(RequestTemplate template) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // 异步线程或非 HTTP 上下文：无法获取入站请求头，跳过
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return;
        }
        for (String headerName : PROPAGATED_HEADERS) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isEmpty()) {
                template.header(headerName, value);
            }
        }
    }
}
