package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 二次认证切面 — 校验 X-Re-Auth-Token 有效性。
 *
 * <p>拦截标注了 {@link RequireReAuth} 的方法，从请求头提取二次认证 token，
 * 在 Redis 中验证其有效性及操作编码匹配。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Aspect
@Component
public class RequireReAuthAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireReAuthAspect.class);

    private static final String HEADER_NAME = "X-Re-Auth-Token";
    private static final String REDIS_PREFIX = "re_auth:";

    private final StringRedisTemplate redisTemplate;

    public RequireReAuthAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(requireReAuth)")
    public Object around(ProceedingJoinPoint pjp, RequireReAuth requireReAuth) throws Throwable {
        String token = extractToken();
        if (token == null || token.isBlank()) {
            throw new BizException("缺少二次认证令牌");
        }
        String userId = SecurityContext.getUserId();
        String redisKey = REDIS_PREFIX + requireReAuth.code() + ":" + (userId != null ? userId : "anonymous");
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null || !stored.equals(token)) {
            log.warn("二次认证失败: code={}, user={}", requireReAuth.code(), userId);
            throw new BizException("二次认证令牌无效或已过期");
        }
        // 验证通过后删除 token（一次性使用）
        redisTemplate.delete(redisKey);
        return pjp.proceed();
    }

    private String extractToken() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            return req.getHeader(HEADER_NAME);
        }
        return null;
    }
}
