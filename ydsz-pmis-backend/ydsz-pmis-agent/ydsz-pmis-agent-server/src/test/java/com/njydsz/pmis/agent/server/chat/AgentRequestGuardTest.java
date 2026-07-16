package com.njydsz.pmis.agent.server.chat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.njydsz.pmis.common.exception.custom.DuplicateException;
import com.njydsz.pmis.common.exception.custom.RateLimitException;

/**
 * {@link AgentRequestGuard} 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>限流检查（首次请求设置 TTL、超限抛异常）</li>
 *   <li>幂等去重（SETNX 成功/失败）</li>
 *   <li>null/blank requestId 跳过幂等</li>
 *   <li>null userId 使用 "anonymous"</li>
 *   <li>releaseIdempotent 释放锁</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Agent 请求守卫 AgentRequestGuard 测试")
@ExtendWith(MockitoExtension.class)
class AgentRequestGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AgentRequestGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        guard = new AgentRequestGuard(redisTemplate);
    }

    // ==================== 限流 ====================

    @Nested
    @DisplayName("限流检查")
    class RateLimit {

        @Test
        @DisplayName("首次请求：increment 返回 1，设置 TTL，通过")
        void shouldSetTtlOnFirstRequest() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();

            verify(valueOps).increment("pmis:agent:rate:user-1");
            verify(redisTemplate).expire(eq("pmis:agent:rate:user-1"), eq(60L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("第 10 次请求：通过（边界值）")
        void shouldPassAtLimit() {
            when(valueOps.increment(anyString())).thenReturn(10L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("第 11 次请求：抛 RateLimitException")
        void shouldThrowOnExceedLimit() {
            when(valueOps.increment(anyString())).thenReturn(11L);

            assertThatThrownBy(() -> guard.check(null, "user-1"))
                    .isInstanceOf(RateLimitException.class)
                    .hasMessageContaining("每分钟最多 10 次");
        }

        @Test
        @DisplayName("null userId：使用 anonymous")
        void shouldUseAnonymousForNullUserId() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            assertThatCode(() -> guard.check(null, null))
                    .doesNotThrowAnyException();

            verify(valueOps).increment("pmis:agent:rate:anonymous");
        }
    }

    // ==================== 幂等去重 ====================

    @Nested
    @DisplayName("幂等去重")
    class Idempotent {

        @Test
        @DisplayName("首次请求：SETNX 返回 true，通过")
        void shouldPassOnFirstRequest() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            assertThatCode(() -> guard.check("req-1", "user-1"))
                    .doesNotThrowAnyException();

            verify(valueOps).setIfAbsent(eq("pmis:agent:idem:req-1"), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("重复请求：SETNX 返回 false，抛 DuplicateException")
        void shouldThrowOnDuplicate() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            assertThatThrownBy(() -> guard.check("req-1", "user-1"))
                    .isInstanceOf(DuplicateException.class)
                    .hasMessageContaining("60 秒");
        }

        @Test
        @DisplayName("SETNX 返回 null：抛 DuplicateException（防御性）")
        void shouldThrowOnNullSetIfAbsent() {
            when(valueOps.increment(anyString())).thenReturn(1L);
            when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(null);

            assertThatThrownBy(() -> guard.check("req-1", "user-1"))
                    .isInstanceOf(DuplicateException.class);
        }

        @Test
        @DisplayName("null requestId：跳过幂等检查")
        void shouldSkipIdempotentForNullRequestId() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();

            verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("blank requestId：跳过幂等检查")
        void shouldSkipIdempotentForBlankRequestId() {
            when(valueOps.increment(anyString())).thenReturn(1L);

            assertThatCode(() -> guard.check("   ", "user-1"))
                    .doesNotThrowAnyException();

            verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    // ==================== releaseIdempotent ====================

    @Nested
    @DisplayName("释放幂等锁")
    class Release {

        @Test
        @DisplayName("null requestId：直接返回，不调用 Redis")
        void shouldReturnImmediatelyForNull() {
            guard.releaseIdempotent(null);

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("blank requestId：直接返回")
        void shouldReturnImmediatelyForBlank() {
            guard.releaseIdempotent("");

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("有效 requestId：删除 Redis key")
        void shouldDeleteKeyForValidRequestId() {
            guard.releaseIdempotent("req-1");

            verify(redisTemplate).delete("pmis:agent:idem:req-1");
        }
    }
}
