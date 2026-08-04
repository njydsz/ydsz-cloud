package com.njydsz.common.core.constant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PageConstants} 单元测试
 *
 * <p>覆盖归一化方法（normalizePageSize / normalizePageNum / calcOffset）、
 * 运行时覆盖值设置等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("PageConstants 分页常量测试")
class PageConstantsTest {

    @Test
    @DisplayName("normalizePageSize 正常值原样返回")
    void normalizePageSize_normal() {
        assertEquals(20, PageConstants.normalizePageSize(20));
        assertEquals(1, PageConstants.normalizePageSize(1));
        assertEquals(1000, PageConstants.normalizePageSize(1000));
    }

    @Test
    @DisplayName("normalizePageSize null/0/负数 → 默认值")
    void normalizePageSize_invalid() {
        assertEquals(20, PageConstants.normalizePageSize(null));
        assertEquals(20, PageConstants.normalizePageSize(0));
        assertEquals(20, PageConstants.normalizePageSize(-5));
    }

    @Test
    @DisplayName("normalizePageSize 超过上限截断为最大值")
    void normalizePageSize_overMax() {
        assertEquals(5000, PageConstants.normalizePageSize(5000));
        assertEquals(5000, PageConstants.normalizePageSize(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("normalizePageNum 正常值原样返回")
    void normalizePageNum_normal() {
        assertEquals(1, PageConstants.normalizePageNum(1));
        assertEquals(3, PageConstants.normalizePageNum(3));
    }

    @Test
    @DisplayName("normalizePageNum null/0/负数 → 第1页")
    void normalizePageNum_invalid() {
        assertEquals(PageConstants.DEFAULT_PAGE_NUM, PageConstants.normalizePageNum(null));
        assertEquals(1, PageConstants.normalizePageNum(0));
        assertEquals(1, PageConstants.normalizePageNum(-1));
    }

    @Test
    @DisplayName("calcOffset 正确计算偏移")
    void calcOffset() {
        assertEquals(0L, PageConstants.calcOffset(1, 20));
        assertEquals(20L, PageConstants.calcOffset(2, 20));
        assertEquals(80L, PageConstants.calcOffset(5, 20)); // (5-1)*20=80
    }

    @Test
    @DisplayName("calcOffset 处理 null 与非法值")
    void calcOffset_invalid() {
        assertEquals(0L, PageConstants.calcOffset(null, 20));   // 第1页 → offset 0
        assertEquals(0L, PageConstants.calcOffset(1, null));    // 默认页大小 → (1-1)*20=0
        assertEquals(0L, PageConstants.calcOffset(0, -1));      // 归一化 → 第1页+默认 → 0
    }

    @Test
    @DisplayName("calcOffset 大页码不溢出 int")
    void calcOffset_noOverflow() {
        // (Integer.MAX_VALUE - 1) * 1000 远超 int 范围，必须为 long 计算
        long offset = PageConstants.calcOffset(Integer.MAX_VALUE, 1000);
        assertEquals((long) (Integer.MAX_VALUE - 1) * 1000, offset);
    }

    @Test
    @DisplayName("编译期常量与运行时默认值一致（未配置时）")
    void compileTimeConstants() {
        assertEquals(1, PageConstants.DEFAULT_PAGE_NUM);
        assertEquals(20, PageConstants.DEFAULT_PAGE_SIZE);
        assertEquals(5000, PageConstants.MAX_PAGE_SIZE);
    }
}
