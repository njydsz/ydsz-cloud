package com.njydsz.common.domain.query;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.core.constant.PageConstants;

/**
 * PageQuery 单元测试
 *
 * <p>重点测试：
 * <ul>
 *   <li>SQL 注入防护（setOrderBy 参数过滤）</li>
 *   <li>分页计算（offset、endRow、hasNext）</li>
 *   <li>搜索关键字截断和转义</li>
 *   <li>排序白名单机制</li>
 *   <li>排序 SQL 片段构建</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class PageQueryTest {

    @Test
    void testSqlInjectionPrevention_byOrderByField() {
        PageQuery query = new PageQuery();
        query.setOrderBy("; DROP TABLE sys_user; --");
        assertTrue(query.getOrderItems().isEmpty());
        assertTrue(query.getOrderSql().isEmpty());
    }

    @Test
    void testSqlInjectionPrevention_byOrderByString() {
        PageQuery query = new PageQuery();
        query.setOrderBy("id DESC; DROP TABLE sys_user");
        // 整个字符串包含分号和空格，不通过 SAFE_COLUMN_PATTERN 校验
        assertTrue(query.getOrderItems().isEmpty());
    }

    @Test
    void testSqlInjectionPrevention_byComplexString() {
        PageQuery query = new PageQuery();
        query.setOrderBy("(SELECT * FROM sys_user) DESC; DROP TABLE sys_user");
        assertTrue(query.getOrderItems().isEmpty());
    }

    @Test
    void testSqlInjectionPrevention_byAllowedField() {
        PageQuery query = new PageQuery();
        query.setOrderBy("created_at DESC");
        assertEquals(1, query.getOrderItems().size());
        assertEquals("created_at DESC", query.getOrderItems().get(0));
        assertEquals("ORDER BY created_at DESC", query.getOrderSql());
    }

    @Test
    void testSqlInjectionPrevention_byAllowedFieldWithModifier() {
        PageQuery query = new PageQuery();
        query.setOrderBy("created_at DESC");
        query.addAscOrder("id");
        assertEquals(2, query.getOrderItems().size());
        assertEquals("ORDER BY created_at DESC, id ASC", query.getOrderSql());
    }

    @Test
    void testSearchKeyTruncation() {
        String longInput = "a".repeat(300);
        PageQuery query = new PageQuery();
        query.setSearchKey(longInput);
        assertEquals(PageQuery.MAX_SEARCH_KEY_LENGTH, query.getSearchKey().length());
    }

    @Test
    void testSearchKeyEscaping() {
        PageQuery query = new PageQuery();
        query.setSearchKey("test\" OR 1=1");
        assertEquals("test\\\" OR 1=1", query.getSearchKey());
    }

    @Test
    void testOffsetCalculation() {
        assertEquals(0, PageQuery.of(1, 10).getOffset());
        assertEquals(10, PageQuery.of(2, 10).getOffset());
        assertEquals(20, PageQuery.of(3, 10).getOffset());
        assertEquals(9999000000L, PageQuery.of(99990001, 100).getOffsetLong());

        ArithmeticException ex = assertThrows(ArithmeticException.class,
                () -> PageQuery.of(99999999999L, 100).getOffset());
        assertTrue(ex.getMessage().contains("exceeds Integer.MAX_VALUE"));
    }

    @Test
    void testHasPrevious() {
        assertFalse(PageQuery.of(1, 10).hasPrevious());
        assertFalse(PageQuery.of(1, 10).hasNext(5));
        assertTrue(PageQuery.of(1, 10).hasNext(15));
    }

    @Test
    void testGetOrderSql() {
        PageQuery query = new PageQuery();
        query.addAscOrder("created_at");
        query.addDescOrder("id");
        assertEquals("ORDER BY created_at ASC, id DESC", query.getOrderSql());
    }

    @Test
    void testGetOrderSql_emptyList() {
        PageQuery query = new PageQuery();
        assertTrue(query.getOrderSql().isEmpty());
    }

    @Test
    void testGetOrderSql_singleItem() {
        PageQuery query = new PageQuery();
        query.addDescOrder("id");
        assertEquals("ORDER BY id DESC", query.getOrderSql());
    }

    @Test
    void testSearchKeyValidation_nullInput() {
        PageQuery query = new PageQuery();
        query.setSearchKey(null);
        assertNull(query.getSearchKey());
        assertFalse(query.hasSearchKey());
    }

    @Test
    void testSearchKeyValidation_emptyInput() {
        PageQuery query = new PageQuery();
        query.setSearchKey("   ");
        assertNull(query.getSearchKey());
        assertFalse(query.hasSearchKey());
    }

    @Test
    void testStatusValidation() {
        PageQuery query = new PageQuery();
        query.setStatus("active");
        assertTrue(query.hasStatus());
    }

    @Test
    void testEffectivePageNum() {
        PageQuery query = PageQuery.builder()
                .pageNum(-5)
                .pageSize(50)
                .build();
        assertEquals(1, query.getEffectivePageNum());

        query.setPageNum(0);
        assertEquals(1, query.getEffectivePageNum());
    }

    @Test
    void testEffectivePageSize_normalization() {
        PageQuery query = new PageQuery();
        query.setPageSize(0);
        assertEquals(PageConstants.getDefaultPageSize(), query.getEffectivePageSize());

        query.setPageSize(PageConstants.getMaxPageSize() + 10);
        assertEquals(PageConstants.getMaxPageSize(), query.getEffectivePageSize());

        query.setPageSize(-10);
        assertEquals(PageConstants.getDefaultPageSize(), query.getEffectivePageSize());
    }
}
