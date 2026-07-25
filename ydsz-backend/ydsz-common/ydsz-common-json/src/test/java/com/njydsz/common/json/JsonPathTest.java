package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.jsonpath.YdszJsonPath;

/**
 * JSONPath 查询测试。
 *
 * @since 1.0.0
 */
class JsonPathTest {

    private static final String JSON = "{\"user\":{\"name\":\"Alice\",\"age\":30},"
            + "\"items\":[{\"id\":1,\"price\":100},{\"id\":2,\"price\":200},{\"id\":3,\"price\":50}]}";

    @Test
    void testSimpleProperty() {
        Object result = YdszJsonPath.get(JSON, "$.user.name");
        assertEquals("Alice", result);
    }

    @Test
    void testNestedProperty() {
        Object result = YdszJsonPath.get(JSON, "$.user.age");
        assertEquals(30, result);
    }

    @Test
    void testArrayIndex() {
        Object result = YdszJsonPath.get(JSON, "$.items[0].id");
        assertEquals(1, result);
    }

    @Test
    void testArrayLastIndex() {
        Object result = YdszJsonPath.get(JSON, "$.items[2].price");
        assertEquals(50, result);
    }

    @Test
    void testArraySlice() {
        YdszJsonPath path = YdszJsonPath.compile("$.items[0:2]");
        List<Object> results = path.getAllValues(JSON);
        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void testWildcard() {
        YdszJsonPath path = YdszJsonPath.compile("$.items[*].id");
        List<Object> results = path.getAllValues(JSON);
        assertNotNull(results);
        assertEquals(3, results.size());
    }

    @Test
    void testArrayFilter() {
        YdszJsonPath path = YdszJsonPath.compile("$.items[?(@.price > 100)]");
        List<Object> results = path.getAllValues(JSON);
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void testRecursiveDescent() {
        YdszJsonPath path = YdszJsonPath.compile("$..id");
        List<Object> results = path.getAllValues(JSON);
        assertNotNull(results);
        assertTrue(results.size() >= 3);
    }

    @Test
    void testCompileInvalidPath() {
        assertThrows(IllegalArgumentException.class, () -> YdszJsonPath.compile(""));
        assertThrows(IllegalArgumentException.class, () -> YdszJsonPath.compile(null));
        assertThrows(IllegalArgumentException.class, () -> YdszJsonPath.compile("user.name"));
    }

    @Test
    void testNonExistentPath() {
        Object result = YdszJsonPath.get(JSON, "$.user.nonexistent");
        assertNull(result);
    }

    @Test
    void testComplexFilterWithAnd() {
        String json = "{\"items\":[{\"id\":1,\"status\":\"active\",\"age\":25},"
                + "{\"id\":2,\"status\":\"inactive\",\"age\":30},"
                + "{\"id\":3,\"status\":\"active\",\"age\":35}]}";
        YdszJsonPath path = YdszJsonPath.compile("$.items[?(@.status == 'active' && @.age >= 30)]");
        List<Object> results = path.getAllValues(json);
        assertNotNull(results);
        assertEquals(1, results.size());
    }
}
