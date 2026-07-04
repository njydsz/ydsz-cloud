package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SortBy 单元测试。
 *
 * <p>验证方法引用 → 字段名的类型安全推导逻辑。
 */
@DisplayName("SortBy 类型安全排序工厂测试")
class SortByTest {

    /** 测试用 POJO：模拟实体类 */
    static class User {
        private Long id;
        private String name;
        private LocalDateTime createdAt;
        private Boolean active;
        private String emailAddress;

        public Long getId() { return id; }
        public String getName() { return name; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public Boolean isActive() { return active; }
        public String getEmailAddress() { return emailAddress; }
    }

    @Test
    @DisplayName("getter 方法引用 - 应正确推导字段名 createdAt")
    void shouldResolveFieldNameFromGetGetter() {
        Sort sort = SortBy.desc(User::getCreatedAt);
        Sort.Order order = sort.getOrderFor("createdAt");
        assertNotNull(order, "Sort 应包含 createdAt 列");
        assertSame(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("is 开头 boolean getter - 应正确推导字段名 active")
    void shouldResolveFieldNameFromIsGetter() {
        Sort sort = SortBy.asc(User::isActive);
        Sort.Order order = sort.getOrderFor("active");
        assertNotNull(order, "Sort 应包含 active 列");
        assertSame(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    @DisplayName("驼峰 getter - 多单词字段名应正确转换")
    void shouldResolveCamelCaseFieldName() {
        Sort sort = SortBy.desc(User::getEmailAddress);
        Sort.Order order = sort.getOrderFor("emailAddress");
        assertNotNull(order, "Sort 应包含 emailAddress 列");
    }

    @Test
    @DisplayName("by 显式方向 - 应保留指定方向")
    void byWithExplicitDirection() {
        Sort sort = SortBy.by(Sort.Direction.ASC, User::getName);
        Sort.Order order = sort.getOrderFor("name");
        assertNotNull(order);
        assertSame(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    @DisplayName("order 返回 Sort.Order - 字段名应正确")
    void orderShouldReturnSortOrder() {
        Sort.Order order = SortBy.orderDesc(User::getId);
        assertEquals("id", order.getProperty());
        assertSame(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("desc/asc 语法糖 - 字段名应一致")
    void descAndAscSugarMethods() {
        Sort.Order desc = SortBy.orderDesc(User::getCreatedAt);
        Sort.Order asc = SortBy.orderAsc(User::getCreatedAt);
        assertEquals("createdAt", desc.getProperty());
        assertEquals("createdAt", asc.getProperty());
        assertSame(Sort.Direction.DESC, desc.getDirection());
        assertSame(Sort.Direction.ASC, asc.getDirection());
    }

    @Test
    @DisplayName("Sort.and 链式 - 多列排序可串联")
    void shouldSupportSortChaining() {
        Sort sort = SortBy.asc(User::getName)
                .and(SortBy.desc(User::getCreatedAt));
        assertNotNull(sort.getOrderFor("name"));
        assertNotNull(sort.getOrderFor("createdAt"));
        assertTrue(sort.iterator().hasNext());
    }

    @Test
    @DisplayName("null getter - 应抛出 IllegalArgumentException")
    void shouldThrowOnNullGetter() {
        assertThrows(IllegalArgumentException.class, () -> SortBy.by(Sort.Direction.DESC, null));
    }

    @Test
    @DisplayName("Sort 字段名 - 排序结果应与 getter 字段名一致")
    void sortFieldNameShouldMatchGetter() {
        // 验证 Sort.by 与 getOrderFor 的字段名一致性
        Sort sort = SortBy.desc(User::getCreatedAt);
        // 关键点：导出的字段名应为 "createdAt"，与 PageRequest 配合时 ES/MyBatis-Plus 都能识别
        Sort.Order order = sort.getOrderFor("createdAt");
        assertNotNull(order);
        // 不存在的字段应返回 null（Spring Data 不会自动补全）
        assertNull(sort.getOrderFor("nonExistentField"));
    }
}
