package com.njydsz.pmis.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageResult 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("PageResult 测试")
class PageResultTest {

    @Test
    @DisplayName("无参构造 - 应初始化空列表，其他字段为默认值 0")
    void noArgsConstructor_shouldInitializeEmptyList() {
        PageResult<String> result = new PageResult<>();

        assertNotNull(result.getList());
        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getPage());
        assertEquals(0, result.getSize());
        assertEquals(0, result.getPages());
    }

    @Test
    @DisplayName("全参构造 - 应正确设置所有字段并自动计算总页数")
    void allArgsConstructor_shouldSetFieldsAndCalculatePages() {
        List<String> list = Arrays.asList("a", "b", "c");
        long total = 25;
        long page = 2;
        long size = 10;

        PageResult<String> result = new PageResult<>(list, total, page, size);

        assertEquals(list, result.getList());
        assertEquals(total, result.getTotal());
        assertEquals(page, result.getPage());
        assertEquals(size, result.getSize());
        assertEquals(3, result.getPages()); // ceil(25/10) = 3
    }

    @Test
    @DisplayName("全参构造 - size 为 0 时 pages 应为 0")
    void allArgsConstructor_withZeroSize() {
        PageResult<String> result = new PageResult<>(Arrays.asList("a"), 100, 1, 0);

        assertEquals(0, result.getPages());
    }

    @Test
    @DisplayName("全参构造 - total 为 0 时 pages 应为 0")
    void allArgsConstructor_withZeroTotal() {
        PageResult<String> result = new PageResult<>(Collections.emptyList(), 0, 1, 10);

        assertEquals(0, result.getPages());
    }

    @Test
    @DisplayName("静态工厂 empty - 应返回空列表，默认第 1 页每页 10 条")
    void empty_shouldReturnCorrectDefaults() {
        PageResult<String> result = PageResult.empty();

        assertNotNull(result.getList());
        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(0, result.getPages());
    }

    @Test
    @DisplayName("静态工厂 of - 应与全参构造等价")
    void of_shouldBeEquivalentToConstructor() {
        List<String> list = Arrays.asList("x", "y");
        long total = 50;
        long page = 3;
        long size = 20;

        PageResult<String> result = PageResult.of(list, total, page, size);

        assertEquals(list, result.getList());
        assertEquals(total, result.getTotal());
        assertEquals(page, result.getPage());
        assertEquals(size, result.getSize());
        assertEquals(3, result.getPages()); // ceil(50/20) = 3
    }

    @Test
    @DisplayName("ofPage - 传入 null 应返回空结果")
    void ofPage_shouldReturnEmptyForNull() {
        PageResult<String> result = PageResult.ofPage(null);

        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    @DisplayName("ofPage - 传入有效 Page 应正确转换")
    void ofPage_shouldConvertFromMybatisPlusPage() {
        Page<String> page = new Page<>(2, 15);
        page.setRecords(Arrays.asList("r1", "r2", "r3"));
        page.setTotal(30);

        PageResult<String> result = PageResult.ofPage(page);

        assertEquals(3, result.getList().size());
        assertEquals(30, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(15, result.getSize());
        assertEquals(2, result.getPages()); // ceil(30/15) = 2
    }
}