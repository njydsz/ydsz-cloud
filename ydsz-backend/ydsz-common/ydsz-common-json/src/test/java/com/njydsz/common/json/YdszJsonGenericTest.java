package com.njydsz.common.json;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.type.JsonType;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 泛型与嵌套结构综合测试。
 */
@DisplayName("泛型与嵌套结构测试")
class YdszJsonGenericTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    // ==================== List 泛型 ====================

    @Nested
    @DisplayName("List 泛型")
    class ListGenericTests {

        @Test
        @DisplayName("List<Integer> 往返")
        void integerListRoundTrip() {
            List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
            String json = YdszJson.toJson(list);
            List<Integer> restored = YdszJson.parseArray(json, Integer.class);

            assertNotNull(restored);
            assertEquals(5, restored.size());
            assertEquals(1, restored.get(0));
            assertEquals(5, restored.get(4));
        }

        @Test
        @DisplayName("List<String> 往返")
        void stringListRoundTrip() {
            List<String> list = Arrays.asList("a", "b", "c");
            String json = YdszJson.toJson(list);
            List<String> restored = YdszJson.parseArray(json, String.class);

            assertEquals(3, restored.size());
            assertEquals("c", restored.get(2));
        }

        @Test
        @DisplayName("空 List 序列化/反序列化")
        void emptyListRoundTrip() {
            List<Integer> empty = Collections.emptyList();
            String json = YdszJson.toJson(empty);
            assertEquals("[]", json);

            List<Integer> restored = YdszJson.parseArray("[]", Integer.class);
            assertNotNull(restored);
            assertTrue(restored.isEmpty());
        }

        @Test
        @DisplayName("List<Object> 混合类型")
        void mixedTypeList() {
            List<Object> list = YdszJson.parseArray("[1,\"hello\",true,3.14]");
            assertEquals(4, list.size());
            assertEquals(1, ((Number) list.get(0)).intValue());
            assertEquals("hello", list.get(1));
            assertEquals(true, list.get(2));
        }

        @Test
        @DisplayName("JsonType 获取泛型 List")
        void jsonTypeList() {
            String json = "[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]";
            JsonType<List<TestBean>> typeRef = new JsonType<List<TestBean>>() {};
            List<TestBean> list = YdszJson.toObject(json, typeRef);

            assertNotNull(list);
            assertEquals(2, list.size());
            assertEquals(1, list.get(0).getId());
            assertEquals("b", list.get(1).getName());
        }
    }

    // ==================== Map 泛型 ====================

    @Nested
    @DisplayName("Map 泛型")
    class MapGenericTests {

        @Test
        @DisplayName("Map<String, Object> 往返")
        void stringObjectMapRoundTrip() {
            Map<String, Object> map = YdszJson.parseMap("{\"a\":1,\"b\":\"x\",\"c\":true}");
            assertEquals(3, map.size());
            assertEquals(1, ((Number) map.get("a")).intValue());
            assertEquals("x", map.get("b"));
            assertEquals(true, map.get("c"));
        }

        @Test
        @DisplayName("嵌套 Map 解析")
        void nestedMap() {
            Map<String, Object> map = YdszJson.parseMap(
                "{\"user\":{\"id\":5,\"name\":\"zoe\"},\"tags\":[\"a\",\"b\"]}");

            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) map.get("user");
            assertEquals(5, ((Number) user.get("id")).intValue());
            assertEquals("zoe", user.get("name"));

            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) map.get("tags");
            assertEquals(2, tags.size());
        }

        @Test
        @DisplayName("空 Map")
        void emptyMap() {
            Map<String, Object> map = YdszJson.parseMap("{}");
            assertNotNull(map);
            assertTrue(map.isEmpty());
        }
    }

    // ==================== Set 泛型 ====================

    @Nested
    @DisplayName("Set 泛型")
    class SetGenericTests {

        @Test
        @DisplayName("Set<String> 往返")
        void stringSetRoundTrip() {
            Set<String> set = new LinkedHashSet<>(Arrays.asList("x", "y", "z"));
            String json = YdszJson.toJson(set);

            JsonType<Set<String>> typeRef = new JsonType<Set<String>>() {};
            Set<String> restored = YdszJson.toObject(json, typeRef);

            assertNotNull(restored);
            assertTrue(restored.contains("x"));
            assertTrue(restored.contains("z"));
        }
    }

    // ==================== 数组类型 ====================

    @Nested
    @DisplayName("数组类型")
    class ArrayTypeTests {

        @Test
        @DisplayName("int[] 序列化")
        void intArraySerialize() {
            int[] arr = {1, 2, 3};
            String json = YdszJson.toJson(arr);
            assertEquals("[1,2,3]", json);
        }

        @Test
        @DisplayName("String[] 序列化")
        void stringArraySerialize() {
            String[] arr = {"a", "b"};
            String json = YdszJson.toJson(arr);
            assertTrue(json.contains("\"a\""));
            assertTrue(json.contains("\"b\""));
        }
    }

    // ==================== 嵌套 Bean ====================

    @Nested
    @DisplayName("嵌套 Bean 结构")
    class NestedBeanTests {

        @Test
        @DisplayName("3 层嵌套 Bean 往返")
        void threeLevelNestedRoundTrip() {
            Grandparent gp = new Grandparent();
            gp.name = "grandpa";
            gp.child = new Parent();
            gp.child.name = "parent";
            gp.child.child = new Child();
            gp.child.child.name = "child";
            gp.child.child.age = 5;

            String json = YdszJson.toJson(gp);
            assertTrue(json.contains("grandpa"));
            assertTrue(json.contains("parent"));
            assertTrue(json.contains("child"));

            Grandparent restored = YdszJson.toObject(json, Grandparent.class);
            assertNotNull(restored);
            assertEquals("grandpa", restored.name);
            assertEquals("parent", restored.child.name);
            assertEquals("child", restored.child.child.name);
            assertEquals(5, restored.child.child.age);
        }

        @Test
        @DisplayName("嵌套 null 子对象")
        void nullNestedChild() {
            Grandparent gp = new Grandparent();
            gp.name = "lonely";

            String json = YdszJson.toJson(gp);
            Grandparent restored = YdszJson.toObject(json, Grandparent.class);
            assertEquals("lonely", restored.name);
            assertNull(restored.child);
        }
    }

    // ==================== 基本类型 ====================

    @Nested
    @DisplayName("基本类型与包装类型")
    class PrimitiveTypeTests {

        @Test
        @DisplayName("null → \"null\"")
        void nullToJson() { assertEquals("null", YdszJson.toJson(null)); }

        @Test
        @DisplayName("Boolean 往返")
        void booleanRoundTrip() {
            assertTrue(YdszJson.toObject("true", Boolean.class));
            assertFalse(YdszJson.toObject("false", Boolean.class));
        }

        @Test
        @DisplayName("Integer 往返")
        void integerRoundTrip() {
            assertEquals(42, (int) YdszJson.toObject("42", Integer.class));
            assertEquals(-1, (int) YdszJson.toObject("-1", Integer.class));
        }

        @Test
        @DisplayName("Long 往返")
        void longRoundTrip() {
            long big = 9_223_372_036_854_775_807L;
            String json = YdszJson.toJson(big);
            assertEquals(big, (long) YdszJson.toObject(json, Long.class));
        }

        @Test
        @DisplayName("Double 往返")
        void doubleRoundTrip() {
            assertEquals(3.14, YdszJson.toObject("3.14", Double.class), 0.0001);
            assertEquals(-2.5, YdszJson.toObject("-2.5", Double.class), 0.0001);
        }

        @Test
        @DisplayName("String 反序列化去掉引号")
        void stringDeserialization() {
            assertEquals("hello", YdszJson.toObject("\"hello\"", String.class));
        }
    }

    // ==================== 内部测试用 Bean ====================

    static class Grandparent {
        public String name;
        public Parent child;
    }

    static class Parent {
        public String name;
        public Child child;
    }

    static class Child {
        public String name;
        public int age;
    }
}
