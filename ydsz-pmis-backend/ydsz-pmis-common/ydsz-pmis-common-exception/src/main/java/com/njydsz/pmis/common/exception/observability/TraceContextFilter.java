package com.njydsz.pmis.common.exception.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 娉ㄥ叆杩囨护鍣? *
 * <p>鍦ㄨ姹傚叆鍙ｅ鑷姩浠?header 鎻愬彇鎴栫敓鎴?traceId锛屽啓鍏?MDC 鍜屽搷搴?header銆? * 閰嶅悎 logback 鐨?{@code %X{traceId}} 閰嶇疆鍙湪鎵€鏈夋棩蹇椾腑鑷姩鎼哄甫 traceId銆? *
 * <p><b>澶勭悊娴佺▼锛?/b>
 * <ol>
 *   <li>浠庤姹?header 鎻愬彇 traceId锛坽@code X-Trace-Id} / {@code X-B3-TraceId}锛?/li>
 *   <li>鑻ユ湭鎻愬彇鍒板垯鐢熸垚鏂扮殑 traceId</li>
 *   <li>鍐欏叆 SLF4J MDC锛屾敞鍏ュ搷搴?header</li>
 *   <li>璇锋眰缁撴潫鍚庢竻鐞?MDC锛堢嚎绋嬫睜澶嶇敤锛?/li>
 * </ol>
 *
 * <p><b>婵€娲绘潯浠讹細</b>闇€鍦?{@code AutoConfiguration.imports} 涓敞鍐岋紝
 * 鎴栧湪涓氬姟绯荤粺涓€氳繃 {@code @Component} 寮曞叆銆? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String existing = request.getHeader(TraceContext.HEADER_TRACE_ID);
        if (existing == null || existing.isEmpty()) {
            existing = request.getHeader(TraceContext.HEADER_B3_TRACE_ID);
        }
        String traceId = TraceContext.extractOrGenerate(existing);
        String spanId = TraceContext.generate();

        TraceContext.setContext(traceId, spanId);
        response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
