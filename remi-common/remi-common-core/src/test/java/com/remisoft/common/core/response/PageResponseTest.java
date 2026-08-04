package com.remisoft.common.core.response;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.remisoft.common.core.constant.PageConstants;
import com.remisoft.common.core.constant.HeaderConstants;

/**
 * {@link PageResponse} 单元测试
 *
 * <p>覆盖分页计算、边界条件（0 条/负值/超大总数）、hasNext/hasPrevious、
 * 空响应、基本类型便捷重载等行为。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("PageResponse 分页响应体测试")
class PageResponseTest {

    @BeforeEach
    void setUp() {
        MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "page-trace");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("success 正确计算总页数")
    void success_calcPages() {
        PageResponse<List<String>> resp = PageResponse.success(100L, 1L, 20L, List.of("a"));
        assertTrue(resp.isSuccess());
        assertEquals(100L, resp.getTotal());
        assertEquals(1L, resp.getPageNum());
        assertEquals(20L, resp.getPageSize());
        assertEquals(5L, resp.getPages());
    }

    @Test
    @DisplayName("总记录数不能整除时向上取整")
    void calcPages_ceil() {
        PageResponse<Void> resp = PageResponse.success(101L, 1L, 20L, null);
        assertEquals(6L, resp.getPages());
    }

    @Test
    @DisplayName("total 为 0 时 pages 为 0")
    void calcPages_zeroTotal() {
        PageResponse<Void> resp = PageResponse.success(0L, 1L, 20L, null);
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("total 为 null 时 pages 为 0")
    void calcPages_nullTotal() {
        PageResponse<Void> resp = PageResponse.of("A00000", "ok", null, 1L, 20L, null);
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("pageSize 为 null 时 pages 为 0")
    void calcPages_nullPageSize() {
        PageResponse<Void> resp = PageResponse.of("A00000", "ok", 100L, 1L, null, null);
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("pageSize 为 0 时 pages 为 0（避免除零）")
    void calcPages_zeroPageSize() {
        PageResponse<Void> resp = PageResponse.of("A00000", "ok", 100L, 1L, 0L, null);
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("pageSize 为负数时 pages 为 0")
    void calcPages_negativePageSize() {
        PageResponse<Void> resp = PageResponse.of("A00000", "ok", 100L, 1L, -5L, null);
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("超大总数不溢出（整数除法，无浮点精度问题）")
    void calcPages_largeTotal() {
        // Long.MAX_VALUE 附近，浮点 double 精度会丢失，整数除法安全
        long total = 9_000_000_000_000_000_000L;
        PageResponse<Void> resp = PageResponse.of("A00000", "ok", total, 1L, 1000L, null);
        assertEquals((total + 999L) / 1000L, resp.getPages());
    }

    @Test
    @DisplayName("基本类型便捷重载 long/int")
    void success_primitiveOverload() {
        PageResponse<List<String>> resp = PageResponse.success(100L, 1, 20, List.of("a"));
        assertEquals(100L, resp.getTotal());
        assertEquals(1L, resp.getPageNum());
        assertEquals(20L, resp.getPageSize());
        assertEquals(5L, resp.getPages());
    }

    @Test
    @DisplayName("fail 返回错误码与消息")
    void fail() {
        PageResponse<Void> resp = PageResponse.fail("A10002", "参数校验失败");
        assertFalse(resp.isSuccess());
        assertEquals("A10002", resp.getCode());
        assertEquals("参数校验失败", resp.getMsg());
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("fail(msg) 使用默认错误码")
    void fail_singleMsg() {
        PageResponse<Void> resp = PageResponse.fail("出错了");
        assertEquals(BaseResponse.UNKNOWN_CODE, resp.getCode());
        assertEquals("出错了", resp.getMsg());
    }

    @Test
    @DisplayName("empty() 返回成功空分页")
    void empty() {
        PageResponse<Void> resp = PageResponse.empty();
        assertTrue(resp.isSuccess());
        assertEquals(0L, resp.getTotal());
        assertEquals(1L, resp.getPageNum());
        assertEquals((long) PageConstants.getDefaultPageSize(), resp.getPageSize());
        assertEquals(0L, resp.getPages());
    }

    @Test
    @DisplayName("hasNext 在当前页小于总页数时为 true")
    void hasNext_true() {
        PageResponse<Void> resp = PageResponse.success(100L, 1L, 20L, null);
        assertTrue(resp.hasNext());
    }

    @Test
    @DisplayName("hasNext 在最后一页时为 false")
    void hasNext_lastPage() {
        PageResponse<Void> resp = PageResponse.success(100L, 5L, 20L, null);
        assertFalse(resp.hasNext());
    }

    @Test
    @DisplayName("hasNext 在 pages 为 0 时为 false")
    void hasNext_noPages() {
        PageResponse<Void> resp = PageResponse.success(0L, 1L, 20L, null);
        assertFalse(resp.hasNext());
    }

    @Test
    @DisplayName("hasPrevious 在第一页时为 false")
    void hasPrevious_firstPage() {
        PageResponse<Void> resp = PageResponse.success(100L, 1L, 20L, null);
        assertFalse(resp.hasPrevious());
    }

    @Test
    @DisplayName("hasPrevious 在第二页时为 true")
    void hasPrevious_secondPage() {
        PageResponse<Void> resp = PageResponse.success(100L, 2L, 20L, null);
        assertTrue(resp.hasPrevious());
    }

    @Test
    @DisplayName("全参数构造器可用")
    void allArgsConstructor() {
        PageResponse<String> resp = new PageResponse<>("A00000", "ok", 10L, 1L, 10L, 1L, "data");
        assertEquals("data", resp.getData());
        assertEquals(1L, resp.getPages());
    }

    @Test
    @DisplayName("继承 BaseResponse 的能力可用")
    void inheritBaseResponse() {
        PageResponse<Void> resp = PageResponse.success(10L, 1L, 10L, null);
        assertNotNull(resp.getTimestamp());
        assertNotNull(resp.getTraceId());
    }
}
