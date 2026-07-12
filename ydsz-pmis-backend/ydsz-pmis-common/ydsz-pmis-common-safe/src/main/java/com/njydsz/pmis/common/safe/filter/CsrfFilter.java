package com.njydsz.pmis.common.safe.filter;

import com.njydsz.pmis.common.safe.config.CsrfProperties;
import com.njydsz.pmis.common.safe.csrf.CsrfToken;
import com.njydsz.pmis.common.safe.csrf.CsrfTokenRepository;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * CSRF 闃叉姢杩囨护鍣?
 * <p>
 * 闃叉璺ㄧ珯璇锋眰浼€狅紙CSRF锛夋敾鍑伙紝鍩轰簬 Token 鏈哄埗銆係ynchronizer Token Pattern
 * 鏄綋鍓嶆渶鎴愮啛鐨?CSRF 闃插尽鏂规锛圤WASP 鎺ㄨ崘锛夈€?
 * </p>
 *
 * <p><b>濞佽儊妯″瀷锛?/b>鐢ㄦ埛宸茬櫥褰曠洰鏍囩珯鐐癸紝鏀诲嚮鑰呴€氳繃绗笁鏂圭珯鐐硅瀵肩敤鎴锋祻瑙堝櫒
 * 鍙戦€佽法鍩熻姹傦紙鎼哄甫鐩爣绔欑偣鐨?Cookie锛夛紝瀹屾垚鏈巿鏉冪殑鍐欐搷浣溿€?/p>
 *
 * <p><b>闃叉姢鍘熺悊锛?/b></p>
 * <ul>
 *   <li>鏈嶅姟绔敓鎴愬敮涓€鐨?CSRF 浠ょ墝骞跺啓鍏?Cookie/Response Header</li>
 *   <li>瀹㈡埛绔湪璇锋眰涓惡甯︿护鐗岋紙Header 鎴?Parameter锛?/li>
 *   <li>鏈嶅姟绔獙璇佷护鐗屾湁鏁堟€э紙鏀诲嚮鑰呮棤娉曢€氳繃璺ㄥ煙鑴氭湰鑾峰彇 Token锛?/li>
 *   <li>Token 涓€娆℃€т娇鐢紝楠岃瘉鍚庣珛鍗崇敓鎴愭柊 Token 闃查噸鏀?/li>
 * </ul>
 *
 * <p><b>浣跨敤鏂瑰紡锛?/b></p>
 * <pre>{@code
 * 1. 鍓嶇鍦ㄩ〉闈㈠姞杞芥椂浠?Cookie/Header 鑾峰彇 CSRF 浠ょ墝
 * 2. 鍙戣捣璇锋眰鏃跺湪 Header 鎴?Parameter 涓惡甯︿护鐗?
 * 3. 鏈嶅姟绔嚜鍔ㄩ獙璇佷护鐗屾湁鏁堟€?
 * }</pre>
 *
 * <p><b>鎬ц兘褰卞搷锛?/b>姣忔闈?GET 璇锋眰閮戒細璋冪敤涓€娆?Redis 鏍￠獙銆傚缓璁敓浜х幆澧?
 * 鍚敤 Redis 瀛樺偍鑰岄潪鍐呭瓨瀛樺偍锛屽惁鍒欏瀹炰緥涓?Token 涓嶄竴鑷淬€?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see CsrfProperties
 * @see CsrfTokenRepository
 */
public class CsrfFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CsrfFilter.class);

    /** CSRF Cookie 鍚嶇О */
    private static final String CSRF_TOKEN_COOKIE = "CSRF-TOKEN";

    /** CSRF 閰嶇疆灞炴€?*/
    private final CsrfProperties properties;
    /** CSRF 浠ょ墝瀛樺偍搴擄紙Redis / 鍐呭瓨锛?*/
    private final CsrfTokenRepository tokenRepository;

    /**
     * 鏋勯€?CSRF 闃叉姢杩囨护鍣?
     *
     * @param properties      CSRF 閰嶇疆灞炴€?
     * @param tokenRepository CSRF 浠ょ墝瀛樺偍搴?
     */
    public CsrfFilter(CsrfProperties properties, CsrfTokenRepository tokenRepository) {
        this.properties = properties;
        this.tokenRepository = tokenRepository;
    }

    /**
     * 杩囨护鍣ㄦ牳蹇冮€昏緫
     * <ol>
     *   <li>绂佺敤 / 鎺掗櫎璺緞锛氱洿鎺ユ斁琛?/li>
     *   <li>GET 璇锋眰锛氱敓鎴愭柊 Token锛屽啓鍏?Cookie 鍜?Response Header</li>
     *   <li>HEAD / OPTIONS 璇锋眰锛氭斁琛岋紙涓嶆惡甯︿笟鍔¤涔夛級</li>
     *   <li>鍏朵粬璇锋眰锛氶獙璇?Token锛岄獙璇侀€氳繃鍚庡埛鏂?Token 闃查噸鏀?/li>
     * </ol>
     *
     * @param httpRequest  HTTP 璇锋眰
     * @param httpResponse HTTP 鍝嶅簲
     * @param chain        杩囨护鍣ㄩ摼
     * @throws IOException      IO 寮傚父
     * @throws ServletException Servlet 寮傚父
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest httpRequest, @NonNull HttpServletResponse httpResponse,
                                    @NonNull FilterChain chain)
            throws IOException, ServletException {

        if (!properties.isEnabled()) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        if (isExcluded(httpRequest)) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        if (HttpMethod.GET.matches(httpRequest.getMethod())) {
            handleGetRequest(httpRequest, httpResponse, chain);
            return;
        }

        if (HttpMethod.HEAD.matches(httpRequest.getMethod()) || HttpMethod.OPTIONS.matches(httpRequest.getMethod())) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        // Origin/Referer 鏍￠獙锛堢浜岄亾闃茬嚎锛屽湪 Token 鏍￠獙涔嬪墠鎵ц锛?
        if (properties.isCheckOrigin() && !validateOrigin(httpRequest)) {
            logger.warn("CSRF Origin 鏍￠獙澶辫触 | URI: {} | Origin: {} | Referer: {}",
                    httpRequest.getRequestURI(),
                    httpRequest.getHeader("Origin"),
                    httpRequest.getHeader("Referer"));
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":403,\"message\":\"Cross-origin request not allowed\"}");
            return;
        }

        if (!validateCsrfToken(httpRequest)) {
            logger.warn("CSRF 楠岃瘉澶辫触: {}", httpRequest.getRequestURI());
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":403,\"message\":\"CSRF token validation failed\"}");
            return;
        }

        // 楠岃瘉閫氳繃鍚庡埛鏂?CSRF token锛岄槻姝㈠悓涓€ token 琚噸鏀炬敾鍑?
        String newToken = (String) httpRequest.getAttribute("NEW_CSRF_TOKEN");
        if (newToken != null) {
            Cookie cookie = buildCsrfCookie(newToken, httpRequest);
            httpResponse.addCookie(cookie);
            httpResponse.setHeader(properties.getTokenHeader(), newToken);
        }

        chain.doFilter(httpRequest, httpResponse);
    }

    private void handleGetRequest(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String sessionId = getSessionId(request);

        CsrfToken token = tokenRepository.createToken(sessionId);

        Cookie cookie = buildCsrfCookie(token.getToken(), request);
        response.addCookie(cookie);

        response.setHeader(properties.getTokenHeader(), token.getToken());

        chain.doFilter(request, response);
    }

    /**
     * 鏋勫缓 CSRF Cookie锛岀粺涓€ Cookie 瀹夊叏灞炴€ч厤缃?
     *
     * @param token   CSRF 浠ょ墝鍊?
     * @param request HTTP 璇锋眰锛堢敤浜庡姩鎬佸垽鏂?Secure 鏍囧織锛?
     * @return 閰嶇疆濂藉畨鍏ㄥ睘鎬х殑 Cookie
     */
    private Cookie buildCsrfCookie(String token, HttpServletRequest request) {
        Cookie cookie = new Cookie(CSRF_TOKEN_COOKIE, token);
        cookie.setPath("/");
        // 鍓嶇闇€瑕佽鍙?CSRF Token锛屼笉鑳借缃?HttpOnly
        cookie.setHttpOnly(false);
        // Secure 鏍囧織锛氶厤缃紭鍏堬紝鏈厤缃椂鏍规嵁璇锋眰鍗忚鍔ㄦ€佸喅瀹?
        Boolean cookieSecure = properties.getCookieSecure();
        if (cookieSecure != null) {
            cookie.setSecure(cookieSecure);
        } else {
            cookie.setSecure(request.isSecure());
        }
        cookie.setMaxAge((int) properties.getExpirationSeconds());
        setSameSiteAttribute(cookie, properties.getSameSite());
        return cookie;
    }

    /**
     * 鏍￠獙璇锋眰鏉ユ簮锛圤rigin/Referer锛夛紝鎷掔粷璺ㄧ珯璇锋眰
     *
     * <p>鏍￠獙閫昏緫锛?
     * <ol>
     *   <li>浼樺厛鏍￠獙 Origin 澶达紝涓虹┖鏃跺洖閫€鍒?Referer 澶?/li>
     *   <li>濡傛灉閰嶇疆浜?allowedOrigins锛屾牎楠屾潵婧愭槸鍚﹀湪鐧藉悕鍗曚腑</li>
     *   <li>濡傛灉鏈厤缃?allowedOrigins锛屾牎楠屾潵婧愭槸鍚︿笌璇锋眰 Host 鍚屾簮</li>
     * </ol>
     *
     * @param request HTTP 璇锋眰
     * @return 鍚屾簮鎴栧湪鐧藉悕鍗曚腑杩斿洖 true锛岃法绔欒繑鍥?false
     */
    private boolean validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        // Origin 涓虹┖鏃跺洖閫€鍒?Referer
        if (origin == null || origin.isEmpty()) {
            String referer = request.getHeader("Referer");
            if (referer == null || referer.isEmpty()) {
                // 鏃?Origin 鍜?Referer锛屽彲鑳芥槸鍚屾簮璇锋眰鎴栭潪娴忚鍣ㄥ鎴风锛屾斁琛?
                return true;
            }
            origin = extractOrigin(referer);
        }

        List<String> allowedOrigins = properties.getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            // 閰嶇疆浜嗙櫧鍚嶅崟锛屾牎楠屾槸鍚﹀尮閰?
            for (String allowed : allowedOrigins) {
                if (matchesOrigin(origin, allowed)) {
                    return true;
                }
            }
            return false;
        }

        // 鏈厤缃櫧鍚嶅崟锛屾牎楠屾槸鍚﹀悓婧愶紙Origin 鐨?host:port 涓庤姹?Host 涓€鑷达級
        String requestHost = request.getServerName() + ":" + request.getServerPort();
        return isSameOrigin(origin, requestHost, request.getScheme(), request.getServerName(), request.getServerPort());
    }

    /**
     * 浠?Referer URL 涓彁鍙?Origin锛坰cheme://host:port锛?
     */
    private String extractOrigin(String referer) {
        int pathIdx = referer.indexOf('/', referer.indexOf("://") + 3);
        return pathIdx > 0 ? referer.substring(0, pathIdx) : referer;
    }

    /**
     * 鏍￠獙 Origin 鏄惁鍖归厤鍏佽鐨勬ā寮忥紙鏀寔閫氶厤绗︼級
     */
    private boolean matchesOrigin(String origin, String allowed) {
        if (allowed.contains("*")) {
            // 閫氶厤绗﹀尮閰嶏紝濡?https://*.example.com
            String pattern = allowed.replace(".", "\\.").replace("*", ".*");
            return origin.matches(pattern);
        }
        return origin.equals(allowed);
    }

    /**
     * 鏍￠獙鏄惁鍚屾簮
     */
    private boolean isSameOrigin(String origin, String requestHost, String scheme, String serverName, int serverPort) {
        // 瑙ｆ瀽 Origin锛歴cheme://host:port
        try {
            java.net.URI originUri = new java.net.URI(origin);
            String originHost = originUri.getHost();
            int originPort = originUri.getPort();
            if (originPort == -1) {
                // 榛樿绔彛澶勭悊
                if ("https".equals(originUri.getScheme())) {
                    originPort = 443;
                } else if ("http".equals(originUri.getScheme())) {
                    originPort = 80;
                }
            }
            return originUri.getScheme().equalsIgnoreCase(scheme)
                    && originHost != null && originHost.equalsIgnoreCase(serverName)
                    && originPort == serverPort;
        } catch (java.net.URISyntaxException e) {
            logger.debug("Origin 瑙ｆ瀽澶辫触: {}", origin);
            return false;
        }
    }

    private boolean validateCsrfToken(HttpServletRequest request) {
        String sessionId = getSessionId(request);
        if (sessionId == null) {
            return false;
        }

        String tokenFromHeader = request.getHeader(properties.getTokenHeader());
        String tokenFromParameter = request.getParameter(properties.getTokenParameter());

        String token = tokenFromHeader != null ? tokenFromHeader : tokenFromParameter;

        if (token == null || token.isEmpty()) {
            logger.debug("CSRF token not found in request");
            return false;
        }

        boolean valid = tokenRepository.validateToken(token, sessionId);
        if (valid) {
            // 楠岃瘉閫氳繃鍚庣敓鎴愭柊 token锛岄€氳繃 request 灞炴€т紶閫掔粰 doFilterInternal 鍐欏叆 response
            CsrfToken newToken = tokenRepository.createToken(sessionId);
            request.setAttribute("NEW_CSRF_TOKEN", newToken.getToken());
        }
        return valid;
    }

    private String getSessionId(HttpServletRequest request) {
        String sessionIdHeader = properties.getSessionIdHeader();
        if (sessionIdHeader != null && !sessionIdHeader.isEmpty()) {
            String sessionId = request.getHeader(sessionIdHeader);
            if (sessionId != null && !sessionId.isEmpty()) {
                return sessionId;
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    private boolean isExcluded(HttpServletRequest request) {
        List<String> excludes = properties.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }

    private void setSameSiteAttribute(jakarta.servlet.http.Cookie cookie, String value) {
        try {
            cookie.setAttribute("SameSite", value);
        } catch (NoSuchMethodError e) {
            // Servlet 5.0 浠ヤ笅鐗堟湰涓嶆敮鎸?setAttribute锛屽拷鐣?
        }
    }

}
