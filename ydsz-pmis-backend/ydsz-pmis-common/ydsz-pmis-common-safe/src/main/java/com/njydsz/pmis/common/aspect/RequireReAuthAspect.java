package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.exception.BizException;
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

/**
 * 二次认证切面 — 校验 X-Re-Auth-Token 有效性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Aspect
@Component
public class RequireReAuthAspect {

    private static final Logger log = LoggerFactory.getLogger(RequireReAuthAspect.class);
    private static final String HEADER_NAME = "X-Re-Auth-Token";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REDIS_PREFIX = "re_auth:";

    private final StringRedisTemplate redisTemplate;

    public RequireReAuthAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(requireReAuth)")
    public Object around(ProceedingJoinPoint pjp, RequireReAuth requireReAuth) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new BizException("非 Web 请求上下文，不支持二次认证");
        }
        HttpServletRequest req = attrs.getRequest();
        String token = req.getHeader(HEADER_NAME);
        if (token == null || token.isBlank()) {
            throw new BizException("缺少二次认证令牌");
        }
        String userId = req.getHeader(USER_ID_HEADER);
        String redisKey = REDIS_PREFIX + requireReAuth.code() + ":" + (userId != null ? userId : "anonymous");
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null || !stored.equals(token)) {
            log.warn("二次认证失败: code={}, user={}", requireReAuth.code(), userId);
            throw new BizException("二次认证令牌无效或已过期");
        }
        redisTemplate.delete(redisKey);
        return pjp.proceed();
    }
}
