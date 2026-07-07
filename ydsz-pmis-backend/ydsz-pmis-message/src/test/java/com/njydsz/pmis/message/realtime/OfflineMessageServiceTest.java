package com.njydsz.pmis.message.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4: OfflineMessageService 单元测试。
 *
 * <p>验证 cacheOffline（LPUSH+LTRIM+expire）、drainOffline（range+reverse+del）、
 * countOffline、异常降级行为。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@SuppressWarnings("unchecked")
class OfflineMessageServiceTest {

    private StringRedisTemplate redisTemplate;
    private ListOperations<String, String> listOps;
    private OfflineMessageService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        service = new OfflineMessageService(redisTemplate);
    }

    @Test
    void cacheOffline_pushesAndTrimsAndExpires() {
        service.cacheOffline("u1", "NOTIFICATION", "hello");

        verify(listOps).leftPush(eq("pmis:ws:offline:u1"), any(String.class));
        verify(listOps).trim(eq("pmis:ws:offline:u1"), eq(0L), eq(99L));
        verify(redisTemplate).expire(eq("pmis:ws:offline:u1"), any(Duration.class));
    }

    @Test
    void cacheOffline_ignoresNullUserId() {
        service.cacheOffline(null, "NOTIFICATION", "x");

        verify(listOps, never()).leftPush(any(), any());
    }

    @Test
    void cacheOffline_swallowsExceptionAndDoesNotThrow() {
        when(listOps.leftPush(any(), any()))
                .thenThrow(new RuntimeException("redis down"));

        // 不应抛异常
        service.cacheOffline("u1", "NOTIFICATION", "x");
    }

    @Test
    void drainOffline_returnsReversedListAndDeletesKey() {
        // LPUSH 导致最新在头部，range 返回 [new, mid, old]
        when(listOps.range("pmis:ws:offline:u1", 0, -1))
                .thenReturn(List.of("newest", "middle", "oldest"));

        List<String> result = service.drainOffline("u1");

        // 反转后应为时间正序：最旧在前
        assertEquals(3, result.size());
        assertEquals("oldest", result.get(0));
        assertEquals("middle", result.get(1));
        assertEquals("newest", result.get(2));
        verify(redisTemplate).delete("pmis:ws:offline:u1");
    }

    @Test
    void drainOffline_returnsEmptyWhenNoMessages() {
        when(listOps.range("pmis:ws:offline:u1", 0, -1))
                .thenReturn(List.of());

        List<String> result = service.drainOffline("u1");

        assertTrue(result.isEmpty());
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void drainOffline_returnsEmptyWhenNullRange() {
        when(listOps.range("pmis:ws:offline:u1", 0, -1))
                .thenReturn(null);

        List<String> result = service.drainOffline("u1");

        assertTrue(result.isEmpty());
    }

    @Test
    void drainOffline_returnsEmptyForNullUserId() {
        List<String> result = service.drainOffline(null);

        assertTrue(result.isEmpty());
        verify(listOps, never()).range(any(), eq(0L), eq(-1L));
    }

    @Test
    void countOffline_returnsListSize() {
        when(listOps.size("pmis:ws:offline:u1")).thenReturn(5L);

        assertEquals(5L, service.countOffline("u1"));
    }

    @Test
    void countOffline_returnsZeroForNullUserId() {
        assertEquals(0L, service.countOffline(null));
    }

    @Test
    void countOffline_returnsZeroWhenSizeNull() {
        when(listOps.size("pmis:ws:offline:u1")).thenReturn(null);

        assertEquals(0L, service.countOffline("u1"));
    }
}
