package com.remisoft.common.core.response;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PageResponse 单元测试
 *
 * <p>覆盖重点场景：基础构造、异常桥接、{@link PageResponse#from(IPageResult)} 转换。
 *
 * @author remi-team
 * @since 1.8.0
 */
class PageResponseTest {

    /**
     * 简单 IPageResult 实现（测试用）
     */
    private static SimplePageResult simpleResult(List<?> records, long total, long pageNum, long pageSize) {
        return new SimplePageResult(records, total, pageNum, pageSize);
    }

    private record SimplePageResult(List<?> records, long total, long pageNum, long pageSize) implements IPageResult {
    }

    @Nested
    @DisplayName("传统构造")
    class Traditional {

        @Test
        @DisplayName("success 正常分页")
        void successNormal() {
            List<String> data = List.of("a", "b", "c");
            PageResponse<String> resp = PageResponse.success(25L, 3L, 10L, data);

            assertEquals("SUCCESS", resp.getCode());
            assertEquals(25L, resp.getTotal());
            assertEquals(3L, resp.getPageNum());
            assertEquals(10L, resp.getPageSize());
            assertEquals(3L, resp.getPages()); // 25/10 → ceil = 3
            assertEquals(data, resp.getData());
        }

        @Test
        @DisplayName("空数据时 pages 应为 0")
        void emptyData() {
            PageResponse<String> resp = PageResponse.success(0L, 1L, 10L, List.of());
            assertEquals(0L, resp.getPages());
            assertEquals(0L, resp.getTotal());
        }

        @Test
        @DisplayName("error 正常构造")
        void error() {
            PageResponse<String> resp = PageResponse.error("BAD_REQUEST", "参数错误");
            assertEquals("BAD_REQUEST", resp.getCode());
            assertEquals(0L, resp.getTotal());
            assertNull(resp.getData());
        }
    }

    @Nested
    @DisplayName("from(IPageResult) 桥接")
    class FromIPageResult {

        @Test
        @DisplayName("类型安全的 from 桥接")
        void from_Success() {
            List<String> records = List.of("item1", "item2");
            IPageResult src = simpleResult(records, 100L, 1L, 20L);

            PageResponse<String> resp = PageResponse.from(src);

            assertEquals("SUCCESS", resp.getCode());
            assertEquals(100L, resp.getTotal());
            assertEquals(1L, resp.getPageNum());
            assertEquals(20L, resp.getPageSize());
            assertEquals(5L, resp.getPages()); // 100/20
            assertEquals(records, resp.getData());
        }

        @Test
        @DisplayName("records 为 null 时 data 应为空列表")
        void from_withNullRecords() {
            IPageResult src = simpleResult(null, 0L, 1L, 20L);

            PageResponse<String> resp = PageResponse.from(src);

            assertNotNull(resp.getData());
            assertTrue(resp.getData().isEmpty());
        }

        @Test
        @DisplayName("from(null) 应该抛出 NullPointerException")
        void from_withNullSource() {
            assertThrows(NullPointerException.class, () -> PageResponse.from(null));
        }

        @Test
        @DisplayName("fromIPage 类型安全校验")
        void fromIPage_typeMismatch() {
            IPageResult src = simpleResult(List.of("string"), 1L, 1L, 20L);

            // 期望类型 Integer，实际 String —— 应该异常
            assertThrows(ClassCastException.class,
                () -> PageResponse.fromIPage(src, Integer.class)
            );
        }

        @Test
        @DisplayName("fromIPage 正常转换")
        void fromIPage_match() {
            List<Integer> records = List.of(1, 2, 3);
            IPageResult src = simpleResult(records, 50L, 2L, 20L);

            PageResponse<Integer> resp = PageResponse.fromIPage(src, Integer.class);

            assertEquals(50L, resp.getTotal());
            assertEquals(records, resp.getData());
        }
    }
}
