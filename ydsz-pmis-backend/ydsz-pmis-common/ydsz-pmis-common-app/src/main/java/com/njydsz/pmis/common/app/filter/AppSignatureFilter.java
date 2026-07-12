package com.njydsz.pmis.common.app.filter;

import com.njydsz.pmis.common.util.security.DigestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * App 绔姹傜鍚嶉獙璇佽繃婊ゅ櫒
 *
 * <p>鍩轰簬 HMAC-SHA256 绠楁硶楠岃瘉璇锋眰绛惧悕鐨勫畬鏁存€у拰鏉ユ簮鍚堟硶鎬с€? *
 * <p><b>绛惧悕绠楁硶锛?/b>
 * <pre>
 * signature = HMAC-SHA256(
 *     key = appSecret,
 *     data = method + "|" + uri + "|" + timestamp + "|" + nonce
 * )
 * </pre>
 *
 * <p><b>蹇呴渶璇锋眰澶达細</b>
 * <ul>
 *   <li>{@code X-App-Sign} - 瀹㈡埛绔绠楃殑绛惧悕鍊硷紙Hex 鏍煎紡锛?/li>
 *   <li>{@code X-App-Timestamp} - 璇锋眰鏃堕棿鎴筹紙姣锛?/li>
 *   <li>{@code X-App-Nonce} - 闅忔満瀛楃涓诧紙鐢ㄤ簬闃查噸鏀撅級</li>
 * </ul>
 *
 * <p><b>閰嶇疆寮€鍏筹細</b>
 * <ul>
 *   <li>{@code remi.app.signature.enabled=false} 鍙鐢ㄧ鍚嶉獙璇?/li>
 *   <li>{@code remi.app.signature.app-secret} 閰嶇疆绛惧悕瀵嗛挜锛堝繀濉級</li>
 *   <li>{@code remi.app.signature.timestamp-tolerance} 鏃堕棿鎴冲宸紙姣锛岄粯璁?5 鍒嗛挓锛?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class AppSignatureFilter extends OncePerRequestFilter {

    /** 绛惧悕澶村悕绉帮紝瀹㈡埛绔皢璁＄畻鍚庣殑 Hex 瀛楃涓插啓鍏ユ澶?*/
    private static final String HEADER_SIGNATURE = "X-App-Sign";
    /** 鏃堕棿鎴冲ご鍚嶇О锛堟绉掞級 */
    private static final String HEADER_TIMESTAMP = "X-App-Timestamp";
    /** 闅忔満瀛楃涓插ご鍚嶇О锛岀敤浜庨槻閲嶆斁 */
    private static final String HEADER_NONCE = "X-App-Nonce";

    /** HMAC-SHA256 绛惧悕瀵嗛挜锛岀敱 {@link AppSignatureProperties#getAppSecret()} 娉ㄥ叆 */
    private final String appSecret;
    /** 鏃堕棿鎴冲宸紙姣锛夛紝鐢?{@link AppSignatureProperties#getTimestampTolerance()} 娉ㄥ叆 */
    private final long timestampTolerance;

    /**
     * 鏋勯€犳柟娉?     *
     * @param appSecret          HMAC-SHA256 绛惧悕瀵嗛挜
     * @param timestampTolerance 鏃堕棿鎴冲宸紙姣锛?     */
    public AppSignatureFilter(String appSecret, long timestampTolerance) {
        this.appSecret = appSecret;
        this.timestampTolerance = timestampTolerance;
    }

    /**
     * 鎵ц绛惧悕鏍￠獙鐨勬牳蹇冮€昏緫
     *
     * <p>鏍￠獙娴佺▼锛?     * <ol>
     *   <li>鎻愬彇绛惧悕鐩稿叧璇锋眰澶达紝缂哄け鍒欒繑鍥?400</li>
     *   <li>瑙ｆ瀽鏃堕棿鎴冲苟鏍￠獙鏄惁鍦ㄥ宸寖鍥村唴</li>
     *   <li>鎸?{@code method|uri|timestamp|nonce} 璁＄畻鏈熸湜绛惧悕</li>
     *   <li>浣跨敤鎭掑畾鏃堕棿姣旇緝瀹㈡埛绔鍚嶄笌鏈嶅姟绔绠楃鍚?/li>
     * </ol>
     *
     * <p>浠绘剰涓€姝ユ牎楠屽け璐ュ潎鐩存帴鍝嶅簲閿欒锛屼笉鍐嶆斁琛岃嚦鍚庣画杩囨护鍣ㄣ€?     *
     * @param request     褰撳墠 HTTP 璇锋眰
     * @param response    褰撳墠 HTTP 鍝嶅簲
     * @param filterChain 杩囨护鍣ㄩ摼
     * @throws ServletException 閫忎紶 Servlet 寮傚父
     * @throws IOException      閫忎紶 IO 寮傚父
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 鎻愬彇绛惧悕鐩稿叧璇锋眰澶?        String signature = request.getHeader(HEADER_SIGNATURE);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);

        // 楠岃瘉蹇呴渶澶?        if (signature == null || timestampStr == null || nonce == null) {
            log.warn("銆怉pp绛惧悕楠岃瘉銆戠己灏戝繀闇€璇锋眰澶?| uri={} | sign={} | timestamp={} | nonce={}",
                    request.getRequestURI(), signature, timestampStr, nonce);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing signature headers");
            return;
        }

        // 楠岃瘉鏃堕棿鎴?        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("銆怉pp绛惧悕楠岃瘉銆戞椂闂存埑鏍煎紡閿欒 | uri={} | timestamp={}", request.getRequestURI(), timestampStr);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid timestamp format");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > timestampTolerance) {
            log.warn("銆怉pp绛惧悕楠岃瘉銆戞椂闂存埑杩囨湡 | uri={} | timestamp={} | currentTime={} | tolerance={}",
                    request.getRequestURI(), timestamp, currentTime, timestampTolerance);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Timestamp expired");
            return;
        }

        // 鏋勫缓绛惧悕瀛楃涓?        String method = request.getMethod();
        String uri = request.getRequestURI();
        String signData = method + "|" + uri + "|" + timestamp + "|" + nonce;

        // 璁＄畻鏈熸湜绛惧悕
        String expectedSignature = DigestUtils.hmacSha256Hex(signData, appSecret);

        // 楠岃瘉绛惧悕锛堟椂搴忔亽瀹氭瘮杈冿級
        if (!DigestUtils.verifyDigestHex(expectedSignature, signature)) {
            log.warn("銆怉pp绛惧悕楠岃瘉銆戠鍚嶄笉鍖归厤 | uri={} | expected={} | actual={}",
                    request.getRequestURI(), expectedSignature, signature);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return;
        }

        log.debug("銆怉pp绛惧悕楠岃瘉銆戦€氳繃 | uri={}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
