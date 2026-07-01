package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimiterAspect 限流切面单元测试
 */
@DisplayName("RateLimiterAspect 限流切面测试")
class RateLimiterAspectTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RateLimiterAspect aspect;

    @org.junit.jupiter.api.BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        aspect = new RateLimiterAspect(redisTemplate);
    }

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("未超限应放行业务执行")
    void allow() throws Throwable {
        when(valueOps.increment(anyString())).thenReturn(1L);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        Object r = aspect.around(pjp, sampleAnnotation(5, "test:", 1, "限流"));
        assertThat(r).isEqualTo("ok");
    }

    @Test
    @DisplayName("超限应抛 RATE_LIMIT 异常")
    void reject() throws Throwable {
        when(valueOps.increment(anyString())).thenReturn(10L);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.around(pjp, sampleAnnotation(5, "test:", 1, "限流")))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.RATE_LIMIT.getCode());
    }

    @Test
    @DisplayName("increment 首次后应设置过期时间")
    void expireOnFirst() throws Throwable {
        when(valueOps.increment(anyString())).thenReturn(1L);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp, sampleAnnotation(5, "exp:", 2, "限流"));
        org.mockito.Mockito.verify(redisTemplate).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("未登录用户按 IP 限流")
    void anonymousByIp() throws Throwable {
        when(valueOps.increment(anyString())).thenReturn(1L);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getRemoteAddr()).thenReturn("192.168.1.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        Object r = aspect.around(pjp, sampleAnnotation(5, "anon:", 1, "限流"));
        assertThat(r).isEqualTo("ok");
    }

    @Test
    @DisplayName("登录用户按 userId 限流")
    void byUser() throws Throwable {
        when(valueOps.increment(anyString())).thenReturn(1L);
        SecurityContext.setCurrent(LoginUser.builder().userId(7L).build());
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        Object r = aspect.around(pjp, sampleAnnotation(5, "user:", 1, "限流"));
        assertThat(r).isEqualTo("ok");
    }

    private RateLimit sampleAnnotation(int qps, String key, int window, String message) {
        return (RateLimit) java.lang.reflect.Proxy.newProxyInstance(
                RateLimit.class.getClassLoader(),
                new Class[]{RateLimit.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "qps": return qps;
                        case "key": return key;
                        case "windowSeconds": return window;
                        case "message": return message;
                        case "annotationType": return RateLimit.class;
                        default: return null;
                    }
                });
    }
}
