package com.njydsz.pmis.common.base.filter;

import com.njydsz.pmis.common.base.config.BaseSecurityHeadersProperties;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 瀹夊叏鍝嶅簲澶磋繃婊ゅ櫒
 *
 * <p>涓?HTTP 鍝嶅簲娣诲姞瀹夊叏鐩稿叧鐨勫ご閮紝闃叉甯歌 Web 瀹夊叏濞佽儊锛? * <ul>
 *   <li>X-Content-Type-Options: nosniff - 闃叉 MIME 绫诲瀷鍡呮帰</li>
 *   <li>X-Frame-Options: DENY - 闃叉鐐瑰嚮鍔寔</li>
 *   <li>X-XSS-Protection: 1; mode=block - 鍚敤娴忚鍣?XSS 杩囨护</li>
 *   <li>Strict-Transport-Security - 寮哄埗 HTTPS</li>
 *   <li>Content-Security-Policy - 鍐呭瀹夊叏绛栫暐</li>
 *   <li>Referrer-Policy - 鎺у埗 Referer 澶?/li>
 * </ul>
 *
 * <p>鎵€鏈夊ご閮ㄥ€煎潎閫氳繃 {@link BaseSecurityHeadersProperties} 閰嶇疆锛屾敮鎸佹帓闄ょ壒瀹氳矾寰勩€? *
 * <p>鎵ц椤哄簭锛歿@code Ordered.HIGHEST_PRECEDENCE + 20}锛岀‘淇濆湪涓氬姟閫昏緫涔嬪墠鎵ц銆? *
 * <p><b>涓?safe 妯″潡鐨勫叧绯伙細</b>
 * 鏈繃婊ゅ櫒涓?base 妯″潡鐨勫厹搴曞疄鐜帮紝浠呭湪鏈紩鍏?safe/web/app 妯″潡鏃剁敓鏁堛€? * 褰撻」鐩腑瀛樺湪 web/app 妯″潡鏃讹紝瀹夊叏鍝嶅簲澶寸敱 safe 妯″潡鐨?{@code SecurityHeaderFilter} 缁熶竴绠＄悊銆? *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    private static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

    private final BaseSecurityHeadersProperties properties;

    /**
     * 鏋勯€犲畨鍏ㄥ搷搴斿ご杩囨护鍣?     *
     * @param properties 瀹夊叏澶撮儴閰嶇疆灞炴€?     */
    public SecurityHeadersFilter(BaseSecurityHeadersProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!isExcluded(request)) {
            addSecurityHeaders(response);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 娣诲姞瀹夊叏鍝嶅簲澶?     *
     * @param response HTTP 鍝嶅簲瀵硅薄
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        addHeaderIfNotEmpty(response, HEADER_X_CONTENT_TYPE_OPTIONS, properties.getContentTypeOptions());
        addHeaderIfNotEmpty(response, HEADER_X_FRAME_OPTIONS, properties.getFrameOptions());
        addHeaderIfNotEmpty(response, HEADER_X_XSS_PROTECTION, properties.getXssProtection());
        addHeaderIfNotEmpty(response, HEADER_STRICT_TRANSPORT_SECURITY, properties.getHsts());
        addHeaderIfNotEmpty(response, HEADER_CONTENT_SECURITY_POLICY, properties.getCsp());
        addHeaderIfNotEmpty(response, HEADER_REFERRER_POLICY, properties.getReferrerPolicy());
    }

    private void addHeaderIfNotEmpty(HttpServletResponse response, String headerName, String headerValue) {
        if (headerValue != null && !headerValue.trim().isEmpty()) {
            response.setHeader(headerName, headerValue);
        }
    }

    /**
     * 鍒ゆ柇璇锋眰璺緞鏄惁闇€瑕佹帓闄ゅ畨鍏ㄥご閮?     *
     * @param request HTTP 璇锋眰
     * @return 鏄惁闇€瑕佹帓闄?     */
    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
