package com.njydsz.pmis.common.safe.filter;

import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL 娉ㄥ叆闃叉姢杩囨护鍣? * <p>
 * 妫€娴嬪苟鎷︽埅 HTTP 璇锋眰涓殑 SQL 娉ㄥ叆鏀诲嚮锛屼繚鎶ゅ簲鐢ㄥ畨鍏ㄣ€傚熀浜庢鍒欑殑"鍚彂寮?+
 * 琛屼负鐗瑰緛"妯″紡锛屽钩琛¤鎶ョ巼涓庢紡鎶ョ巼锛屾槸 MyBatis/MyBatis-Plus 绛?ORM 鍦烘櫙
 * 鐨勬渶鍚庝竴閬撻槻绾匡紙鏈€浣冲疄璺垫槸缁撳悎棰勭紪璇?SQL 闃叉娉ㄥ叆锛夈€? * </p>
 *
 * <p><b>濞佽儊妯″瀷锛?/b>鏀诲嚮鑰呴€氳繃鏌ヨ鍙傛暟銆佽〃鍗曞瓧娈点€丠TTP Header銆丣SON Body
 * 娉ㄥ叆 SQL 璇彞锛岀粫杩囪璇併€佽鍙栨晱鎰熸暟鎹€佺牬鍧忔暟鎹畬鏁存€с€?/p>
 *
 * <p><b>妫€娴嬭寖鍥达細</b></p>
 * <ul>
 *   <li>璇锋眰鍙傛暟锛圦uery String锛?/li>
 *   <li>璇锋眰澶达紙Header锛夛細User-Agent銆丷eferer銆乆-Forwarded-For</li>
 *   <li>璇锋眰浣擄紙Body锛夛細浠?JSON/XML 鏍煎紡锛屾渶澶?64KB 鎴柇妫€娴?/li>
 * </ul>
 *
 * <p><b>妫€娴嬭鍒欙紙鏀诲嚮鐗瑰緛锛夛細</b></p>
 * <ul>
 *   <li>UNION SELECT 鑱斿悎鏌ヨ娉ㄥ叆</li>
 *   <li>甯冨皵鍨嬫敞鍏ワ細OR/AND 鏁板瓧=鏁板瓧锛堝 {@code OR 1=1}锛?/li>
 *   <li>寮曞彿 + 閫昏緫杩愮畻绗︼紙濡?{@code ' OR '}锛?/li>
 *   <li>鍫嗗彔鏌ヨ锛氬紩鍙?鍒嗗彿鍚庤窡 DDL/DML</li>
 *   <li>SQL 娉ㄩ噴绗?{@code --} / {@code /*}</li>
 *   <li>瀛樺偍杩囩▼鎵ц EXEC / XP_</li>
 *   <li>鏃堕棿鐩叉敞 SLEEP / BENCHMARK / WAITFOR DELAY</li>
 *   <li>鍗遍櫓鏂囦欢鎿嶄綔 INTO OUTFILE / LOAD_FILE</li>
 *   <li>INFORMATION_SCHEMA 鎺㈡祴</li>
 * </ul>
 *
 * <p><b>閰嶇疆椤癸細</b></p>
 * <ul>
 *   <li>{@code remi.safe.sql-injection.enabled} - 鏄惁鍚敤锛堥粯璁?true锛?/li>
 *   <li>{@code remi.safe.sql-injection.block-on-detect} - 妫€娴嬪埌鏀诲嚮鏃舵槸鍚﹂樆鏂紙榛樿 true锛?/li>
 *   <li>{@code remi.safe.sql-injection.whitelist-paths} - 鎺掗櫎妫€娴嬬殑 URL 妯″紡</li>
 *   <li>{@code remi.safe.sql-injection.whitelist-params} - 鐧藉悕鍗曞弬鏁板悕锛堝叾鍊间笉妫€娴嬶級</li>
 * </ul>
 *
 * <p><b>璇姤鎺у埗锛?/b>涓嶅尮閰嶈８ SQL 鍏抽敭瀛楋紙閬垮厤姝ｅ父涓氬姟鏌ヨ"select user"绛夎璇垽锛夛紝
 * 浠呭尮閰嶇粍鍚堟敾鍑荤壒寰併€傝〃鍗曚腑鍖呭惈 SQL 瀛楁鍚嶏紙濡?ORDER BY DESC锛夋椂寤鸿鍔犲叆鐧藉悕鍗曞弬鏁般€?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class SqlInjectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlInjectionFilter.class);

    /**
     * SQL 娉ㄥ叆妫€娴嬫鍒欒〃杈惧紡
     *
     * <p>鍖归厤 SQL 娉ㄥ叆鏀诲嚮鐗瑰緛妯″紡锛堣€岄潪瑁稿叧閿瘝锛夛紝闄嶄綆璇姤鐜囷細
     * <ul>
     *   <li>UNION SELECT 鑱斿悎鏌ヨ娉ㄥ叆</li>
     *   <li>甯冨皵鍨嬫敞鍏ワ細OR/AND 鏁板瓧=鏁板瓧锛堝 OR 1=1锛?/li>
     *   <li>寮曞彿 + 閫昏緫杩愮畻绗︼紙濡?' OR '锛?/li>
     *   <li>鍫嗗彔鏌ヨ锛氬紩鍙?鍒嗗彿鍚庤窡 DDL/DML</li>
     *   <li>SQL 娉ㄩ噴绗?-- / /*</li>
     *   <li>瀛樺偍杩囩▼鎵ц EXEC / XP_</li>
     *   <li>鏃堕棿鐩叉敞 SLEEP / BENCHMARK / WAITFOR DELAY</li>
     *   <li>鍗遍櫓鏂囦欢鎿嶄綔 INTO OUTFILE / LOAD_FILE</li>
     *   <li>INFORMATION_SCHEMA 鎺㈡祴</li>
     * </ul>
     */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)" +
                    // UNION SELECT 鑱斿悎鏌ヨ娉ㄥ叆
                    "(?:\\bUNION\\s+(?:ALL\\s+)?SELECT\\b)" +
                    // 甯冨皵鍨嬫敞鍏ワ細OR/AND 鏁板瓧=鏁板瓧锛堝 OR 1=1, AND '1'='1'锛?                    "|(?:\\b(?:OR|AND)\\b\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?)" +
                    // 寮曞彿 + 閫昏緫杩愮畻绗︼紙濡?' OR ', ' AND '锛?                    "|(?:['\"]\\s*(?:OR|AND)\\s+['\"])" +
                    // 鍫嗗彔鏌ヨ锛氬紩鍙?鍒嗗彿鍚庤窡鍗遍櫓 SQL 璇彞
                    "|(?:['\";]\\s*(?:DROP|DELETE|TRUNCATE|ALTER|CREATE|INSERT|UPDATE)\\b)" +
                    // SQL 琛屾敞閲婄 --
                    "|(?:--\\s)" +
                    // SQL 鍧楁敞閲婄 /*
                    "|(?:/\\*)" +
                    // 瀛樺偍杩囩▼鎵ц
                    "|(?:\\b(?:EXEC|EXECUTE)\\s*\\()" +
                    "|(?:\\bXP_\\w+)" +
                    // 鏃堕棿鐩叉敞
                    "|(?:\\bWAITFOR\\s+DELAY\\b)" +
                    "|(?:\\bSLEEP\\s*\\()" +
                    "|(?:\\bBENCHMARK\\s*\\()" +
                    // 鍗遍櫓鏂囦欢鎿嶄綔
                    "|(?:\\bINTO\\s+(?:OUTFILE|DUMPFILE)\\b)" +
                    "|(?:\\bLOAD_FILE\\s*\\()" +
                    // 淇℃伅 schema 鎺㈡祴
                    "|(?:\\bINFORMATION_SCHEMA\\b)"
    );

    /** 璇锋眰浣撴渶澶ф娴嬮暱搴︼紝閬垮厤澶ц姹備綋瀵艰嚧鎬ц兘闂 */
    private static final int MAX_BODY_DETECT_LENGTH = 65536;

    /**
     * 鏄惁鍚敤闃绘柇妯″紡
     */
    private final boolean blockOnDetect;

    /**
     * 瀹夊叏浜嬩欢鍙戝竷鍣?     */
    private final SecurityEventPublisher eventPublisher;

    /**
     * 鐧藉悕鍗曡矾寰勶紙Ant 椋庢牸锛夛紝鍖归厤鏃惰烦杩囨娴?     */
    private final List<String> whitelistPaths;

    /**
     * 鐧藉悕鍗曞弬鏁板悕锛屽尮閰嶇殑鍙傛暟鍊艰烦杩囨娴?     */
    private final List<String> whitelistParams;

    public SqlInjectionFilter(boolean blockOnDetect, SecurityEventPublisher eventPublisher) {
        this(blockOnDetect, eventPublisher, null, null);
    }

    public SqlInjectionFilter(boolean blockOnDetect, SecurityEventPublisher eventPublisher,
                              List<String> whitelistPaths, List<String> whitelistParams) {
        this.blockOnDetect = blockOnDetect;
        this.eventPublisher = eventPublisher;
        this.whitelistPaths = filterNotBlank(whitelistPaths);
        this.whitelistParams = filterNotBlank(whitelistParams);
    }

    public SqlInjectionFilter() {
        this(true, null, null, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 鐧藉悕鍗曡矾寰勮烦杩囨娴?        if (UrlPathUtils.matchAny(whitelistPaths, request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 璇诲彇骞剁紦瀛?JSON/XML 璇锋眰浣擄紙瑙ｅ喅 InputStream 鍙兘璇诲彇涓€娆＄殑闂锛?        CachedBodyHttpServletRequest wrappedRequest = null;
        String bodyContent = null;
        String contentType = request.getContentType();
        if (contentType != null
                && (contentType.contains("application/json") || contentType.contains("application/xml"))
                && !(request instanceof CachedBodyHttpServletRequest)) {
            try {
                byte[] bodyBytes = request.getInputStream().readAllBytes();
                bodyContent = new String(bodyBytes, StandardCharsets.UTF_8);
                if (bodyContent.length() > MAX_BODY_DETECT_LENGTH) {
                    bodyContent = bodyContent.substring(0, MAX_BODY_DETECT_LENGTH);
                }
                wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);
            } catch (IOException e) {
                // 璇诲彇璇锋眰浣撳け璐ワ紝浠呮娴嬪弬鏁板拰璇锋眰澶?            }
        }

        // 妫€娴嬭姹傚弬鏁般€佽姹傚ご鍜岃姹備綋
        if (detectSqlInjection(request, bodyContent)) {
            String clientIp = getClientIp(request);
            String queryString = request.getQueryString();

            log.warn("銆怱QL娉ㄥ叆闃叉姢銆戞娴嬪埌鍙枒璇锋眰 | ip={} | uri={} | query={}",
                    clientIp, uri, queryString);

            // 鍙戝竷瀹夊叏浜嬩欢
            if (eventPublisher != null) {
                SecurityEvent event = new SecurityEvent(
                        SecurityEventType.SQL_INJECTION,
                        uri,
                        clientIp,
                        request.getHeader("User-Agent"),
                        queryString,
                        SecurityEvent.Severity.HIGH
                );
                eventPublisher.publish(event);
            }

            if (blockOnDetect) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"A04053\",\"msg\":\"璇锋眰鍖呭惈闈炴硶瀛楃\"}");
                return;
            }
        }

        // 璇锋眰浣撳凡缂撳瓨鏃朵紶閫掑寘瑁呰姹傦紝纭繚涓嬫父鍙噸澶嶈鍙?        filterChain.doFilter(wrappedRequest != null ? wrappedRequest : request, response);
    }

    /**
     * 妫€娴嬭姹備腑鏄惁鍖呭惈 SQL 娉ㄥ叆鐗瑰緛
     *
     * <p>妫€娴嬭寖鍥达細
     * <ul>
     *   <li>璇锋眰鍙傛暟锛堥€愬€兼娴嬶紝璺宠繃鐧藉悕鍗曞弬鏁帮級</li>
     *   <li>鍏抽敭璇锋眰澶达紙User-Agent, Referer, X-Forwarded-For锛?/li>
     *   <li>璇锋眰浣擄紙鐢辫皟鐢ㄦ柟璇诲彇鍚庝紶鍏ワ紝浠?JSON/XML 绫诲瀷锛?/li>
     * </ul>
     *
     * @param request     HTTP 璇锋眰
     * @param bodyContent 宸茶鍙栫殑璇锋眰浣撳唴瀹癸紙鍙负 null锛?     * @return true 琛ㄧず妫€娴嬪埌 SQL 娉ㄥ叆
     */
    private boolean detectSqlInjection(HttpServletRequest request, String bodyContent) {
        // 妫€娴嬭姹傚弬鏁帮紙閫愬€兼娴嬶紝璺宠繃鐧藉悕鍗曞弬鏁板悕锛?        Map<String, String[]> paramMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            if (whitelistParams.contains(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (StringUtils.hasText(value) && SQL_INJECTION_PATTERN.matcher(value).find()) {
                    return true;
                }
            }
        }

        // 妫€娴嬪叧閿姹傚ご
        String[] headersToCheck = {"User-Agent", "Referer", "X-Forwarded-For"};
        for (String headerName : headersToCheck) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue) && SQL_INJECTION_PATTERN.matcher(headerValue).find()) {
                return true;
            }
        }

        // 妫€娴嬭姹備綋
        if (StringUtils.hasText(bodyContent) && SQL_INJECTION_PATTERN.matcher(bodyContent).find()) {
            return true;
        }

        return false;
    }

    /**
     * 杩囨护鎺夊垪琛ㄤ腑鐨勭┖鐧藉瓧绗︿覆锛堝鐞?@Value 绌洪粯璁ゅ€煎満鏅級
     */
    private static List<String> filterNotBlank(List<String> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>(list.size());
        for (String item : list) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    /**
     * 鑾峰彇瀹㈡埛绔湡瀹?IP
     *
     * @param request HTTP 璇锋眰
     * @return 瀹㈡埛绔?IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 缂撳瓨璇锋眰浣撶殑 HTTP 璇锋眰鍖呰鍣紝瑙ｅ喅 InputStream 鍙兘璇诲彇涓€娆＄殑闂銆?     *
     * <p>鍦?Filter 涓鍙栬姹備綋杩涜瀹夊叏妫€娴嬪悗锛岄€氳繃姝ゅ寘瑁呭櫒灏嗙紦瀛樼殑瀛楄妭鏁扮粍
     * 閲嶆柊鎻愪緵缁欎笅娓?Filter/Servlet锛岀‘淇濅笟鍔′唬鐮佷粛鍙甯歌鍙栬姹備綋銆?     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody != null ? cachedBody : new byte[0];
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            final ByteArrayInputStream bis = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bis.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }

                @Override
                public int read() throws IOException {
                    return bis.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
