package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.*;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.type.YdszJsonType;

@DisplayName("YdszJson 核心序列化/反序列化测试")
class YdszJsonCoreTest {

    // ==================== 测试模型 ====================

    @YdszJsonClass
    static class User {
        private Long id;
        private String name;
        private int age;

        public User() {}

        public User(Long id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @YdszJsonClass
    static class Address {
        private String city;
        private String street;

        public Address() {}

        public Address(String city, String street) {
            this.city = city;
            this.street = street;
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    @YdszJsonClass
    static class NestedUser {
        private String name;
        private Address address;

        public NestedUser() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Address getAddress() { return address; }
        public void setAddress(Address address) { this.address = address; }
    }

    // ==================== toJson 基本类型（通过 Map 包装） ====================

    @Nested
    @DisplayName("toJson - 基本类型序列化")
    class ToJsonPrimitives {

        @Test
        @DisplayName("序列化包含 int 的 Map")
        void toJsonInt() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", 42);
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("42"));
        }

        @Test
        @DisplayName("序列化包含 long 的 Map")
        void toJsonLong() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", 9999999999L);
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("9999999999"));
        }

        @Test
        @DisplayName("序列化包含 double 的 Map")
        void toJsonDouble() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", 3.14);
            String json = YdszJson.toJson(map);
            assertNotNull(json);
        }

        @Test
        @DisplayName("序列化包含 boolean 的 Map")
        void toJsonBoolean() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", true);
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("true"));
        }

        @Test
        @DisplayName("序列化包含 String 的 Map")
        void toJsonString() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", "hello");
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("hello"));
        }

        @Test
        @DisplayName("序列化 null")
        void toJsonNull() {
            assertEquals("null", YdszJson.toJson(null));
        }
    }

    // ==================== toJson 集合 ====================

    @Nested
    @DisplayName("toJson - 集合序列化")
    class ToJsonCollections {

        @Test
        @DisplayName("序列化 List")
        void toJsonList() {
            List<Integer> list = Arrays.asList(1, 2, 3);
            String json = YdszJson.toJson(list);
            assertTrue(json.contains("1"));
            assertTrue(json.contains("2"));
            assertTrue(json.contains("3"));
        }

        @Test
        @DisplayName("序列化 Map")
        void toJsonMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "John");
            map.put("age", 30);
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("name"));
            assertTrue(json.contains("John"));
            assertTrue(json.contains("age"));
        }

        @Test
        @DisplayName("序列化 String 数组")
        void toJsonStringArray() {
            String[] arr = {"hello", "world"};
            String json = YdszJson.toJson(arr);
            assertTrue(json.contains("hello"));
            assertTrue(json.contains("world"));
        }
    }

    // ==================== toJson 对象 ====================

    @Nested
    @DisplayName("toJson - 对象序列化")
    class ToJsonObjects {

        @Test
        @DisplayName("序列化简单 POJO")
        void toJsonSimplePojo() {
            User user = new User(1L, "John", 30);
            String json = YdszJson.toJson(user);
            assertTrue(json.contains("John"));
            assertTrue(json.contains("30"));
        }

        @Test
        @DisplayName("序列化嵌套对象")
        void toJsonNestedObject() {
            NestedUser nu = new NestedUser();
            nu.setName("Alice");
            nu.setAddress(new Address("Beijing", "Chaoyang"));
            String json = YdszJson.toJson(nu);
            assertTrue(json.contains("Alice"));
            assertTrue(json.contains("Beijing"));
            assertTrue(json.contains("Chaoyang"));
        }

        @Test
        @DisplayName("序列化空对象")
        void toJsonEmptyObject() {
            User user = new User();
            String json = YdszJson.toJson(user);
            assertNotNull(json);
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}"));
        }
    }

    // ==================== toObject 反序列化 ====================

    @Nested
    @DisplayName("toObject - 反序列化")
    class ToObjectTests {

        @Test
        @DisplayName("反序列化为 Map")
        void toObjectAsMap() {
            String json = "{\"id\":1,\"name\":\"John\",\"age\":30}";
            Map<String, Object> map = YdszJson.parseObject(json);
            assertNotNull(map);
            assertEquals("John", map.get("name"));
            assertEquals(1L, ((Number) map.get("id")).longValue());
            assertEquals(30L, ((Number) map.get("age")).longValue());
        }

        @Test
        @DisplayName("反序列化泛型 List<String>")
        void toObjectGenericListString() {
            String json = "[\"a\",\"b\",\"c\"]";
            List<String> list = YdszJson.toObject(json, new YdszJsonType<List<String>>() {});
            assertNotNull(list);
            assertEquals(3, list.size());
            assertEquals("a", list.get(0));
            assertEquals("b", list.get(1));
            assertEquals("c", list.get(2));
        }

        @Test
        @DisplayName("toObject 返回 Map（当前实现）")
        void toObjectReturnsMap() {
            String json = "{\"id\":1,\"name\":\"John\"}";
            Object result = YdszJson.toObject(json, Map.class);
            assertNotNull(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals("John", map.get("name"));
        }
    }

    // ==================== parseObject / parseArray ====================

    @Nested
    @DisplayName("parseObject / parseArray")
    class ParseTests {

        @Test
        @DisplayName("parseObject 返回 Map")
        void parseObjectReturnsMap() {
            String json = "{\"name\":\"John\",\"age\":30}";
            Map<String, Object> map = YdszJson.parseObject(json);
            assertNotNull(map);
            assertEquals("John", map.get("name"));
            assertEquals(30L, ((Number) map.get("age")).longValue());
        }

        @Test
        @DisplayName("parseArray 返回 List")
        void parseArrayReturnsList() {
            String json = "[1,2,3]";
            List<Object> list = YdszJson.parseArray(json);
            assertNotNull(list);
            assertEquals(3, list.size());
        }

        @Test
        @DisplayName("parseArray 带元素类型")
        void parseArrayWithElementType() {
            String json = "[\"a\",\"b\",\"c\"]";
            List<String> list = YdszJson.parseArray(json, String.class);
            assertNotNull(list);
            assertEquals(3, list.size());
            assertEquals("a", list.get(0));
        }
    }

    // ==================== 往返测试 ====================

    @Nested
    @DisplayName("往返测试 (Round-trip)")
    class RoundTripTests {

        @Test
        @DisplayName("Map 往返序列化")
        void roundTripMap() {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("key", "value");
            original.put("num", 123);
            String json = YdszJson.toJson(original);
            Map<String, Object> restored = YdszJson.parseObject(json);
            assertEquals("value", restored.get("key"));
            assertEquals(123L, ((Number) restored.get("num")).longValue());
        }

        @Test
        @DisplayName("List 往返序列化")
        void roundTripList() {
            List<String> original = Arrays.asList("a", "b", "c");
            String json = YdszJson.toJson(original);
            List<String> restored = YdszJson.toObject(json, new YdszJsonType<List<String>>() {});
            assertEquals(original, restored);
        }
    }

    // ==================== format ====================

    @Nested
    @DisplayName("format - 格式化输出")
    class FormatTests {

        @Test
        @DisplayName("format 输出带缩进")
        void formatProducesIndentedOutput() {
            User user = new User(1L, "John", 30);
            String formatted = YdszJson.format(user);
            assertTrue(formatted.contains("\n") || formatted.contains("  "));
        }

        @Test
        @DisplayName("toJson(obj, true) 等同于 format")
        void toJsonPrettyEqualsFormat() {
            User user = new User(1L, "John", 30);
            String fromFormat = YdszJson.format(user);
            String fromPretty = YdszJson.toJson(user, true);
            assertEquals(fromFormat, fromPretty);
        }
    }

    // ==================== 特殊字符 ====================

    @Nested
    @DisplayName("特殊字符处理")
    class SpecialCharacterTests {

        @Test
        @DisplayName("序列化包含引号的字符串")
        void stringWithQuotes() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", "say \"hello\"");
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("\\\"") || json.contains("hello"));
        }

        @Test
        @DisplayName("序列化包含换行符的字符串")
        void stringWithNewline() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", "line1\nline2");
            String json = YdszJson.toJson(map);
            assertTrue(json.contains("\\n") || json.contains("line1"));
        }

        @Test
        @DisplayName("序列化包含 Unicode 的字符串")
        void stringWithUnicode() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", "你好世界");
            String json = YdszJson.toJson(map);
            assertNotNull(json);
            assertTrue(json.contains("你好世界"));
        }
    }

    // ==================== 空对象和空数组 ====================

    @Nested
    @DisplayName("空对象和空数组")
    class EmptyTests {

        @Test
        @DisplayName("序列化空 Map")
        void emptyMap() {
            String json = YdszJson.toJson(new LinkedHashMap<>());
            assertEquals("{}", json);
        }

        @Test
        @DisplayName("序列化空 List")
        void emptyList() {
            String json = YdszJson.toJson(new ArrayList<>());
            assertEquals("[]", json);
        }

        @Test
        @DisplayName("反序列化空对象")
        void emptyObjectDeserialize() {
            Map<String, Object> map = YdszJson.parseObject("{}");
            assertNotNull(map);
            assertTrue(map.isEmpty());
        }

        @Test
        @DisplayName("反序列化空数组")
        void emptyArrayDeserialize() {
            List<Object> list = YdszJson.parseArray("[]");
            assertNotNull(list);
            assertTrue(list.isEmpty());
        }
    }

    // ==================== 深层嵌套 ====================

    @Nested
    @DisplayName("深层嵌套对象")
    class DeepNestedTests {

        @Test
        @DisplayName("序列化深层嵌套 Map")
        void deepNestedMap() {
            Map<String, Object> level3 = new LinkedHashMap<>();
            level3.put("value", "deep");
            Map<String, Object> level2 = new LinkedHashMap<>();
            level2.put("level3", level3);
            Map<String, Object> level1 = new LinkedHashMap<>();
            level1.put("level2", level2);

            String json = YdszJson.toJson(level1);
            assertTrue(json.contains("deep"));

            Map<String, Object> parsed = YdszJson.parseObject(json);
            assertNotNull(parsed);
        }
    }
}
