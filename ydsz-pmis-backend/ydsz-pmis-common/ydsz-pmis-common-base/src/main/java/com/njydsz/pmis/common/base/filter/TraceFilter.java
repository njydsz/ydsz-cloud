package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.core.context.RequestContext;
import com.njydsz.pmis.common.core.trace.TraceIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 閾捐矾杩借釜杩囨护鍣? *
 * <p>鍔熻兘璇存槑锛? * <ul>
 *   <li>鐢熸垚鎴栨彁鍙?traceId</li>
 *   <li>灏?traceId 娉ㄥ叆 MDC锛屼緵鏃ュ織妗嗘灦浣跨敤</li>
 *   <li>灏?traceId 瀛樺叆 RequestContext</li>
 *   <li>鍦ㄥ搷搴斿ご涓繑鍥?traceId</li>
 * </ul>
 *
 * <p>鎵ц椤哄簭锛欻IGH_PRECEDENCE + 10锛岀‘淇濆湪涓氬姟閫昏緫涔嬪墠鎵ц
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class TraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 鎻愬彇鎴栫敓鎴?traceId
            String traceId = extractOrGenerateTraceId(request);

            // 娉ㄥ叆 MDC
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // 瀛樺叆 RequestContext
            RequestContext.setTraceId(traceId);

            // 璁剧疆鍝嶅簲澶?            response.setHeader(TRACE_ID_HEADER, traceId);

            // 缁х画澶勭悊
            filterChain.doFilter(request, response);
        } finally {
            // 娓呯悊 MDC锛堢敱 RequestContextCleanupFilter 缁熶竴娓呯悊锛?            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 鎻愬彇鎴栫敓鎴?traceId
     *
     * @param request HTTP 璇锋眰
     * @return traceId
     */
    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // 浼樺厛浠庤姹傚ご鎻愬彇
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 濡傛灉璇锋眰澶翠腑娌℃湁锛屽垯鐢熸垚鏂扮殑
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdGenerator.generate();
        }

        return traceId;
    }
}
