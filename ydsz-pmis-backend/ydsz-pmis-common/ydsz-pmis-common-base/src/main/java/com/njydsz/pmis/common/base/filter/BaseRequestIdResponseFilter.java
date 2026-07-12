package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.base.config.BaseTraceProperties;
import com.njydsz.pmis.common.core.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 璇锋眰ID鍝嶅簲澶磋繃婊ゅ櫒锛圵eb/App 鍏变韩锛?
 *
 * <p>瀛愮被瑕嗙洊 {@link #resolveRequestId(HttpServletRequest)} 鎻愪緵涓嶅悓鐨?ID 鏉ユ簮銆?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public abstract class BaseRequestIdResponseFilter extends OncePerRequestFilter {

    protected static final String HEADER_REQUEST_ID = HeaderConstants.X_REQUEST_ID;

    private final BaseTraceProperties traceProperties;

    protected BaseRequestIdResponseFilter(BaseTraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (traceProperties.isResponseHeaderEnabled()) {
                String requestId = resolveRequestId(request);
                if (requestId != null && !requestId.isBlank()) {
                    response.setHeader(HEADER_REQUEST_ID, requestId);
                    log.debug("璇锋眰ID [{}] 宸叉坊鍔犲埌璇锋眰 {} 鐨勫搷搴斿ご涓?, requestId, request.getRequestURI());
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            afterFilter(request, response);
        }
    }

    /**
     * 瀛愮被瑕嗙洊姝ゆ柟娉曟彁渚涘叿浣撶殑璇锋眰 ID 瑙ｆ瀽閫昏緫
     */
    protected abstract String resolveRequestId(HttpServletRequest request);

    /**
     * 璇锋眰缁撴潫鍚庢竻鐞嗭紙榛樿绌哄疄鐜帮紝瀛愮被鍙鐩栵級
     */
    protected void afterFilter(HttpServletRequest request, HttpServletResponse response) {
        // 榛樿绌哄疄鐜?
    }
}
