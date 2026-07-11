package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.security.SensitiveOperationEvent;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Objects;

/**
 * 敏感操作二次认证 AOP
 *
 * <p>校验流程：
 * <ol>
 *   <li>从请求头 {@code X-Re-Auth-Token} 读取 token</li>
 *   <li>Redis 校验 token 是否存在 + 未过期</li>
 *   <li>通过则继续并发布审计事件；失败则抛出 FORBIDDEN</li>
 * </ol>
 *
 * <p>token 颁发由 {@code TwoFactorService} 完成。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequireReAuthAspect {

    /** 二次认证请求头名称 */
    private static final String HEADER = "X-Re-Auth-Token";
    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "pmis:reauth:";

    /** Redis 操作模板 */
    private final StringRedisTemplate redisTemplate;
    /** Spring 事件发布器 */
    private final ApplicationEventPublisher publisher;

    /**
     * 环绕增强：校验二次认证 token，通过后执行目标方法并发布审计事件
     *
     * @param pjp 连接点
     * @param ann 二次认证注解
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, RequireReAuth ann) throws Throwable {
        LoginUser user = SecurityContext.getCurrentOrNull();
        if (user == null) {
            throw new BizException(BizErrorCode.UNAUTHORIZED);
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
        String token = request == null ? null : request.getHeader(HEADER);
        if (!StringUtils.hasText(token)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "敏感操作需要二次认证: " + ann.name());
        }
        String redisKey = KEY_PREFIX + ann.code() + ":" + user.getUserId() + ":" + token;
        String op = redisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(op)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "二次认证 token 无效或已过期");
        }
        // 一次性 token，使用后立即失效
        redisTemplate.delete(redisKey);

        Object result = pjp.proceed();
        try {
            publishEvent(pjp, ann, user, request);
        } catch (Exception e) {
            log.warn("[ReAuth] 发布审计事件失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 构造并发布敏感操作审计事件。
     *
     * @param pjp     连接点
     * @param ann     二次认证注解
     * @param user    当前登录用户
     * @param request HTTP 请求（可为 null）
     */
    private void publishEvent(ProceedingJoinPoint pjp, RequireReAuth ann, LoginUser user, HttpServletRequest request) {
        SensitiveOperationEvent event = SensitiveOperationEvent.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .operationCode(ann.code())
                .operationName(ann.name())
                .reAuthMethod("PASSWORD")
                .verifiedAt(System.currentTimeMillis())
                .expireAt(System.currentTimeMillis() + ann.ttlSeconds() * 1000L)
                .traceId(TraceIdUtil.get())
                .clientIp(request == null ? "" : clientIp(request))
                .tenantId(TenantContext.getTenantId())
                .build();
        publisher.publishEvent(event);
    }

    /**
     * 解析客户端真实 IP。
     *
     * <p>优先级：X-Forwarded-For（取第一个）&gt; X-Real-IP &gt; remoteAddr，兜底 "unknown"。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 字符串
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > -1 ? ip.substring(0, idx) : ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return Objects.requireNonNullElse(request.getRemoteAddr(), "unknown");
    }

    /**
     * 颁发二次认证 token，写入 Redis 并设置过期时间
     *
     * @param operationCode 操作码
     * @param userId        用户 ID
     * @param ttlSeconds    有效期（秒）
     * @return 二次认证 token
     */
    public String issueToken(String operationCode, String userId, int ttlSeconds) {
        String token = SnowflakeIdGenerator.nextIdStr();
        String key = KEY_PREFIX + operationCode + ":" + userId + ":" + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
        return token;
    }
}
