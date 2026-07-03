package com.njydsz.pmis.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CursorPageQuery 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CursorPageQuery 测试")
class CursorPageQueryTest {

    @Test
    @DisplayName("默认值 - size 应为 20，cursor 应为 null")
    void defaultValues_shouldBeCorrect() {
        CursorPageQuery query = new CursorPageQuery();

        assertEquals(20, query.getSize());
        assertNull(query.getCursor());
    }

    @Test
    @DisplayName("safeSize - 正常 size 应返回原值")
    void safeSize_shouldReturnNormalSize() {
        CursorPageQuery query = new CursorPageQuery();
        query.setSize(10);

        assertEquals(10, query.safeSize());
    }

    @Test
    @DisplayName("safeSize - size 为 0 应 clamp 到 1")
    void safeSize_shouldClampZeroToOne() {
        CursorPageQuery query = new CursorPageQuery();
        query.setSize(0);

        assertEquals(1, query.safeSize());
    }

    @Test
    @DisplayName("safeSize - size 为负数应 clamp 到 1")
    void safeSize_shouldClampNegativeToOne() {
        CursorPageQuery query = new CursorPageQuery();
        query.setSize(-5);

        assertEquals(1, query.safeSize());
    }

    @Test
    @DisplayName("safeSize - size 超过 MAX_SIZE 应 clamp 到 200")
    void safeSize_shouldClampOverMax() {
        CursorPageQuery query = new CursorPageQuery();
        query.setSize(500);

        assertEquals(200, query.safeSize());
    }

    @Test
    @DisplayName("safeSize - size 恰好等于 MAX_SIZE 应返回 200")
    void safeSize_shouldReturnExactMax() {
        CursorPageQuery query = new CursorPageQuery();
        query.setSize(200);

        assertEquals(200, query.safeSize());
    }

    @Test
    @DisplayName("MAX_SIZE 常量 - 应等于 200")
    void maxSize_shouldBe200() {
        assertEquals(200, CursorPageQuery.MAX_SIZE);
    }
}