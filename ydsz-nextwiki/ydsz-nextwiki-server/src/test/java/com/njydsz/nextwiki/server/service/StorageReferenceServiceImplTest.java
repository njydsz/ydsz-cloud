package com.njydsz.nextwiki.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.redis.service.ops.RedisStringOps;

@ExtendWith(MockitoExtension.class)
class StorageReferenceServiceImplTest {

    @InjectMocks
    private StorageReferenceServiceImpl storageRefService;

    @Mock
    private RedisStringOps redisStringOps;

    @Nested
    @DisplayName("increment")
    class Increment {

        @Test
        @DisplayName("increment - first reference sets TTL and returns 1")
        void increment_firstReference_setsTtl() {
            when(redisStringOps.incr(anyString(), eq(1L))).thenReturn(1L);

            long result = storageRefService.increment("file-001");

            assertThat(result).isEqualTo(1L);
            verify(redisStringOps).expire(anyString(), anyLong());
        }

        @Test
        @DisplayName("increment - subsequent references increment without TTL")
        void increment_subsequentReference_noTtlSet() {
            when(redisStringOps.incr(anyString(), eq(1L))).thenReturn(3L);

            long result = storageRefService.increment("file-001");

            assertThat(result).isEqualTo(3L);
            verify(redisStringOps, never()).expire(anyString(), anyLong());
        }

        @Test
        @DisplayName("increment - constructs correct Redis key")
        void increment_correctKey() {
            when(redisStringOps.incr("wiki:ref:file-abc", 1L)).thenReturn(1L);

            storageRefService.increment("file-abc");

            verify(redisStringOps).incr("wiki:ref:file-abc", 1L);
        }
    }

    @Nested
    @DisplayName("decrement")
    class Decrement {

        @Test
        @DisplayName("decrement - count above zero returns reduced count")
        void decrement_countAboveZero_returnsReduced() {
            when(redisStringOps.decr(anyString(), eq(1L))).thenReturn(2L);

            long result = storageRefService.decrement("file-001");

            assertThat(result).isEqualTo(2L);
            verify(redisStringOps, never()).del(anyString());
        }

        @Test
        @DisplayName("decrement - count reaches zero deletes key and returns 0")
        void decrement_countReachesZero_deletesKey() {
            when(redisStringOps.decr(anyString(), eq(1L))).thenReturn(0L);

            long result = storageRefService.decrement("file-001");

            assertThat(result).isEqualTo(0L);
            verify(redisStringOps).del(anyString());
        }

        @Test
        @DisplayName("decrement - count goes below zero deletes key and returns 0")
        void decrement_countBelowZero_deletesKey() {
            when(redisStringOps.decr(anyString(), eq(1L))).thenReturn(-1L);

            long result = storageRefService.decrement("file-001");

            assertThat(result).isEqualTo(0L);
            verify(redisStringOps).del(anyString());
        }
    }

    @Nested
    @DisplayName("getCount")
    class GetCount {

        @Test
        @DisplayName("getCount - existing count returns parsed value")
        void getCount_existing_returnsValue() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn("5");

            long result = storageRefService.getCount("file-001");

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("getCount - no value returns zero")
        void getCount_noValue_returnsZero() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn(null);

            long result = storageRefService.getCount("file-001");

            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("getCount - blank string returns zero")
        void getCount_blankString_returnsZero() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn("  ");

            long result = storageRefService.getCount("file-001");

            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("getCount - invalid number returns zero")
        void getCount_invalidNumber_returnsZero() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn("not_a_number");

            long result = storageRefService.getCount("file-001");

            assertThat(result).isEqualTo(0L);
        }
    }
}
