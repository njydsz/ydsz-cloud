package com.njydsz.pmis.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CursorPageResult 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CursorPageResult 测试")
class CursorPageResultTest {

    @Test
    @DisplayName("无参构造 - 所有字段应为 null/false/0")
    void noArgsConstructor_shouldHaveNullFields() {
        CursorPageResult<String> result = new CursorPageResult<>();

        assertNull(result.getList());
        assertNull(result.getNextCursor());
        assertFalse(result.isHasMore());
        assertEquals(0, result.getSize());
    }

    @Test
    @DisplayName("全参构造 - 应正确设置所有字段")
    void allArgsConstructor_shouldSetAllFields() {
        List<String> list = Arrays.asList("a", "b", "c");
        String nextCursor = "cursor123";
        boolean hasMore = true;
        long size = 3;

        CursorPageResult<String> result = new CursorPageResult<>(list, nextCursor, hasMore, size);

        assertEquals(list, result.getList());
        assertEquals(nextCursor, result.getNextCursor());
        assertTrue(result.isHasMore());
        assertEquals(size, result.getSize());
    }

    @Test
    @DisplayName("全参构造 - hasMore 为 false 时 nextCursor 可为 null")
    void allArgsConstructor_withNoMoreData() {
        List<String> list = Arrays.asList("a", "b");

        CursorPageResult<String> result = new CursorPageResult<>(list, null, false, 2);

        assertEquals(list, result.getList());
        assertNull(result.getNextCursor());
        assertFalse(result.isHasMore());
        assertEquals(2, result.getSize());
    }

    @Test
    @DisplayName("静态工厂 of - 数据不足一页时 hasMore 应为 false")
    void of_shouldReturnResult_withoutMore() {
        List<String> records = Arrays.asList("a", "b", "c");
        long requestedSize = 5;

        CursorPageResult<String> result = CursorPageResult.of(records, r -> "cursor-" + r, requestedSize);

        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
        assertEquals(3, result.getList().size());
        assertEquals(requestedSize, result.getSize());
    }

    @Test
    @DisplayName("静态工厂 of - 数据超过一页时 hasMore 应为 true，且有 nextCursor")
    void of_shouldReturnResult_withMore() {
        List<String> records = Arrays.asList("a", "b", "c", "d", "e", "f");
        long requestedSize = 5;

        CursorPageResult<String> result = CursorPageResult.of(records, r -> "cursor-" + r, requestedSize);

        assertTrue(result.isHasMore());
        assertNotNull(result.getNextCursor());
        assertEquals("cursor-e", result.getNextCursor());
        assertEquals(requestedSize, result.getSize());
        assertEquals(5, result.getList().size());
    }

    @Test
    @DisplayName("静态工厂 of - 空列表 should have no more")
    void of_shouldHandleEmptyList() {
        List<String> records = new ArrayList<>();
        long requestedSize = 10;

        CursorPageResult<String> result = CursorPageResult.of(records, r -> "cursor-" + r, requestedSize);

        assertFalse(result.isHasMore());
        assertNull(result.getNextCursor());
        assertTrue(result.getList().isEmpty());
        assertEquals(requestedSize, result.getSize());
    }
}