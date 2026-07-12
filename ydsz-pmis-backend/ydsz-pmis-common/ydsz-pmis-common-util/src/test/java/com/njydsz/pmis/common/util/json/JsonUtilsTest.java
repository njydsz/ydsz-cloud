package com.njydsz.pmis.common.util.json;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 单元测试
 *
 * @author Marvin Lee
 * @version 4.0.0
 */
@DisplayName("JsonUtils - 统一 JSON 工具类测试")
class JsonUtilsTest {

    @BeforeEach
    void resetMetrics() {
        JsonUtils.setMetrics(new JsonMetrics());
    }

    // ==================== 简单对象序列化/反序列化 ====================

    @Test
    @DisplayName("对象与 JSON 字符串双向转换")
    void shouldSerializeAndDeserializeSimpleObject() {
        User user = new User("u001", "Alice", 30);
        String json = JsonUtils.toJson(user);
        assertNotNull(json);
        assertTrue(json.contains("\"id\":\"u001\""));
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));

        User parsed = JsonUtils.fromJson(json, User.class);
        assertEquals(user, parsed);
    }

    @Test
    @DisplayName("美化输出 JSON 字符串")
    void shouldOutputPrettyJson() {
        User user = new User("u002", "Bob", 25);
        String pretty = JsonUtils.toPrettyJson(user);
        assertNotNull(pretty);
        assertTrue(pretty.contains(System.lineSeparator()));
        assertTrue(pretty.contains("\"id\""));
    }

    @Test
    @DisplayName("null 对象序列化返回 null")
    void shouldReturnNullWhenSerializeNull() {
        assertNull(JsonUtils.toJson(null));
        assertNull(JsonUtils.toPrettyJson(null));
    }

    @Test
    @DisplayName("空/ null JSON 字符串反序列化返回 null")
    void shouldReturnNullWhenDeserializeEmpty() {
        assertNull(JsonUtils.fromJson(null, User.class));
        assertNull(JsonUtils.fromJson("", User.class));
        assertNull(JsonUtils.fromJson("  ", User.class));
    }

    // ==================== 集合类型 ====================

    @Test
    @DisplayName("List 类型反序列化")
    void shouldDeserializeList() {
        String json = "[{\"id\":\"u1\",\"name\":\"A\",\"age\":1},{\"id\":\"u2\",\"name\":\"B\",\"age\":2}]";
        List<User> users = JsonUtils.fromJsonToList(json, User.class);
        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("u1", users.get(0).getId());
    }

    @Test
    @DisplayName("Map 类型反序列化")
    void shouldDeserializeMap() {
        String json = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        Map<String, String> map = JsonUtils.fromJsonToMap(json, String.class, String.class);
        assertNotNull(map);
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    @Test
    @DisplayName("TypeReference 泛型反序列化")
    void shouldDeserializeWithTypeReference() {
        String json = "{\"code\":200,\"data\":{\"id\":\"u3\",\"name\":\"C\",\"age\":3}}";
        Result<User> result = JsonUtils.fromJson(json, new TypeReference<Result<User>>() {});
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("u3", result.getData().getId());
    }

    // ==================== 字节数组 ====================

    @Test
    @DisplayName("对象与 JSON 字节数组双向转换")
    void shouldSerializeAndDeserializeBytes() {
        User user = new User("u004", "Dave", 40);
        byte[] bytes = JsonUtils.toJsonBytes(user);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        User parsed = JsonUtils.fromJsonBytes(bytes, User.class);
        assertEquals(user, parsed);
    }

    @Test
    @DisplayName("null 对象转字节数组返回空数组")
    void shouldReturnEmptyBytesWhenSerializeNull() {
        byte[] bytes = JsonUtils.toJsonBytes(null);
        assertNotNull(bytes);
        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("空字节数组反序列化返回 null")
    void shouldReturnNullWhenDeserializeEmptyBytes() {
        assertNull(JsonUtils.fromJsonBytes(new byte[0], User.class));
        assertNull(JsonUtils.fromJsonBytes(null, User.class));
    }

    // ==================== Java 8 时间 ====================

    @Test
    @DisplayName("Java 8 日期时间序列化格式")
    void shouldSerializeJavaTimeInConfiguredPattern() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 6, 17, 14, 30, 0);
        LocalDate date = LocalDate.of(2026, 6, 17);
        LocalTime time = LocalTime.of(14, 30, 0);

        TimeEntity entity = new TimeEntity(dateTime, date, time);
        String json = JsonUtils.toJson(entity);

        assertTrue(json.contains("\"dateTime\":\"2026-06-17 14:30:00\""));
        assertTrue(json.contains("\"date\":\"2026-06-17\""));
        assertTrue(json.contains("\"time\":\"14:30:00\""));
    }

    @Test
    @DisplayName("Java 8 日期时间反序列化")
    void shouldDeserializeJavaTimeInConfiguredPattern() {
        String json = "{\"dateTime\":\"2026-06-17 14:30:00\",\"date\":\"2026-06-17\",\"time\":\"14:30:00\"}";
        TimeEntity entity = JsonUtils.fromJson(json, TimeEntity.class);
        assertEquals(LocalDateTime.of(2026, 6, 17, 14, 30, 0), entity.getDateTime());
        assertEquals(LocalDate.of(2026, 6, 17), entity.getDate());
        assertEquals(LocalTime.of(14, 30, 0), entity.getTime());
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("非法 JSON 反序列化抛出 JsonException")
    void shouldThrowJsonExceptionWhenDeserializeInvalidJson() {
        assertThrows(JsonUtils.JsonException.class, () -> JsonUtils.fromJson("not json", User.class));
    }

    @Test
    @DisplayName("类型不匹配反序列化抛出 JsonException")
    void shouldThrowJsonExceptionWhenTypeMismatch() {
        String json = "{\"id\":\"u5\",\"name\":\"E\",\"age\":\"not-a-number\"}";
        assertThrows(JsonUtils.JsonException.class, () -> JsonUtils.fromJson(json, User.class));
    }

    @Test
    @DisplayName("未知字段应被忽略")
    void shouldIgnoreUnknownFields() {
        String json = "{\"id\":\"u6\",\"name\":\"F\",\"age\":6,\"extra\":\"ignore-me\"}";
        User user = JsonUtils.fromJson(json, User.class);
        assertNotNull(user);
        assertEquals("u6", user.getId());
    }

    // ==================== YAML 转换 ====================

    @Test
    @DisplayName("JSON 与 YAML 双向转换")
    void shouldConvertJsonAndYaml() {
        String json = "{\"name\":\"Alice\",\"age\":30,\"items\":[\"a\",\"b\"]}";
        String yaml = YamlUtils.jsonToYaml(json);
        assertNotNull(yaml);
        assertTrue(yaml.contains("name:"));
        assertTrue(yaml.contains("Alice"));
        assertTrue(yaml.contains("age: 30"));

        String convertedJson = YamlUtils.yamlToJson(yaml);
        assertNotNull(convertedJson);
        assertTrue(convertedJson.contains("\"name\""));
        assertTrue(convertedJson.contains("\"Alice\""));
    }

    @Test
    @DisplayName("空/ null 输入的 YAML 转换返回 null")
    void shouldReturnNullForEmptyYamlInput() {
        assertNull(YamlUtils.jsonToYaml(null));
        assertNull(YamlUtils.jsonToYaml(""));
        assertNull(YamlUtils.jsonToYaml("   "));
        assertNull(YamlUtils.yamlToJson(null));
        assertNull(YamlUtils.yamlToJson(""));
    }

    @Test
    @DisplayName("非法 JSON 转 YAML 抛出 JsonException")
    void shouldThrowExceptionForInvalidJsonToYaml() {
        assertThrows(JsonUtils.JsonException.class, () -> YamlUtils.jsonToYaml("not json"));
    }

    // ==================== 指标采集 ====================

    @Test
    @DisplayName("序列化成功时记录指标")
    void shouldRecordSerializeMetrics() {
        User user = new User("u007", "Gina", 27);
        JsonUtils.toJson(user);

        JsonMetrics metrics = JsonUtils.getMetrics();
        assertNotNull(metrics);
        assertEquals(1, metrics.getSerializeSuccessCount());
        assertEquals(0, metrics.getSerializeFailCount());
        assertTrue(metrics.getTotalSerializeTimeNanos() >= 0);
        assertTrue(metrics.getAverageSerializeTimeMillis() >= 0);
    }

    @Test
    @DisplayName("反序列化成功时记录指标")
    void shouldRecordDeserializeMetrics() {
        String json = "{\"id\":\"u008\",\"name\":\"Hank\",\"age\":28}";
        JsonUtils.fromJson(json, User.class);

        JsonMetrics metrics = JsonUtils.getMetrics();
        assertEquals(1, metrics.getDeserializeSuccessCount());
        assertEquals(0, metrics.getDeserializeFailCount());
        assertTrue(metrics.getTotalDeserializeTimeNanos() >= 0);
        assertTrue(metrics.getAverageDeserializeTimeMillis() >= 0);
    }

    @Test
    @DisplayName("反序列化失败时记录失败指标")
    void shouldRecordDeserializeFailureMetrics() {
        assertThrows(JsonUtils.JsonException.class, () -> JsonUtils.fromJson("not json", User.class));

        JsonMetrics metrics = JsonUtils.getMetrics();
        assertEquals(0, metrics.getDeserializeSuccessCount());
        assertEquals(1, metrics.getDeserializeFailCount());
    }

    // ==================== 测试模型 ====================

    public static class User {
        private String id;
        private String name;
        private int age;

        public User() {}

        public User(String id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User user)) return false;
            return age == user.age && java.util.Objects.equals(id, user.id) && java.util.Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, name, age);
        }
    }

    public static class Result<T> {
        private int code;
        private T data;

        public Result() {}

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
    }

    public static class TimeEntity {
        private LocalDateTime dateTime;
        private LocalDate date;
        private LocalTime time;

        public TimeEntity() {}

        public TimeEntity(LocalDateTime dateTime, LocalDate date, LocalTime time) {
            this.dateTime = dateTime;
            this.date = date;
            this.time = time;
        }

        public LocalDateTime getDateTime() { return dateTime; }
        public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public LocalTime getTime() { return time; }
        public void setTime(LocalTime time) { this.time = time; }
    }
}
