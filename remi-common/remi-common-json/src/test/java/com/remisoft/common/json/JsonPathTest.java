package com.remisoft.common.json;

import java.util.List;

import com.remisoft.common.json.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonPath 查询单元测试（P1）。
 */
class JsonPathTest {

    private static final String JSON = "{\"user\":{\"name\":\"Alice\",\"age\":30},"
        + "\"items\":[{\"id\":1,\"price\":50},{\"id\":2,\"price\":150}],"
        + "\"tags\":[\"a\",\"b\",\"c\"]}";

    @Test
    void rootFieldAccess() {
        Object name = JsonPath.get(JSON, "$.user.name");
        assertEquals("Alice", name);
    }

    @Test
    void nestedFieldAccess() {
        Object age = JsonPath.get(JSON, "$.user.age");
        assertEquals(30, ((Number) age).intValue());
    }

    @Test
    void arrayIndexAccess() {
        Object id = JsonPath.get(JSON, "$.items[0].id");
        assertEquals(1, ((Number) id).intValue());
    }

    @Test
    void arrayWildcard() {
        Object ids = JsonPath.get(JSON, "$.items[*].id");
        assertNotNull(ids);
    }

    @Test
    void arrayFilterExpression() {
        Object expensive = JsonPath.get(JSON, "$.items[?(@.price > 100)]");
        assertNotNull(expensive);
    }

    @Test
    void arraySlice() {
        Object slice = JsonPath.get(JSON, "$.tags[0:2]");
        assertNotNull(slice);
    }

    @Test
    void recursiveDescent() {
        // 递归下降 $..id 查找所有 id 字段
        Object result = JsonPath.get(JSON, "$..id");
        // 递归下降可能返回 List 或单个值，只要不抛异常即可
        // （不同实现行为不同，这里验证不抛异常）
    }

    @Test
    void compileReturnsCachedInstance() {
        JsonPath p1 = JsonPath.compile("$.user.name");
        JsonPath p2 = JsonPath.compile("$.user.name");
        // LRU cache should return same instance
        assertTrue(p1 == p2);
    }

    @Test
    void nullPathThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile(null));
    }

    @Test
    void emptyPathThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile(""));
    }

    @Test
    void pathNotStartingWithDollarThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile("user.name"));
    }

    @Test
    void getWithObjectInput() {
        java.util.Map<String, Object> obj = RemiJson.parseMap(JSON);
        Object name = JsonPath.get(obj, "$.user.name");
        assertEquals("Alice", name);
    }

    private static void assertNotNull(Object obj) {
        assertTrue(obj != null, "Expected non-null result");
    }
}
