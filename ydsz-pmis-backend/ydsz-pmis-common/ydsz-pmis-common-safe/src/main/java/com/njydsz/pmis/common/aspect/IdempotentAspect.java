package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.Idempotent;
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

import java.time.Duration;

/**
 * 幂等切面 — 基于 Redis SETNX 实现防重提交。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String USER_ID_HEADER = "X-User-Id";

    private final StringRedisTemplate redisTemplate;

    public IdempotentAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String key = buildKey(idempotent);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.ttlSeconds()));
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("幂等拦截: key={}, method={}", key, pjp.getSignature().toShortString());
            throw new BizException(idempotent.message());
        }
        try {
            return pjp.proceed();
        } catch (Exception e) {
            redisTemplate.delete(key);
            throw e;
        }
    }

    private String buildKey(Idempotent idempotent) {
        StringBuilder sb = new StringBuilder("idempotent:");
        sb.append(idempotent.key());
        if (idempotent.useUser()) {
            sb.append(":").append(getUserId());
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            sb.append(":").append(req.getRequestURI());
        }
        return sb.toString();
    }

    private String getUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String userId = attrs.getRequest().getHeader(USER_ID_HEADER);
                return userId != null ? userId : "anonymous";
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return "anonymous";
    }
}
