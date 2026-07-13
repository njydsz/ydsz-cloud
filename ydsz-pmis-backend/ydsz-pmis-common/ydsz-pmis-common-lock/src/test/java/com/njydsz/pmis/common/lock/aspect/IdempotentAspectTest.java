package com.njydsz.pmis.common.lock.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.lock.exception.IdempotentException;

/**
 * {@link IdempotentAspect} 单元测试
 *
 * <p>覆盖核心逻辑：
 * <ul>
 *   <li>Redis key 命名空间前缀（{@code pmis:idem:}）</li>
 *   <li>静态 key 直接使用</li>
 *   <li>SpEL 表达式 key 解析</li>
 *   <li>空 key 自动生成（类名#方法名#参数摘要）</li>
 *   <li>Redis 返回 1 时正常放行</li>
 *   <li>Redis 返回 0 时抛 {@link IdempotentException}</li>
 *   <li>业务异常自动释放锁</li>
 *   <li>非业务异常保留锁</li>
 *   <li>Redis 故障降级放行</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @since 1.1.0
 */
@DisplayName("IdempotentAspect 单元测试")
class IdempotentAspectTest {

    private StringRedisTemplate redisTemplate;
    private IdempotentAspect aspect;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        aspect = new IdempotentAspect(redisTemplate, "test-ns", null);
    }

    @Test
    @DisplayName("固定前缀应为 pmis:idem:")
    void keyPrefixShouldBeConstant() {
        assertEquals("pmis:idem:", IdempotentAspect.keyPrefix());
    }

    @Test
    @DisplayName("namespace 应正确注入")
    void namespaceShouldBeInjected() {
        assertEquals("test-ns", aspect.namespace());
    }

    @Test
    @DisplayName("静态 key 应直接使用，并附加命名空间前缀")
    void staticKeyShouldBeUsedDirectly() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "staticKey", new Object[]{});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("order:create");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Object result = aspect.around(joinPoint, idempotent);

        assertEquals("ok", result);
        verify(redisTemplate, times(1)).execute(any(),
                eq(Collections.singletonList("pmis:idem:test-ns:order:create")), any(), eq("5"));
    }

    @Test
    @DisplayName("Redis 返回 0 应抛 IdempotentException（HTTP 409）")
    void shouldThrowIdempotentExceptionWhenRedisReturnsZero() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "staticKey", new Object[]{});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("order:create");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(0L);

        IdempotentException ex = assertThrows(IdempotentException.class,
                () -> aspect.around(joinPoint, idempotent));

        assertEquals(409, ex.getHttpStatus());
        assertEquals("IDEMPOTENT_REJECT", ex.getCode());
        assertEquals("请勿重复提交", ex.getMessage());
    }

    @Test
    @DisplayName("空 key 应自动按 类名#方法名#参数摘要 生成")
    void emptyKeyShouldAutoGenerate() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "autoKey", new Object[]{"arg1", 123});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Object result = aspect.around(joinPoint, idempotent);

        assertEquals("ok", result);
        verify(redisTemplate, times(1)).execute(any(),
                eq(Collections.singletonList("pmis:idem:test-ns:SampleController#autoKeyMethod#"
                        + sha256Prefix("arg1|123"))), any(), eq("5"));
    }

    @Test
    @DisplayName("SpEL 表达式 #{#orderId} 应正确解析")
    void spelKeyShouldBeResolved() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "spelKey", new Object[]{"ORD-001"});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("#{#orderId}");
        when(idempotent.ttlSeconds()).thenReturn(10);
        when(idempotent.message()).thenReturn("重复提交");
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Object result = aspect.around(joinPoint, idempotent);

        assertEquals("ok", result);
        verify(redisTemplate, times(1)).execute(any(),
                eq(Collections.singletonList("pmis:idem:test-ns:ORD-001")), any(), eq("10"));
    }

    @Test
    @DisplayName("业务异常应自动释放幂等锁")
    void businessExceptionShouldReleaseLock() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "throwBusiness", new Object[]{});
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("test:biz");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> aspect.around(joinPoint, idempotent));

        assertNotNull(thrown);
        // 验证调用了两次 Redis：一次获取锁，一次释放锁
        verify(redisTemplate, times(2)).execute(any(), anyList(), any(Object.class), any());
    }

    @Test
    @DisplayName("非业务异常应保留幂等锁（不释放）")
    void nonBusinessExceptionShouldKeepLock() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "throwRuntime", new Object[]{});
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("test:sys");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.around(joinPoint, idempotent));

        assertEquals("sys-error", thrown.getMessage());
        // 验证只调用了一次 Redis（获取锁），未调用释放锁
        verify(redisTemplate, times(1)).execute(any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("Redis 故障应降级放行")
    void redisFailureShouldDegradeAndPass() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "staticKey", new Object[]{});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("order:create");
        when(idempotent.ttlSeconds()).thenReturn(5);
        when(idempotent.message()).thenReturn("请勿重复提交");
        when(redisTemplate.execute(any(), anyList(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        Object result = aspect.around(joinPoint, idempotent);

        assertEquals("ok", result);
    }

    @Test
    @DisplayName("TTL 非法（<=0）应降级放行")
    void invalidTtlShouldDegradeAndPass() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint(SampleController.class, "staticKey", new Object[]{});
        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("order:create");
        when(idempotent.ttlSeconds()).thenReturn(0);
        when(idempotent.message()).thenReturn("请勿重复提交");

        Object result = aspect.around(joinPoint, idempotent);

        assertEquals("ok", result);
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any());
    }

    // ============================== 测试辅助 ==============================

    /**
     * 计算参数 SHA-256 前 16 字节十六进制（与 IdempotentAspect.digestArgs 一致）
     */
    private static String sha256Prefix(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * Mock ProceedingJoinPoint，模拟目标方法调用
     */
    private ProceedingJoinPoint mockJoinPoint(Class<?> controllerClass, String methodName, Object[] args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method;
        try {
            // SampleController 中所有测试方法名都加 "Method" 后缀避免与 Java 关键字冲突
            method = controllerClass.getMethod(methodName + "Method", getParamTypes(args));
        } catch (NoSuchMethodException e) {
            method = controllerClass.getMethods()[0];
        }
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn("ok");
        return joinPoint;
    }

    private Class<?>[] getParamTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] == null ? Object.class : args[i].getClass();
        }
        return types;
    }

    /**
     * 测试用 Controller，提供被切面拦截的方法签名
     */
    public static class SampleController {
        @Idempotent(key = "static:key", ttlSeconds = 5)
        public String staticKeyMethod() {
            return "ok";
        }

        @Idempotent(ttlSeconds = 5)
        public String autoKeyMethod(String arg1, Integer arg2) {
            return "ok";
        }

        @Idempotent(key = "#{#orderId}", ttlSeconds = 10)
        public String spelKeyMethod(String orderId) {
            return "ok";
        }

        @Idempotent(key = "test:biz", ttlSeconds = 5)
        public String throwBusinessMethod() {
            throw BusinessException.builder().message("biz-error").build();
        }

        @Idempotent(key = "test:sys", ttlSeconds = 5)
        public String throwRuntimeMethod() {
            throw new RuntimeException("sys-error");
        }
    }
}
