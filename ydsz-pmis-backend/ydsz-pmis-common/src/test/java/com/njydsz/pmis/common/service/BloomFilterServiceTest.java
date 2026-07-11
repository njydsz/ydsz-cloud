package com.njydsz.pmis.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BloomFilterService} 布隆过滤器服务测试
 *
 * <p>覆盖过滤器的添加、判断、批量添加、重建、清空、计数以及未注册过滤器的异常处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("BloomFilterService 布隆过滤器服务测试")
@ExtendWith(MockitoExtension.class)
class BloomFilterServiceTest {

    @Mock
    private RBloomFilter<String> userBloomFilter;

    @Mock
    private RBloomFilter<String> userIdBloomFilter;

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new BloomFilterService(userBloomFilter, userIdBloomFilter);
        bloomFilterService.init();
    }

    @Nested
    @DisplayName("init() 初始化注册")
    class InitTest {

        @Test
        @DisplayName("init 后 user:username 和 user:id 均可访问")
        void shouldRegisterBothFilters() {
            // 验证两个过滤器都能正常调用（不抛异常）
            when(userBloomFilter.contains("test")).thenReturn(false);
            when(userIdBloomFilter.contains("1")).thenReturn(false);

            assertThat(bloomFilterService.mightContain("user:username", "test")).isFalse();
            assertThat(bloomFilterService.mightContain("user:id", "1")).isFalse();
        }
    }

    @Nested
    @DisplayName("mightContain() 判断元素")
    class MightContainTest {

        @Test
        @DisplayName("元素存在返回 true")
        void shouldReturnTrueWhenExists() {
            when(userBloomFilter.contains("admin")).thenReturn(true);

            assertThat(bloomFilterService.mightContain("user:username", "admin")).isTrue();
        }

        @Test
        @DisplayName("元素不存在返回 false")
        void shouldReturnFalseWhenNotExists() {
            when(userBloomFilter.contains("ghost")).thenReturn(false);

            assertThat(bloomFilterService.mightContain("user:username", "ghost")).isFalse();
        }

        @Test
        @DisplayName("user:id 过滤器独立判断")
        void shouldCheckUserIdFilter() {
            when(userIdBloomFilter.contains("1001")).thenReturn(true);

            assertThat(bloomFilterService.mightContain("user:id", "1001")).isTrue();
        }

        @Test
        @DisplayName("未注册的过滤器名抛出 IllegalArgumentException")
        void shouldThrowForUnregisteredFilter() {
            assertThatThrownBy(() -> bloomFilterService.mightContain("unknown:filter", "key"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未注册的布隆过滤器")
                    .hasMessageContaining("unknown:filter");
        }
    }

    @Nested
    @DisplayName("add() 添加元素")
    class AddTest {

        @Test
        @DisplayName("添加单个元素到 user:username")
        void shouldAddToUsernameFilter() {
            bloomFilterService.add("user:username", "newuser");

            verify(userBloomFilter).add("newuser");
        }

        @Test
        @DisplayName("添加单个元素到 user:id")
        void shouldAddToUserIdFilter() {
            bloomFilterService.add("user:id", "2002");

            verify(userIdBloomFilter).add("2002");
        }

        @Test
        @DisplayName("向未注册的过滤器添加抛出 IllegalArgumentException")
        void shouldThrowWhenAddToUnregistered() {
            assertThatThrownBy(() -> bloomFilterService.add("unknown", "key"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("addAll() 批量添加")
    class AddAllTest {

        @Test
        @DisplayName("批量添加多个元素")
        void shouldAddAllElements() {
            List<String> keys = List.of("u1", "u2", "u3");

            bloomFilterService.addAll("user:username", keys);

            verify(userBloomFilter).add("u1");
            verify(userBloomFilter).add("u2");
            verify(userBloomFilter).add("u3");
        }

        @Test
        @DisplayName("keys 为 null 时不调用过滤器")
        void shouldNotCallFilterWhenKeysNull() {
            bloomFilterService.addAll("user:username", null);

            verify(userBloomFilter, never()).add(any(String.class));
        }

        @Test
        @DisplayName("keys 为空集合时不调用过滤器")
        void shouldNotCallFilterWhenKeysEmpty() {
            bloomFilterService.addAll("user:username", List.of());

            verify(userBloomFilter, never()).add(any(String.class));
        }

        @Test
        @DisplayName("批量添加到 user:id 过滤器")
        void shouldAddAllToUserIdFilter() {
            List<String> ids = List.of("1", "2");

            bloomFilterService.addAll("user:id", ids);

            verify(userIdBloomFilter).add("1");
            verify(userIdBloomFilter).add("2");
        }

        @Test
        @DisplayName("向未注册过滤器批量添加抛出异常")
        void shouldThrowWhenAddAllToUnregistered() {
            assertThatThrownBy(() -> bloomFilterService.addAll("unknown", List.of("k")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("rebuild() 重建过滤器")
    class RebuildTest {

        @Test
        @DisplayName("重建：删除旧数据 → 重新初始化 → 批量添加新元素")
        void shouldDeleteInitAndAddElements() {
            List<String> keys = List.of("a", "b");
            when(userBloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

            bloomFilterService.rebuild("user:username", keys);

            verify(userBloomFilter).delete();
            verify(userBloomFilter).tryInit(100000L, 0.001);
            verify(userBloomFilter).add("a");
            verify(userBloomFilter).add("b");
        }

        @Test
        @DisplayName("重建时 keys 为 null 仅删除和初始化，不添加")
        void shouldDeleteAndInitWithoutAddWhenKeysNull() {
            when(userBloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

            bloomFilterService.rebuild("user:username", null);

            verify(userBloomFilter).delete();
            verify(userBloomFilter).tryInit(100000L, 0.001);
            verify(userBloomFilter, never()).add(any(String.class));
        }

        @Test
        @DisplayName("重建时 keys 为空集合仅删除和初始化")
        void shouldDeleteAndInitWithoutAddWhenKeysEmpty() {
            when(userBloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

            bloomFilterService.rebuild("user:username", List.of());

            verify(userBloomFilter).delete();
            verify(userBloomFilter).tryInit(100000L, 0.001);
            verify(userBloomFilter, never()).add(any(String.class));
        }

        @Test
        @DisplayName("重建未注册的过滤器抛出异常")
        void shouldThrowWhenRebuildUnregistered() {
            assertThatThrownBy(() -> bloomFilterService.rebuild("unknown", List.of("k")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("count() 估算元素数量")
    class CountTest {

        @Test
        @DisplayName("返回过滤器估算的元素数量")
        void shouldReturnEstimatedCount() {
            when(userBloomFilter.count()).thenReturn(5000L);

            assertThat(bloomFilterService.count("user:username")).isEqualTo(5000L);
        }

        @Test
        @DisplayName("空过滤器返回 0")
        void shouldReturnZeroForEmptyFilter() {
            when(userIdBloomFilter.count()).thenReturn(0L);

            assertThat(bloomFilterService.count("user:id")).isEqualTo(0L);
        }

        @Test
        @DisplayName("未注册过滤器抛出异常")
        void shouldThrowWhenCountUnregistered() {
            assertThatThrownBy(() -> bloomFilterService.count("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("clear() 清空过滤器")
    class ClearTest {

        @Test
        @DisplayName("清空：删除数据 → 重新初始化")
        void shouldDeleteAndReinit() {
            when(userBloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

            bloomFilterService.clear("user:username");

            verify(userBloomFilter).delete();
            verify(userBloomFilter).tryInit(100000L, 0.001);
        }

        @Test
        @DisplayName("清空 user:id 过滤器")
        void shouldClearUserIdFilter() {
            when(userIdBloomFilter.tryInit(anyLong(), anyDouble())).thenReturn(true);

            bloomFilterService.clear("user:id");

            verify(userIdBloomFilter).delete();
            verify(userIdBloomFilter).tryInit(100000L, 0.001);
        }

        @Test
        @DisplayName("清空未注册的过滤器抛出异常")
        void shouldThrowWhenClearUnregistered() {
            assertThatThrownBy(() -> bloomFilterService.clear("unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
