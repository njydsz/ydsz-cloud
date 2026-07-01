package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdempotentAspect 幂等 AOP 测试")
class IdempotentAspectTest {

    private StringRedisTemplate redis;
    private IdempotentAspect aspect;
    private ProceedingJoinPoint pjp;

    @BeforeEach
    void setUp() throws Exception {
        redis = mock(StringRedisTemplate.class);
        aspect = new IdempotentAspect(redis);
        pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Sample.class.getDeclaredMethod("doIt", String.class));
        when(pjp.getArgs()).thenReturn(new Object[]{"hello"});
    }

    @Test
    @DisplayName("首次请求应放行 - Lua 返回 1")
    @SuppressWarnings("unchecked")
    void firstPass() throws Throwable {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(pjp.proceed()).thenReturn("OK");
        Object r = aspect.around(pjp, annOf("test:", 5));
        assertThat(r).isEqualTo("OK");
    }

    @Test
    @DisplayName("重复请求应抛 BizException")
    @SuppressWarnings("unchecked")
    void duplicate() throws Throwable {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> aspect.around(pjp, annOf("test:", 5)))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10001);
    }

    @Test
    @DisplayName("业务异常时主动释放锁")
    @SuppressWarnings("unchecked")
    void releaseOnException() throws Throwable {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(pjp.proceed()).thenThrow(new RuntimeException("biz error"));
        assertThatThrownBy(() -> aspect.around(pjp, annOf("test:", 5)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("biz error");
        verify(redis, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("keyFromArg SpEL 解析为参数值")
    @SuppressWarnings("unchecked")
    void spelExtract() throws Throwable {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(pjp.proceed()).thenReturn("OK");
        aspect.around(pjp, annOf("test:", "#name", 5));
        // 验证 key 中包含参数值 'hello'
        org.mockito.ArgumentCaptor<List<String>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), captor.capture(), any(), any());
        String k = captor.getValue().get(0);
        assertThat(k).contains("test:").contains("hello");
    }

    private Idempotent annOf(String key, int ttl) {
        return annOf(key, "", ttl);
    }

    private Idempotent annOf(String key, String keyFromArg, int ttl) {
        return new Idempotent() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Idempotent.class;
            }
            @Override public String key() { return key; }
            @Override public String keyFromArg() { return keyFromArg; }
            @Override public boolean useUser() { return false; }
            @Override public int ttlSeconds() { return ttl; }
            @Override public String message() { return "dup"; }
        };
    }

    static class Sample {
        public String doIt(String name) { return name; }
    }
}
