package com.njydsz.pmis.common.safe.filter;

import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.safe.xss.EscapeUtils;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.FilterChain;

/**
 * XSS 瀹夊叏闃叉姢杩囨护鍣?
 * <p>
 * 鍏ㄥ眬 HTTP 璇锋眰鍙傛暟涓?JSON 璇锋眰浣撶殑 XSS 鏀诲嚮杩囨护銆?
 * 鍩轰簬 Spring {@link OncePerRequestFilter} 瀹炵幇锛屽湪璇锋眰杩涘叆 Controller 涔嬪墠瀹屾垚鍙傛暟娓呮礂銆?
 * </p>
 *
 * <p><b>濞佽儊妯″瀷锛?/b>鏀诲嚮鑰呴€氳繃鏌ヨ鍙傛暟銆佽〃鍗曞瓧娈点€丣SON Body 娉ㄥ叆 JavaScript / HTML 鐗囨锛?
 * 瀹炵幇 cookie 绐冨彇銆侀挀楸笺€乁I 浼銆侀敭鐩樿褰曠瓑 XSS 鏀诲嚮銆?/p>
 *
 * <p><b>鏍稿績鐗规€э細</b></p>
 * <ul>
 *   <li>鍏ㄥ眬杩囨护锛氫竴娆￠厤缃紝鍏ㄥ眬鐢熸晥</li>
 *   <li>鏅鸿兘鎺掗櫎锛氭敮鎸?Ant 椋庢牸璺緞鍖归厤锛屾帓闄ゆ棤闇€杩囨护鐨勭鐐?/li>
 *   <li>JSON 鏀寔锛氬彲澶勭悊 JSON 璇锋眰浣撶殑 XSS 鏀诲嚮</li>
 *   <li>OncePerRequest锛氬熀浜?Spring 杩囨护鍣紝纭繚姣忔璇锋眰鍙墽琛屼竴娆?/li>
 *   <li>瀹夊叏鍛婅锛氭娴嬪埌 XSS 鏀诲嚮鏃跺彂甯冨畨鍏ㄤ簨浠?/li>
 * </ul>
 *
 * <p><b>杩囨护鑼冨洿锛?/b></p>
 * <ul>
 *   <li>鎺掗櫎璺緞鍒楄〃涓殑绔偣涓嶈繃婊?/li>
 *   <li>鍏朵粬鎵€鏈夎姹傜殑鍙傛暟鍜?JSON Body 閮戒細缁忚繃 XSS 杩囨护</li>
 * </ul>
 *
 * <p><b>鎬ц兘褰卞搷锛?/b>姣忔璇锋眰閮戒細鎵ц鍙傛暟閬嶅巻鍜屽瓧绗︿覆鏇挎崲锛屽楂?QPS 鎺ュ彛闇€璇勪及
 * 鎬ц兘寮€閿€銆侸SON Body 鍦ㄥ唴瀛樹腑缂撳瓨锛?0MB 涓婇檺锛夛紝涓嶅簲浣滀负澶ф枃浠朵笂浼犳帴鍙ｇ殑鍏滃簳銆?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see XssHttpServletRequestWrapper
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class XssFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(XssFilter.class);

    /**
     * 榛樿 XSS 鎺掗櫎璺緞鍒楄〃
     */
    private static final List<String> DEFAULT_EXCLUDES = new ArrayList<>();

    static {
        DEFAULT_EXCLUDES.add("/error");
        DEFAULT_EXCLUDES.add("/favicon.ico");
        DEFAULT_EXCLUDES.add("/actuator/**");
    }

    /** 鎺掗櫎璺緞鍒楄〃锛圓nt 椋庢牸锛?*/
    private final List<String> excludes;
    /** 瀹夊叏浜嬩欢鍙戝竷鍣紙鍙负 null锛?*/
    private final SecurityEventPublisher eventPublisher;
    /** 瀹夊叏鍛婅閰嶇疆锛堝彲涓?null锛?*/
    private final SafeAlertProperties alertProperties;

    /**
     * 榛樿鏋勯€犲櫒锛氫娇鐢ㄩ粯璁ゆ帓闄よ矾寰勶紝涓嶅彂甯冨畨鍏ㄤ簨浠?
     */
    public XssFilter() {
        this.excludes = new ArrayList<>(DEFAULT_EXCLUDES);
        this.eventPublisher = null;
        this.alertProperties = null;
    }

    /**
     * 鑷畾涔夋帓闄よ矾寰勬瀯閫犲櫒
     *
     * @param excludes 鎺掗櫎璺緞鍒楄〃锛坣ull 鏃朵娇鐢ㄩ粯璁わ級
     */
    public XssFilter(List<String> excludes) {
        this.excludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        if (this.excludes.isEmpty()) {
            this.excludes.addAll(DEFAULT_EXCLUDES);
        }
        this.eventPublisher = null;
        this.alertProperties = null;
    }

    /**
     * 瀹屾暣鏋勯€犲櫒
     *
     * @param excludes         鎺掗櫎璺緞鍒楄〃
     * @param eventPublisher   瀹夊叏浜嬩欢鍙戝竷鍣紙鍙负 null锛?
     * @param alertProperties   瀹夊叏鍛婅閰嶇疆锛堝彲涓?null锛?
     */
    public XssFilter(List<String> excludes, SecurityEventPublisher eventPublisher,
                     SafeAlertProperties alertProperties) {
        this.excludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        if (this.excludes.isEmpty()) {
            this.excludes.addAll(DEFAULT_EXCLUDES);
        }
        this.eventPublisher = eventPublisher;
        this.alertProperties = alertProperties;
    }

    /**
     * 杩囨护鍣ㄦ牳蹇冮€昏緫
     * <ol>
     *   <li>鎺掗櫎璺緞鐩存帴鏀捐</li>
     *   <li>JSON 璇锋眰浣撳厛缂撳瓨骞舵墽琛?XSS 鏀诲嚮妫€娴嬶紝鍛戒腑鏃跺彂甯冨畨鍏ㄤ簨浠?/li>
     *   <li>闈?JSON 璇锋眰閬嶅巻鍙傛暟鍋?XSS 妫€娴?/li>
     *   <li>浣跨敤 {@link XssHttpServletRequestWrapper} 鍖呰璇锋眰锛岃嚜鍔ㄦ竻娲楀弬鏁?/li>
     * </ol>
     *
     * @param request     HTTP 璇锋眰
     * @param response    HTTP 鍝嶅簲
     * @param filterChain 杩囨护鍣ㄩ摼
     * @throws IOException      IO 寮傚父
     * @throws ServletException Servlet 寮傚父
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws IOException, ServletException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 瀵逛簬 JSON 璇锋眰锛岀紦瀛樿姹備綋浠ユ敮鎸?XSS 妫€娴嬪拰鍚庣画 Wrapper 璇诲彇
        CachedRequestBody cachedBody = null;
        if (isJsonRequest(request)) {
            try {
                byte[] bodyBytes = request.getInputStream().readAllBytes();
                cachedBody = new CachedRequestBody(bodyBytes);
                // 浣跨敤缂撳瓨鐨勮姹備綋杩涜 XSS 妫€娴?
                if (cachedBody.hasText() && EscapeUtils.containsXSS(cachedBody.getText())) {
                    publishEvent(request, cachedBody.getText());
                }
            } catch (IOException e) {
                // 璇诲彇澶辫触鏃?fail-closed锛氳褰曟棩蹇楀苟鎶涘嚭锛岄伩鍏嶅悗缁?Wrapper 璇诲彇宸叉秷璐圭殑 InputStream 鍑洪敊
                log.warn("XSS 杩囨护鍣ㄨ鍙栬姹備綋澶辫触 | URI: {} | 娑堟伅: {}", request.getRequestURI(), e.getMessage());
                throw e;
            }
        } else {
            // 闈?JSON 璇锋眰鍙娴嬪弬鏁?
            detectAndPublishXssEvent(request);
        }

        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(request, cachedBody);
        filterChain.doFilter(xssRequest, response);
    }

    /**
     * 妫€娴嬮潪 JSON 璇锋眰鍙傛暟涓殑 XSS 鏀诲嚮锛屽苟鍙戝竷瀹夊叏浜嬩欢
     * <p>浠呮壂鎻忛潪 JSON 璇锋眰銆侸SON 璇锋眰浣撶殑妫€娴嬪湪 {@link #doFilterInternal} 涓畬鎴愩€?
     */
    private void detectAndPublishXssEvent(HttpServletRequest request) {
        if (eventPublisher == null || alertProperties == null || !alertProperties.isEnabled()) {
            return;
        }

        // 妫€鏌ヨ姹傚弬鏁?
        String[] paramNames = request.getParameterMap().keySet().toArray(new String[0]);
        for (String name : paramNames) {
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (String value : values) {
                    if (EscapeUtils.containsXSS(value)) {
                        publishEvent(request, value);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 鍙戝竷 XSS 鏀诲嚮瀹夊叏浜嬩欢
     *
     * @param request HTTP 璇锋眰
     * @param payload 瑙﹀彂妫€娴嬬殑鏀诲嚮杞借嵎
     */
    private void publishEvent(HttpServletRequest request, String payload) {
        SecurityEvent event = new SecurityEvent(
                SecurityEventType.XSS_ATTACK,
                request.getRequestURI(),
                getClientIp(request),
                request.getHeader("User-Agent"),
                payload,
                SecurityEvent.Severity.HIGH
        );
        eventPublisher.publish(event);
    }

    /**
     * 鍒ゆ柇璇锋眰鏄惁涓?JSON 璇锋眰
     *
     * @param request HTTP 璇锋眰
     * @return Content-Type 鍖呭惈 {@code application/json} 鏃惰繑鍥?true
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
        return StringUtils.hasText(contentType) &&
               contentType.toLowerCase().contains("application/json");
    }

    /**
     * 缂撳瓨鐨勮姹備綋锛岀敤浜庢敮鎸?XSS 妫€娴嬪拰鍚庣画 Wrapper 閲嶅璇诲彇
     */
    static class CachedRequestBody {
        /** 鍘熷瀛楄妭鏁扮粍 */
        private final byte[] bytes;
        /** 鍘熷瀛楃涓诧紙UTF-8 缂栫爜锛?*/
        private final String text;

        CachedRequestBody(byte[] bytes) {
            this.bytes = bytes;
            this.text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        byte[] getBytes() {
            return bytes;
        }

        String getText() {
            return text;
        }

        boolean hasText() {
            return StringUtils.hasText(text);
        }
    }

    /**
     * 鑾峰彇瀹㈡埛绔湡瀹?IP
     * <p>濮旀墭缁?{@code ServletUtils.getClientIp}锛屽凡澶勭悊鍙嶅悜浠ｇ悊澶淬€?
     *
     * @param request HTTP 璇锋眰
     * @return 瀹㈡埛绔?IP
     */
    private String getClientIp(HttpServletRequest request) {
        return com.njydsz.pmis.common.util.http.ServletUtils.getClientIp(request);
    }

    /**
     * 鍒ゆ柇璇锋眰璺緞鏄惁闇€瑕佹帓闄?XSS 杩囨护
     *
     * @param request HTTP 璇锋眰
     * @return 闇€瑕佹帓闄よ繑鍥?true
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }
}
