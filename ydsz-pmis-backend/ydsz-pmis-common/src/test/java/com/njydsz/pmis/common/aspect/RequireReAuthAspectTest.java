package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.RequireReAuth;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.security.SensitiveOperationEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RequireReAuthAspect 敏感操作二次认证切面测试
 */
@DisplayName("RequireReAuthAspect 二次认证切面测试")
class RequireReAuthAspectTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ApplicationEventPublisher publisher;
    private RequireReAuthAspect aspect;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        publisher = mock(ApplicationEventPublisher.class);
        aspect = new RequireReAuthAspect(redisTemplate, publisher);
        SecurityContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("未登录应抛 UNAUTHORIZED")
    void noLogin() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.around(pjp, annotation("OP-1", "敏感操作", 300)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("缺少 token 应抛 FORBIDDEN")
    void missingToken() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.around(pjp, annotation("OP-1", "敏感操作", 300)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Redis token 不存在应抛 FORBIDDEN")
    void invalidToken() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        whenHttp("bad-token", "127.0.0.1");
        when(valueOps.get(anyString())).thenReturn(null);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.around(pjp, annotation("OP-1", "敏感操作", 300)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("Redis token 有效：消费后继续执行业务")
    void validToken_consume() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        whenHttp("good-token", "127.0.0.1");
        when(valueOps.get(anyString())).thenReturn("1");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");

        Object r = aspect.around(pjp, annotation("OP-1", "敏感操作", 300));
        assertThat(r).isEqualTo("OK");

        verify(redisTemplate, times(1)).delete(anyString());
        ArgumentCaptor<SensitiveOperationEvent> cap = ArgumentCaptor.forClass(SensitiveOperationEvent.class);
        verify(publisher, times(1)).publishEvent(cap.capture());
        SensitiveOperationEvent e = cap.getValue();
        assertThat(e.getOperationCode()).isEqualTo("OP-1");
        assertThat(e.getOperationName()).isEqualTo("敏感操作");
        assertThat(e.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("issueToken 生成 key 并设置 TTL")
    void issueToken() {
        String token = aspect.issueToken("OP-1", 1L, 600);
        assertThat(token).isNotBlank();
        verify(valueOps, times(1)).set(anyString(), eq("1"), eq(Duration.ofSeconds(600)));
    }

    @Test
    @DisplayName("发布事件失败不影响主流程")
    void publishFailure_swallow() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        whenHttp("good-token", "127.0.0.1");
        when(valueOps.get(anyString())).thenReturn("1");
        org.mockito.Mockito.doThrow(new RuntimeException("busy")).when(publisher).publishEvent(any());

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");

        Object r = aspect.around(pjp, annotation("OP-1", "敏感操作", 300));
        assertThat(r).isEqualTo("OK");
    }

    @Test
    @DisplayName("请求头均为空时 clientIp 回退 remoteAddr")
    void noForwardedHeader() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        whenHttp("good-token", "127.0.0.1", null, null);
        when(valueOps.get(anyString())).thenReturn("1");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("OK");

        aspect.around(pjp, annotation("OP-1", "敏感操作", 300));
        ArgumentCaptor<SensitiveOperationEvent> cap = ArgumentCaptor.forClass(SensitiveOperationEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().getClientIp()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("无 token 时不应 publishEvent")
    void noToken_noPublish() throws Throwable {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).username("u").build());
        // 不设 request，无 token
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.around(pjp, annotation("OP-1", "敏感操作", 300)))
                .isInstanceOf(BizException.class);
        verify(publisher, never()).publishEvent(any());
    }

    private void whenHttp(String token, String remote) {
        whenHttp(token, remote, null, null);
    }

    private void whenHttp(String token, String remote, String xff, String xRealIp) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("X-Re-Auth-Token")).thenReturn(token);
        when(req.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(req.getHeader("X-Real-IP")).thenReturn(xRealIp);
        when(req.getRemoteAddr()).thenReturn(remote);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private RequireReAuth annotation(String code, String name, int ttl) {
        return (RequireReAuth) java.lang.reflect.Proxy.newProxyInstance(
                RequireReAuth.class.getClassLoader(),
                new Class[]{RequireReAuth.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "code": return code;
                        case "name": return name;
                        case "ttlSeconds": return ttl;
                        case "annotationType": return RequireReAuth.class;
                        default: return null;
                    }
                });
    }
}
