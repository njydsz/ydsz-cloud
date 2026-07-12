package com.njydsz.pmis.common.safe.filter;

import com.njydsz.pmis.common.safe.config.SecurityHeaderProperties;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 瀹夊叏鍝嶅簲澶磋繃婊ゅ櫒锛圵eb/App 鍏变韩鎶借薄鍩虹被锛? *
 * @author Marvin Lee
 * @version 3.5.0
 */
@Slf4j
@RequiredArgsConstructor
public class BaseSecurityHeaderFilter extends OncePerRequestFilter {

    private final SecurityHeaderProperties properties;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled() || isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        addSecurityHeaders(response);
        filterChain.doFilter(request, response);
        log.debug("瀹夊叏鍝嶅簲澶村凡娣诲姞鍒拌姹?{} 鐨勫搷搴斾腑", request.getRequestURI());
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        addHeaderIfNotEmpty(response, "X-Frame-Options", properties.getFrameOptions());
        addHeaderIfNotEmpty(response, "X-Content-Type-Options", properties.getContentTypeOptions());
        addHeaderIfNotEmpty(response, "X-XSS-Protection", properties.getXssProtection());
        addHeaderIfNotEmpty(response, "Strict-Transport-Security", properties.getHsts());
        addHeaderIfNotEmpty(response, "Content-Security-Policy", properties.getCsp());
        addHeaderIfNotEmpty(response, "Referrer-Policy", properties.getReferrerPolicy());
        addHeaderIfNotEmpty(response, "Permissions-Policy", properties.getPermissionsPolicy());
    }

    private void addHeaderIfNotEmpty(HttpServletResponse response, String headerName, String headerValue) {
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            response.setHeader(headerName, headerValue);
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
