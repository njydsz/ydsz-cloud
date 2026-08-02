package com.njydsz.common.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSONPath 查询综合测试。
 */
@DisplayName("JSONPath 查询测试")
class YdszJsonPathTest {

    private static final String SAMPLE_JSON = "{"
        + "\"store\":{"
        + "\"name\":\"MyStore\","
        + "\"location\":\"Beijing\","
        + "\"books\":["
        + "{\"title\":\"Java入门\",\"price\":29.9,\"author\":\"Alice\"},"
        + "{\"title\":\"Spring实战\",\"price\":59.8,\"author\":\"Bob\"},"
        + "{\"title\":\"算法导论\",\"price\":99.0,\"author\":\"Charlie\"}"
        + "],"
        + "\"employees\":["
        + "{\"name\":\"张三\",\"age\":30},"
        + "{\"name\":\"李四\",\"age\":25}"
        + "]"
        + "}}";

    @Nested
    @DisplayName("基础路径查询")
    class BasicPathTests {

        @Test
        @DisplayName("$.store.name — 简单对象路径")
        void simplePath() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.name");
            assertEquals("MyStore", result);
        }

        @Test
        @DisplayName("$.store.location — 另一属性")
        void anotherProperty() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.location");
            assertEquals("Beijing", result);
        }

        @Test
        @DisplayName("$.store — 返回子对象 Map")
        void subObject() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store");
            assertTrue(result instanceof Map, "应返回 Map 对象");
            @SuppressWarnings("unchecked")
            Map<String, Object> store = (Map<String, Object>) result;
            assertEquals("MyStore", store.get("name"));
        }
    }

    @Nested
    @DisplayName("数组索引查询")
    class ArrayIndexTests {

        @Test
        @DisplayName("$.store.books[0] — 第一个元素")
        void firstElement() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[0]");
            assertTrue(result instanceof Map);
        }

        @Test
        @DisplayName("$.store.books[0].title — 数组元素的属性")
        void arrayElementProperty() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[0].title");
            assertEquals("Java入门", result);
        }

        @Test
        @DisplayName("$.store.books[2].price — 最后一个元素的数字属性")
        void lastElementNumericProperty() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[2].price");
            assertEquals(99.0, ((Number) result).doubleValue(), 0.001);
        }
    }

    @Nested
    @DisplayName("递归下降查询")
    class RecursiveDescentTests {

        @Test
        @DisplayName("$..name — 递归查找所有 name 字段")
        void recursiveName() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$..name");
            assertNotNull(result);
            // 应找到 store.name=MyStore, 两个 employee.name
        }

        @Test
        @DisplayName("$..author — 递归查找所有 author 字段")
        void recursiveAuthor() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$..author");
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("数组切片查询")
    class ArraySliceTests {

        @Test
        @DisplayName("$.store.books[0:2] — 前两个元素")
        void sliceFirstTwo() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[0:2]");
            assertTrue(result instanceof List);
            assertEquals(2, ((List<?>) result).size());
        }
    }

    @Nested
    @DisplayName("通配符查询")
    class WildcardTests {

        @Test
        @DisplayName("$.store.books[*].title — 所有书的标题")
        void allBookTitles() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[*].title");
            assertTrue(result instanceof List);
            List<?> titles = (List<?>) result;
            assertEquals(3, titles.size());
            assertEquals("Java入门", titles.get(0));
        }
    }

    @Nested
    @DisplayName("过滤器查询")
    class FilterTests {

        @Test
        @DisplayName("$.store.books[?(@.price > 50)] — 价格大于 50 的书")
        void filterPriceGreaterThan() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.books[?(@.price > 50)]");
            assertNotNull(result);
            assertTrue(result instanceof List);
            assertEquals(2, ((List<?>) result).size(), "应有 2 本书价格 > 50");
        }

        @Test
        @DisplayName("$.store.employees[?(@.age >= 30)] — 年龄 >= 30 的员工")
        void filterAgeGreaterEqual() {
            Object result = YdszJson.getByPath(SAMPLE_JSON, "$.store.employees[?(@.age >= 30)]");
            assertTrue(result instanceof List);
            assertEquals(1, ((List<?>) result).size());
        }
    }
}
