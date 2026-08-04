package com.remisoft.agent.server.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.redis.service.RedisService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("Agent 请求守卫 AgentRequestGuard 测试")
@ExtendWith(MockitoExtension.class)
class AgentRequestGuardTest {

    @Mock
    private RedisService redisService;

    private AgentRequestGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AgentRequestGuard(redisService);
    }

    // ==================== 限流 ====================

    @Nested
    @DisplayName("限流检查")
    class RateLimit {

        @Test
        @DisplayName("首次请求：incr 返回 1，设置 TTL，通过")
        void shouldSetTtlOnFirstRequest() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();

            verify(redisService).incr("remi:agent:rate:user-1", 1L);
            verify(redisService).expire(eq("remi:agent:rate:user-1"), eq(60L));
        }

        @Test
        @DisplayName("第 10 次请求：通过（边界值）")
        void shouldPassAtLimit() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(10L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("第 11 次请求：抛 BusinessException")
        void shouldThrowOnExceedLimit() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(11L);

            assertThatThrownBy(() -> guard.check(null, "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("每分钟最多 10 次");
        }

        @Test
        @DisplayName("null userId：使用 anonymous")
        void shouldUseAnonymousForNullUserId() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);

            assertThatCode(() -> guard.check(null, null))
                    .doesNotThrowAnyException();

            verify(redisService).incr("remi:agent:rate:anonymous", 1L);
        }
    }

    // ==================== 幂等去重 ====================

    @Nested
    @DisplayName("幂等去重")
    class Idempotent {

        @Test
        @DisplayName("首次请求：setIfAbsent 返回 true，通过")
        void shouldPassOnFirstRequest() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);
            when(redisService.setIfAbsent(anyString(), eq("1"), anyLong())).thenReturn(true);

            assertThatCode(() -> guard.check("req-1", "user-1"))
                    .doesNotThrowAnyException();

            verify(redisService).setIfAbsent(eq("remi:agent:idem:req-1"), eq("1"), eq(60L));
        }

        @Test
        @DisplayName("重复请求：setIfAbsent 返回 false，抛 BusinessException")
        void shouldThrowOnDuplicate() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);
            when(redisService.setIfAbsent(anyString(), eq("1"), anyLong())).thenReturn(false);

            assertThatThrownBy(() -> guard.check("req-1", "user-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("60 秒");
        }

        @Test
        @DisplayName("null requestId：跳过幂等检查")
        void shouldSkipIdempotentForNullRequestId() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);

            assertThatCode(() -> guard.check(null, "user-1"))
                    .doesNotThrowAnyException();

            verify(redisService, never()).setIfAbsent(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("blank requestId：跳过幂等检查")
        void shouldSkipIdempotentForBlankRequestId() {
            when(redisService.incr(anyString(), eq(1L))).thenReturn(1L);

            assertThatCode(() -> guard.check("   ", "user-1"))
                    .doesNotThrowAnyException();

            verify(redisService, never()).setIfAbsent(anyString(), anyString(), anyLong());
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

            verify(redisService, never()).delete(anyString());
        }

        @Test
        @DisplayName("blank requestId：直接返回")
        void shouldReturnImmediatelyForBlank() {
            guard.releaseIdempotent("");

            verify(redisService, never()).delete(anyString());
        }

        @Test
        @DisplayName("有效 requestId：删除 Redis key")
        void shouldDeleteKeyForValidRequestId() {
            guard.releaseIdempotent("req-1");

            verify(redisService).delete("remi:agent:idem:req-1");
        }
    }
}
