ackage com.njydsz.pmis.common.safe.ratelimit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.alert.SafeAlertProperties;
import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventPublisher;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.util.url.UrlPathUtils;

/**
 * 基于 Redis 令牌桶的全局限流 Filter。
 *
 * <p>支持按 IP / 用户 / 全局三种维度进行限流。
 * 使用 Redis + Lua 实现滑动窗口限流，保证分布式环境下的精确限流。
 * 继承 {@link OncePerRequestFilter}，确保每次请求只执行一次。
 *
 * <p><b>限流维度：</b>
 * <ul>
 *   <li>IP - 按客户端 IP 限流（默认）</li>
 *   <li>USER - 按登录用户限流，从请求头 X-User-Id 获取</li>
 *   <li>GLOBAL - 全局共享限流</li>
 * </ul>
 *
 * <p><b>实现原理：</b>
 * 使用 Redis ZSet 实现滑动窗口算法，将每个请求的时间戳作为 score 存入 ZSet，
 * 通过统计窗口内的请求数判断是否超限。
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

    /** JSON 序列化器，用于生成限流响应体 */
    // JsonUtils as JSON engine

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
                log.warn("【安全模块】请求被限流 | key={}, uri={}, ip={}", rateLimitKey, request.getRequestURI(), getClientIp(request));
                publishRateLimitEvent(request);
                writeRateLimitResponse(response);
                return;
            }
        } catch (Exception e) {
            // Redis 异常时 fail-open：限流是保护性措施而非安全性措施，放行请求不中断服务
            log.warn("【安全模块】Redis 限流不可用，放行请求 | key={}, uri={}, error={}",
                    rateLimitKey, request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 写入限流响应（JSON 格式 BaseResponse，HTTP 429）
     */
    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<Void> body = BaseResponse.error(
                UnifiedExceptionCode.RATE_LIMIT.getCode(), properties.getMessage());
        response.getWriter().write(YdszJson.toJson(body));
    }

    /**
     * 根据配置的维度解析限流 Key。
     *
     * <p>为减少碰撞，IP 维度组合使用 IP + 用户ID（如有）+ URI；
     * USER 维度组合使用 用户ID + URI。
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
                // 组合 IP + 用户ID（如有）+ URI，减少 NAT 出口共享 IP 导致的误限
                StringBuilder key = new StringBuilder(properties.getIpKey()).append(clientIp);
                if (StringUtils.hasText(userId)) {
                    key.append(":").append(userId);
                }
                key.append(":").append(uri);
                return key.toString();
        }
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>获取直连 IP（request.getRemoteAddr()，不可伪造）</li>
     *   <li>如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 X-Forwarded-For / X-Real-IP</li>
     *   <li>否则直接使用直连 IP</li>
     * </ol>
     * 这样可以防止外部客户端伪造 X-Forwarded-For 绕过 IP 限流。
     */
    private String getClientIp(HttpServletRequest request) {
        String directIp = request.getRemoteAddr();
        // 如果直连 IP 是可信代理（本地回环或内网私有地址），才信任 X-Forwarded-For
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
     * 判断 IP 是否为可信代理。
     *
     * <p>可信代理包括：
     * <ul>
     *   <li>本地回环：127.0.0.0/8, ::1</li>
     *   <li>内网私有：10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16</li>
     *   <li>Docker 默认网段：172.17.0.0/16</li>
     * </ul>
     */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        // 本地回环
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        // 内网私有地址（简单判断，CIDR 精确匹配可后续增强）
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
                // 解析失败，不视为可信代理
            }
        }
        return false;
    }

    /**
     * 判断请求路径是否需要排除限流。
     */
    private boolean isExcluded(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return UrlPathUtils.matchAny(excludes, servletPath);
    }

    /**
     * 发布限流安全事件。
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
