package com.njydsz.common.jdbc.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.PageResponse;

/**
 * {@link PageResponses} 单元测试
 *
 * <p>覆盖 IPage → PageResponse 转换、null 处理、映射转换等行为。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@DisplayName("PageResponses MyBatis-Plus 分页转换测试")
class PageResponsesTest {

    @Test
    @DisplayName("IPage 转换为成功分页响应")
    void success_convertsIpage() {
        IPage<String> page = new Page<>(1, 20);
        page.setTotal(100);
        page.setRecords(List.of("a", "b"));

        PageResponse<List<String>> resp = PageResponses.success(page);
        assertTrue(resp.isSuccess());
        assertEquals(100L, resp.getTotal());
        assertEquals(1L, resp.getPageNum());
        assertEquals(20L, resp.getPageSize());
        assertEquals(5L, resp.getPages());
        assertEquals(List.of("a", "b"), resp.getData());
    }

    @Test
    @DisplayName("null IPage 返回空分页响应")
    void success_nullPage() {
        PageResponse<List<String>> resp = PageResponses.success(null);
        assertTrue(resp.isSuccess());
        assertEquals(0L, resp.getTotal());
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
    }

    @Test
    @DisplayName("records 为 null 时返回空列表")
    void success_nullRecords() {
        IPage<String> page = new Page<>(1, 10);
        page.setTotal(5);
        page.setRecords(null);

        PageResponse<List<String>> resp = PageResponses.success(page);
        assertNotNull(resp.getData());
        assertTrue(resp.getData().isEmpty());
        assertEquals(5L, resp.getTotal());
    }

    @Test
    @DisplayName("带映射函数的转换（DO → VO）")
    void success_withMapper() {
        IPage<String> page = new Page<>(2, 10);
        page.setTotal(25);
        page.setRecords(List.of("u1", "u2"));

        PageResponse<List<UserVO>> resp = PageResponses.success(page, UserVO::new);
        assertEquals(3L, resp.getPages());
        assertEquals(List.of(new UserVO("u1"), new UserVO("u2")), resp.getData());
    }

    @Test
    @DisplayName("带映射函数时 null IPage 返回空分页")
    void success_withMapper_nullPage() {
        PageResponse<List<UserVO>> resp = PageResponses.success(null, UserVO::new);
        assertTrue(resp.getData().isEmpty());
    }

    /**
     * 测试用 VO。
     */
    record UserVO(String id) {
    }
}
