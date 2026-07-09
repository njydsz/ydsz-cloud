package com.njydsz.pmis.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BloomFilterService 布隆过滤器服务单元测试
 *
 * <p>覆盖 mightContain / add / addAll / rebuild / count / clear 方法,
 * 以及未注册过滤器的异常场景.
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilterService 布隆过滤器服务测试")
class BloomFilterServiceTest {

    @Mock
    private RBloomFilter<String> userBloomFilter;

    @Mock
    private RBloomFilter<String> userIdBloomFilter;

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new BloomFilterService(userBloomFilter, userIdBloomFilter);
        // @PostConstruct 不会在单元测试中自动调用，需手动触发 init 完成过滤器注册
        bloomFilterService.init();
    }

    // ==================== mightContain ====================

    @Test
    @DisplayName("正常场景：mightContain 委托给对应过滤器")
    void mightContain_已注册过滤器_委托调用() {
        String key = "alice";
        when(userBloomFilter.contains(key)).thenReturn(true);

        boolean result = bloomFilterService.mightContain("user:username", key);

        assertEquals(true, result);
        verify(userBloomFilter).contains(key);
    }

    @Test
    @DisplayName("正常场景：mightContain 对 user:id 过滤器委托调用")
    void mightContain_用户ID过滤器() {
        String key = "12345";
        when(userIdBloomFilter.contains(key)).thenReturn(false);

        boolean result = bloomFilterService.mightContain("user:id", key);

        assertEquals(false, result);
        verify(userIdBloomFilter).contains(key);
    }

    @Test
    @DisplayName("异常场景：mightContain 未注册过滤器抛 IllegalArgumentException")
    void mightContain_未注册过滤器_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> bloomFilterService.mightContain("unknown", "key"));
    }

    // ==================== add ====================

    @Test
    @DisplayName("正常场景：add 添加元素到对应过滤器")
    void add_已注册过滤器_委托调用() {
        String key = "bob";

        bloomFilterService.add("user:username", key);

        verify(userBloomFilter).add(key);
    }

    @Test
    @DisplayName("异常场景：add 未注册过滤器抛 IllegalArgumentException")
    void add_未注册过滤器_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> bloomFilterService.add("unknown", "key"));
    }

    // ==================== addAll ====================

    @Test
    @DisplayName("正常场景：addAll 批量添加多个元素")
    void addAll_批量添加() {
        List<String> keys = List.of("a", "b", "c");

        bloomFilterService.addAll("user:id", keys);

        verify(userIdBloomFilter).add("a");
        verify(userIdBloomFilter).add("b");
        verify(userIdBloomFilter).add("c");
    }

    @Test
    @DisplayName("边界场景：addAll 传入 null 直接返回不调用过滤器")
    void addAll_null_跳过() {
        bloomFilterService.addAll("user:username", null);

        verify(userBloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("边界场景：addAll 传入空集合直接返回不调用过滤器")
    void addAll_空集合_跳过() {
        bloomFilterService.addAll("user:username", Collections.emptyList());

        verify(userBloomFilter, never()).add(anyString());
    }

    // ==================== rebuild ====================

    @Test
    @DisplayName("正常场景：rebuild 删除旧数据 → 重新初始化 → 批量添加")
    void rebuild_带元素集合() {
        List<String> keys = List.of("x", "y");
        when(userBloomFilter.tryInit(100000L, 0.001)).thenReturn(true);

        bloomFilterService.rebuild("user:username", keys);

        verify(userBloomFilter).delete();
        verify(userBloomFilter).tryInit(100000L, 0.001);
        verify(userBloomFilter).add("x");
        verify(userBloomFilter).add("y");
    }

    @Test
    @DisplayName("边界场景：rebuild 传 null 集合仅删除+初始化不添加")
    void rebuild_null集合() {
        when(userIdBloomFilter.tryInit(100000L, 0.001)).thenReturn(true);

        bloomFilterService.rebuild("user:id", null);

        verify(userIdBloomFilter).delete();
        verify(userIdBloomFilter).tryInit(100000L, 0.001);
        verify(userIdBloomFilter, never()).add(anyString());
    }

    @Test
    @DisplayName("异常场景：rebuild 未注册过滤器抛 IllegalArgumentException")
    void rebuild_未注册过滤器_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> bloomFilterService.rebuild("unknown", List.of("a")));
    }

    // ==================== count ====================

    @Test
    @DisplayName("正常场景：count 返回过滤器估算元素数")
    void count_已注册过滤器() {
        when(userBloomFilter.count()).thenReturn(42L);

        long result = bloomFilterService.count("user:username");

        assertEquals(42L, result);
    }

    @Test
    @DisplayName("异常场景：count 未注册过滤器抛 IllegalArgumentException")
    void count_未注册过滤器_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> bloomFilterService.count("unknown"));
    }

    // ==================== clear ====================

    @Test
    @DisplayName("正常场景：clear 删除数据并重新初始化")
    void clear_已注册过滤器() {
        when(userBloomFilter.tryInit(100000L, 0.001)).thenReturn(true);

        bloomFilterService.clear("user:username");

        verify(userBloomFilter).delete();
        verify(userBloomFilter).tryInit(100000L, 0.001);
    }

    @Test
    @DisplayName("异常场景：clear 未注册过滤器抛 IllegalArgumentException")
    void clear_未注册过滤器_抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> bloomFilterService.clear("unknown"));
    }
}
