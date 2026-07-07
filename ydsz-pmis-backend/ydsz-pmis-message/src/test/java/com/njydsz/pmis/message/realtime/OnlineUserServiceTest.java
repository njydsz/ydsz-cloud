package com.njydsz.pmis.message.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4: OnlineUserService 单元测试。
 *
 * <p>验证 Redis Hash 上的 markOnline / markOffline / isOnline / getSessionCount / renewSession 行为。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@SuppressWarnings("unchecked")
class OnlineUserServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private OnlineUserService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new OnlineUserService(redisTemplate);
    }

    @Test
    void markOnline_putsSessionIdAndSetsTtl() {
        service.markOnline("u1", "sess-1");

        verify(hashOps).put(eq("pmis:ws:online:u1"), eq("sess-1"), any(String.class));
        verify(redisTemplate).expire(eq("pmis:ws:online:u1"), any(Duration.class));
    }

    @Test
    void markOnline_ignoresNullUserId() {
        service.markOnline(null, "sess-1");

        verify(hashOps, never()).put(any(), any(), any());
    }

    @Test
    void markOnline_ignoresNullSessionId() {
        service.markOnline("u1", null);

        verify(hashOps, never()).put(any(), any(), any());
    }

    @Test
    void markOffline_removesSessionId() {
        when(hashOps.delete("pmis:ws:online:u1", "sess-1")).thenReturn(1L);

        service.markOffline("u1", "sess-1");

        verify(hashOps).delete(eq("pmis:ws:online:u1"), eq("sess-1"));
    }

    @Test
    void markOffline_deletesKeyWhenHashEmpty() {
        // delete 返回 0 表示 Hash 已空（没有剩余字段被删除）
        when(hashOps.delete("pmis:ws:online:u1", "sess-1")).thenReturn(0L);

        service.markOffline("u1", "sess-1");

        verify(redisTemplate).delete("pmis:ws:online:u1");
    }

    @Test
    void markOffline_keepsKeyWhenHashNotEmpty() {
        when(hashOps.delete("pmis:ws:online:u1", "sess-1")).thenReturn(1L);

        service.markOffline("u1", "sess-1");

        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void isOnline_returnsTrueWhenHashSizePositive() {
        when(hashOps.size("pmis:ws:online:u1")).thenReturn(2L);

        assertTrue(service.isOnline("u1"));
    }

    @Test
    void isOnline_returnsFalseWhenHashSizeZero() {
        when(hashOps.size("pmis:ws:online:u1")).thenReturn(0L);

        assertFalse(service.isOnline("u1"));
    }

    @Test
    void isOnline_returnsFalseWhenNullSize() {
        when(hashOps.size("pmis:ws:online:u1")).thenReturn(null);

        assertFalse(service.isOnline("u1"));
    }

    @Test
    void isOnline_returnsFalseForNullUserId() {
        assertFalse(service.isOnline(null));
    }

    @Test
    void getSessionCount_returnsHashSize() {
        when(hashOps.size("pmis:ws:online:u1")).thenReturn(3L);

        assertEquals(3L, service.getSessionCount("u1"));
    }

    @Test
    void getSessionCount_returnsZeroForNullUserId() {
        assertEquals(0L, service.getSessionCount(null));
    }

    @Test
    void renewSession_refreshesTtlWhenKeyExists() {
        when(redisTemplate.hasKey("pmis:ws:online:u1")).thenReturn(true);

        service.renewSession("u1", "sess-1");

        verify(hashOps).put(eq("pmis:ws:online:u1"), eq("sess-1"), any(String.class));
        verify(redisTemplate).expire(eq("pmis:ws:online:u1"), any(Duration.class));
    }

    @Test
    void renewSession_skipsWhenKeyNotExists() {
        when(redisTemplate.hasKey("pmis:ws:online:u1")).thenReturn(false);

        service.renewSession("u1", "sess-1");

        verify(hashOps, never()).put(any(), any(), any());
    }
}
