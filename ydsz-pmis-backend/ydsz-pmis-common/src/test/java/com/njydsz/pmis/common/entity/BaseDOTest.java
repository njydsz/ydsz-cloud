package com.njydsz.pmis.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseDO 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("BaseDO 测试")
class BaseDOTest {

    /**
     * 具体子类，用于测试继承的字段
     */
    static class TestEntity extends BaseDO {
    }

    @Test
    @DisplayName("setter/getter - createdBy 应正确存取")
    void createdBy_shouldSetAndGet() {
        TestEntity entity = new TestEntity();
        entity.setCreatedBy(100L);
        assertEquals(100L, entity.getCreatedBy());
    }

    @Test
    @DisplayName("setter/getter - createdAt 应正确存取")
    void createdAt_shouldSetAndGet() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    @DisplayName("setter/getter - updatedBy 应正确存取（雪花字符串）")
    void updatedBy_shouldSetAndGet() {
        TestEntity entity = new TestEntity();
        entity.setUpdatedBy("200");
        assertEquals("200", entity.getUpdatedBy());
    }

    @Test
    @DisplayName("setter/getter - updatedAt 应正确存取")
    void updatedAt_shouldSetAndGet() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("setter/getter - deleted 应正确存取")
    void deleted_shouldSetAndGet() {
        TestEntity entity = new TestEntity();
        entity.setDeleted(1);
        assertEquals(1, entity.getDeleted());

        entity.setDeleted(0);
        assertEquals(0, entity.getDeleted());
    }

    @Test
    @DisplayName("setter/getter - 所有字段默认值应为 null（Lombok @Data）")
    void defaultValues_shouldBeNull() {
        TestEntity entity = new TestEntity();

        assertNull(entity.getCreatedBy());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedBy());
        assertNull(entity.getUpdatedAt());
        assertNull(entity.getDeleted());
    }
}