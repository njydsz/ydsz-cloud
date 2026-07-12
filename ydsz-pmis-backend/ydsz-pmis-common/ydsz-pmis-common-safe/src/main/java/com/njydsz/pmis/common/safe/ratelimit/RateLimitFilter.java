package com.njydsz.pmis.common.safe.ratelimit;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.util.url.UrlPathUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
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

/**
 * 鍩轰簬 Redis 浠ょ墝妗剁殑鍏ㄥ眬闄愭祦 Filter銆?
 *
 * <p>鏀寔鎸?IP / 鐢ㄦ埛 / 鍏ㄥ眬涓夌缁村害杩涜闄愭祦銆?
 * 浣跨敤 Redis + Lua 瀹炵幇婊戝姩绐楀彛闄愭祦锛屼繚璇佸垎甯冨紡鐜涓嬬殑绮剧‘闄愭祦銆?
 * 缁ф壙 {@link OncePerRequestFilter}锛岀‘淇濇瘡娆¤姹傚彧鎵ц涓€娆°€?
 *
 * <p><b>闄愭祦缁村害锛?/b>
 * <ul>
 *   <li>IP - 鎸夊鎴风 IP 闄愭祦锛堥粯璁わ級</li>
 *   <li>USER - 鎸夌櫥褰曠敤鎴烽檺娴侊紝浠庤姹傚ご X-User-Id 鑾峰彇</li>
 *   <li>GLOBAL - 鍏ㄥ眬鍏变韩闄愭祦</li>
 * </ul>
 *
 * <p><b>瀹炵幇鍘熺悊锛?/b>
 * 浣跨敤 Redis ZSet 瀹炵幇婊戝姩绐楀彛绠楁硶锛屽皢姣忎釜璇锋眰鐨勬椂闂存埑浣滀负 score 瀛樺叆 ZSet锛?
 * 閫氳繃缁熻绐楀彛鍐呯殑璇锋眰鏁板垽鏂槸鍚﹁秴闄愩€?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final List<String> DEFAULT_EXCLUDES = new ArrayList<>();

    static {
        DEFAULT_EXCLUDES.add("/error");
        DEFAULT_EXCLUDES.add("/favicon.ico");
        DEFAULT_EXCLUDES.add("/actuator/**");
    }

    private static final String LUA_RATE_LIMIT_SCRIPT =
            "local key = KEYS[1]\n" +
            "local window = tonumber(ARGV[1])\n" +
            "local limit = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local windowStart = now - window\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count < limit then\n" +
            "    redis.call('ZADD', key, now, now .. '-' .. math.random(100000))\n" +
            "    redis.call('EXPIRE', key, window + 1)\n" +
            "    return 1\n" +
            "end\n" +
            "return 0";

    private final RateLimitProperties properties;
    private final RedisService redisService;
    private final List<String> excludes;
    private final SecurityEventPublisher eventPublisher;
    private final SafeAlertProperties alertProperties;

    /** JSON 搴忓垪鍖栧櫒锛岀敤浜庣敓鎴愰檺娴佸搷搴斾綋 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RateLimitFilter(RateLimitProperties properties,
                           RedisService redisService,
                           SecurityEventPublisher eventPublisher,
                           SafeAlertProperties alertProperties) {
        this.properties = properties;
        this.redisService = redisService;
        this.excludes = properties.getExcludes() == null || properties.getExcludes().isEmpty()
                ? new ArrayList<>(DEFAULT_EXCLUDES)
                : new ArrayList<>(properties.getExcludes());
        this.eventPublisher = eventPublisher;
        this.alertProperties = alertProperties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain)
            throws IOException, ServletException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rateLimitKey = resolveRateLimitKey(request);
        if (rateLimitKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowSeconds = 1;
        int limit = properties.getLimitPerSecond();
        long now = System.currentTimeMillis();

        try {
            Long result = redisService.executeScript(
                    LUA_RATE_LIMIT_SCRIPT,
                    List.of(rateLimitKey),
                    Long.class,
                    windowSeconds, limit, now
            );
            boolean allowed = result != null && result == 1L;

            if (!allowed) {
                log.warn("銆愬畨鍏ㄦā鍧椼€戣姹傝闄愭祦 | key={}, uri={}, ip={}", rateLimitKey, request.getRequestURI(), getClientIp(request));
                publishRateLimitEvent(request);
                writeRateLimitResponse(response);
                return;
            }
        } catch (Exception e) {
            // Redis 寮傚父鏃?fail-open锛氶檺娴佹槸淇濇姢鎬ф帾鏂借€岄潪瀹夊叏鎬ф帾鏂斤紝鏀捐璇锋眰涓嶄腑鏂湇鍔?
            log.warn("銆愬畨鍏ㄦā鍧椼€慠edis 闄愭祦涓嶅彲鐢紝鏀捐璇锋眰 | key={}, uri={}, error={}",
                    rateLimitKey, request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 鍐欏叆闄愭祦鍝嶅簲锛圝SON 鏍煎紡 BaseResponse锛孒TTP 429锛?
     */
    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<Void> body = BaseResponse.error(
                UnifiedExceptionCode.RATE_LIMIT.getCode(), properties.getMessage());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }

    /**
     * 鏍规嵁閰嶇疆鐨勭淮搴﹁В鏋愰檺娴?Key銆?
     *
     * <p>涓哄噺灏戠鎾烇紝IP 缁村害缁勫悎浣跨敤 IP + 鐢ㄦ埛ID锛堝鏈夛級+ URI锛?
     * USER 缁村害缁勫悎浣跨敤 鐢ㄦ埛ID + URI銆?
     */
    private String resolveRateLimitKey(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);
        String userId = request.getHeader("X-User-Id");

        RateLimitProperties.Dimension dimension = properties.getDimension();
        switch (dimension) {
            case USER:
                if (StringUtils.hasText(userId)) {
                    return properties.getUserKey() + userId + ":" + uri;
                }
                return properties.getIpKey() + clientIp + ":" + uri;
            case GLOBAL:
                return properties.getGlobalKey();
            case IP:
            default:
                // 缁勫悎 IP + 鐢ㄦ埛ID锛堝鏈夛級+ URI锛屽噺灏?NAT 鍑哄彛鍏变韩 IP 瀵艰嚧鐨勮闄?
                StringBuilder key = new StringBuilder(properties.getIpKey()).append(clientIp);
                if (StringUtils.hasText(userId)) {
                    key.append(":").append(userId);
                }
                key.append(":").append(uri);
                return key.toString();
        }
    }

    /**
     * 鑾峰彇瀹㈡埛绔湡瀹?IP銆?
     *
     * <p>鍒ゆ柇閫昏緫锛?
     * <ol>
     *   <li>鑾峰彇鐩磋繛 IP锛坮equest.getRemoteAddr()锛屼笉鍙吉閫狅級</li>
     *   <li>濡傛灉鐩磋繛 IP 鏄彲淇′唬鐞嗭紙鏈湴鍥炵幆鎴栧唴缃戠鏈夊湴鍧€锛夛紝鎵嶄俊浠?X-Forwarded-For / X-Real-IP</li>
     *   <li>鍚﹀垯鐩存帴浣跨敤鐩磋繛 IP</li>
     * </ol>
     * 杩欐牱鍙互闃叉澶栭儴瀹㈡埛绔吉閫?X-Forwarded-For 缁曡繃 IP 闄愭祦銆?
     */
    private String getClientIp(HttpServletRequest request) {
        String directIp = request.getRemoteAddr();
        // 濡傛灉鐩磋繛 IP 鏄彲淇′唬鐞嗭紙鏈湴鍥炵幆鎴栧唴缃戠鏈夊湴鍧€锛夛紝鎵嶄俊浠?X-Forwarded-For
        if (directIp != null && isTrustedProxy(directIp)) {
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int index = ip.indexOf(',');
                if (index != -1) {
                    return ip.substring(0, index).trim();
                }
                return ip.trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip.trim();
            }
        }
        return directIp != null && !directIp.isEmpty() ? directIp : "0.0.0.0";
    }

    /**
     * 鍒ゆ柇 IP 鏄惁涓哄彲淇′唬鐞嗐€?
     *
     * <p>鍙俊浠ｇ悊鍖呮嫭锛?
     * <ul>
     *   <li>鏈湴鍥炵幆锛?27.0.0.0/8, ::1</li>
     *   <li>鍐呯綉绉佹湁锛?0.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
     *   <li>Docker 榛樿缃戞锛?72.17.0.0/16</li>
     * </ul>
     */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 鏈湴鍥炵幆
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        // 鍐呯綉绉佹湁鍦板潃锛堢畝鍗曞垽鏂紝CIDR 绮剧‘鍖归厤鍙悗缁寮猴級
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            // 172.16.0.0 - 172.31.255.255
            try {
                int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return true;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // 瑙ｆ瀽澶辫触锛屼笉瑙嗕负鍙俊浠ｇ悊
            }
        }
        return false;
    }

    /**
     * 鍒ゆ柇璇锋眰璺緞鏄惁闇€瑕佹帓闄ら檺娴併€?
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }

    /**
     * 鍙戝竷闄愭祦瀹夊叏浜嬩欢銆?
     */
    private void publishRateLimitEvent(HttpServletRequest request) {
        if (eventPublisher == null || alertProperties == null || !alertProperties.isEnabled()) {
            return;
        }
        SecurityEvent event = new SecurityEvent(
                SecurityEventType.RATE_LIMIT_TRIGGERED,
                request.getRequestURI(),
                getClientIp(request),
                request.getHeader("User-Agent"),
                "Rate limit exceeded for key: " + resolveRateLimitKey(request),
                SecurityEvent.Severity.MEDIUM
        );
        eventPublisher.publish(event);
    }
}
