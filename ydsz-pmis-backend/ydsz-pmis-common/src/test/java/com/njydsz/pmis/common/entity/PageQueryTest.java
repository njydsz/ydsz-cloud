package com.njydsz.pmis.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageQuery 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("PageQuery 测试")
class PageQueryTest {

    // ==================== 默认值 ====================

    @Test
    @DisplayName("默认值 - page 应为 1，size 应为 10")
    void defaultValues_shouldBeCorrect() {
        PageQuery pageQuery = new PageQuery();
        assertEquals(1, pageQuery.getPage());
        assertEquals(10, pageQuery.getSize());
    }

    @Test
    @DisplayName("默认值 - orderDir 应为 desc")
    void defaultOrderDir_shouldBeDesc() {
        PageQuery pageQuery = new PageQuery();
        assertEquals("desc", pageQuery.getOrderDir());
    }

    @Test
    @DisplayName("默认值 - keyword 和 orderBy 应为 null")
    void defaultNullableFields_shouldBeNull() {
        PageQuery pageQuery = new PageQuery();
        assertNull(pageQuery.getKeyword());
        assertNull(pageQuery.getOrderBy());
    }

    // ==================== offset() ====================

    @Test
    @DisplayName("offset - 默认值(page=1, size=10) 应返回 0")
    void offset_shouldReturnZeroForDefaults() {
        PageQuery pageQuery = new PageQuery();
        assertEquals(0, pageQuery.offset());
    }

    @Test
    @DisplayName("offset - page=2, size=10 应返回 10")
    void offset_shouldReturnCorrectForPage2() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(2);
        pageQuery.setSize(10);
        assertEquals(10, pageQuery.offset());
    }

    @Test
    @DisplayName("offset - page=3, size=20 应返回 40")
    void offset_shouldReturnCorrectForPage3Size20() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(3);
        pageQuery.setSize(20);
        assertEquals(40, pageQuery.offset());
    }

    @Test
    @DisplayName("offset - page=0 应自动修正为 page=1，返回 0")
    void offset_shouldClampPageTo1() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(0);
        pageQuery.setSize(10);
        assertEquals(0, pageQuery.offset());
    }

    @Test
    @DisplayName("offset - page 为负数应自动修正为 1")
    void offset_shouldClampNegativePage() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(-5);
        pageQuery.setSize(10);
        assertEquals(0, pageQuery.offset());
    }

    @Test
    @DisplayName("offset - size=0 应自动修正为 size=1")
    void offset_shouldClampSizeTo1() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(2);
        pageQuery.setSize(0);
        assertEquals(1, pageQuery.offset()); // (2-1)*1 = 1
    }

    @Test
    @DisplayName("offset - size 超过 MAX_SIZE 应被限制")
    void offset_shouldClampSizeToMax() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(1);
        pageQuery.setSize(500);
        assertEquals(0, pageQuery.offset()); // (1-1)*200 = 0
    }

    @Test
    @DisplayName("offset - size 为负数应自动修正为 1")
    void offset_shouldClampNegativeSize() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(2);
        pageQuery.setSize(-10);
        assertEquals(1, pageQuery.offset()); // (2-1)*1 = 1
    }

    // ==================== MAX_SIZE 常量 ====================

    @Test
    @DisplayName("MAX_SIZE 常量应为 200")
    void maxSize_shouldBe200() {
        assertEquals(200, PageQuery.MAX_SIZE);
    }

    // ==================== setter/getter ====================

    @Test
    @DisplayName("setter/getter - page 和 size 应正确设置和获取")
    void setterGetter_shouldWorkForPageAndSize() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPage(5);
        pageQuery.setSize(50);
        assertEquals(5, pageQuery.getPage());
        assertEquals(50, pageQuery.getSize());
    }

    @Test
    @DisplayName("setter/getter - keyword 和 orderBy 应正确设置和获取")
    void setterGetter_shouldWorkForKeywordAndOrderBy() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setKeyword("测试");
        pageQuery.setOrderBy("create_time");
        assertEquals("测试", pageQuery.getKeyword());
        assertEquals("create_time", pageQuery.getOrderBy());
    }

    @Test
    @DisplayName("setter/getter - orderDir 应正确设置和获取")
    void setterGetter_shouldWorkForOrderDir() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setOrderDir("asc");
        assertEquals("asc", pageQuery.getOrderDir());
    }

    // ==================== 序列化 ====================

    @Test
    @DisplayName("PageQuery 应实现 Serializable")
    void pageQuery_shouldImplementSerializable() {
        PageQuery pageQuery = new PageQuery();
        assertInstanceOf(java.io.Serializable.class, pageQuery);
    }
}