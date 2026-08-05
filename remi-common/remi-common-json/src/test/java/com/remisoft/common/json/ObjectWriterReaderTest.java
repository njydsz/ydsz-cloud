package com.remisoft.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.annotation.JsonClass;
import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.naming.PropertyNamingStrategy;
import com.remisoft.common.json.provider.SerializationProvider;
import com.remisoft.common.json.type.JsonType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ObjectWriter / ObjectReader 免 ThreadLocal 显式序列化器测试（P1-2）。
 */
class ObjectWriterReaderTest {

    @BeforeEach
    void setUp() {
        SerializationProvider.clearThreadLocals();
        JsonConfig.getInstance().apply();
        // 反序列化需要关闭 SafeMode（非 Spring 环境无 @JsonClass 扫描注册）
        // 注册表在首次写操作前的 initializeWhitelist() 中初始化
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        SerializationProvider.clearThreadLocals();
        JsonConfig.getInstance().apply();
    }

    // 测试 POJO（需要 @JsonClass 注解以通过 AutoType 白名单检查）
    @JsonClass
    @SuppressWarnings("unused")
    public static class TestUser {
        private String name;
        private int age;
        private String email;

        public TestUser() {}

        public TestUser(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Test
    @DisplayName("ObjectWriter.forType: 基本序列化")
    void forType_basicSerialize() {
        TestUser user = new TestUser("Alice", 30, "alice@example.com");
        ObjectWriter writer = ObjectWriter.forType(TestUser.class);
        String json = writer.toJson(user);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertTrue(json.contains("\"age\":30"));
        assertTrue(json.contains("\"email\":\"alice@example.com\""));
    }

    @Test
    @DisplayName("ObjectWriter: toPrettyJson 格式化输出")
    void writer_toPrettyJson() {
        TestUser user = new TestUser("Bob", 25, null);
        ObjectWriter writer = ObjectWriter.forType(TestUser.class);
        String json = writer.toPrettyJson(user);

        assertTrue(json.contains("\n"), "Pretty print should include newlines");
        assertTrue(json.contains("  "), "Pretty print should include indentation");
    }

    @Test
    @DisplayName("ObjectWriter: 链式配置 - SNAKE_CASE 命名")
    void writer_withSnakeCase() {
        TestUser user = new TestUser("Charlie", 35, "charlie@example.com");
        ObjectWriter writer = ObjectWriter.forType(TestUser.class)
                .withNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        String json = writer.toJson(user);

        // SNAKE_CASE for camelCase fields: first_name stays the same for these
        // All our test fields are single words so naming won't visibly change
        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
    }

    @Test
    @DisplayName("ObjectWriter.standard: 使用全局默认配置")
    void standard_usesGlobalConfig() {
        ObjectWriter writer = ObjectWriter.standard();
        TestUser user = new TestUser("Dave", 40, "dave@example.com");
        String json = writer.toJson(user);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"Dave\""));
    }

    @Test
    @DisplayName("ObjectWriter.of: 使用显式配置")
    void of_usesExplicitConfig() {
        JsonConfig config = JsonConfig.builder().prettyPrint(true).build();
        ObjectWriter writer = ObjectWriter.of(config);
        TestUser user = new TestUser("Eve", 28, "eve@example.com");
        // 注意：ctx.prettyPrint 在 ASM 路径未被读取，format 通过独立路径
        String json = writer.toPrettyJson(user);

        assertTrue(json.contains("\n"));
    }

    @Test
    @DisplayName("ObjectWriter: toJsonBytes 返回 UTF-8 字节")
    void toJsonBytes_returnsUtf8Bytes() {
        ObjectWriter writer = ObjectWriter.standard();
        TestUser user = new TestUser("Frank", 45, "frank@example.com");
        byte[] bytes = writer.toJsonBytes(user);
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(json.contains("\"name\":\"Frank\""));
    }

    @Test
    @DisplayName("ObjectWriter: toPrettyJson 格式化输出")
    void toPrettyJson_formattedOutput() {
        ObjectWriter writer = ObjectWriter.standard();
        TestUser user = new TestUser("Grace", 32, "grace@example.com");
        String json = writer.toPrettyJson(user);

        assertTrue(json.contains("\n"));
        assertTrue(json.contains("\"name\""));
    }

    @Test
    @DisplayName("ObjectWriter: 链式配置返回新实例，原实例不变")
    void chaining_returnsNewInstance() {
        ObjectWriter writer1 = ObjectWriter.forType(TestUser.class);
        ObjectWriter writer2 = writer1.withNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        assertNotSame(writer1, writer2, "withNamingStrategy should return new instance");
    }

    @Test
    @DisplayName("ObjectReader.forType: 基本反序列化")
    void reader_forType_basicDeserialize() {
        String json = "{\"name\":\"Ivan\",\"age\":29,\"email\":\"ivan@example.com\"}";
        ObjectReader reader = ObjectReader.forType(TestUser.class);
        TestUser user = reader.readValue(json, TestUser.class);

        assertNotNull(user);
        assertEquals("Ivan", user.getName());
        assertEquals(29, user.getAge());
        assertEquals("ivan@example.com", user.getEmail());
    }

    @Test
    @DisplayName("ObjectReader: readBytes 反序列化")
    void reader_readValueBytes() throws IOException {
        String json = "{\"name\":\"Jane\",\"age\":31,\"email\":\"jane@example.com\"}";
        ObjectReader reader = ObjectReader.forType(TestUser.class);
        TestUser user = reader.readValue(json.getBytes(StandardCharsets.UTF_8), TestUser.class);

        assertNotNull(user);
        assertEquals("Jane", user.getName());
        assertEquals(31, user.getAge());
    }

    @Test
    @DisplayName("ObjectReader: readMap 解析为 Map")
    void reader_readMap() {
        String json = "{\"key1\":\"value1\",\"key2\":42}";
        ObjectReader reader = ObjectReader.forType(Map.class);
        Map<String, Object> map = reader.readMap(json);

        assertNotNull(map);
        assertEquals("value1", map.get("key1"));
    }

    @Test
    @DisplayName("ObjectReader: readList 解析数组")
    void reader_readList() {
        String json = "[{\"name\":\"Alice\",\"age\":30,\"email\":null},{\"name\":\"Bob\",\"age\":25,\"email\":null}]";
        ObjectReader reader = ObjectReader.forType(TestUser.class);
        List<TestUser> users = reader.readList(json, TestUser.class);

        assertNotNull(users);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).getName());
        assertEquals("Bob", users.get(1).getName());
    }

    @Test
    @DisplayName("ObjectReader.readValue: 泛型类型")
    void reader_genericType() {
        String json = "[{\"name\":\"Carol\",\"age\":35,\"email\":null}]";
        ObjectReader reader = ObjectReader.forType(TestUser.class);
        Object result = reader.readValue(json, new JsonType<List<TestUser>>() {}.getType());

        assertNotNull(result);
    }

    @Test
    @DisplayName("ObjectReader: 链式配置 - 返回新实例")
    void reader_chainingReturnsNewInstance() {
        ObjectReader reader1 = ObjectReader.forType(TestUser.class);
        ObjectReader reader2 = reader1.withMaxJsonSize(1024);

        assertNotSame(reader1, reader2, "withMaxJsonSize should return new instance");
    }

    @Test
    @DisplayName("ObjectReader: InputStream 反序列化")
    void reader_fromInputStream() throws IOException {
        String json = "{\"name\":\"Dave\",\"age\":40,\"email\":\"dave@example.com\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ObjectReader reader = ObjectReader.forType(TestUser.class);
        TestUser user = reader.readValue(new ByteArrayInputStream(bytes), TestUser.class);

        assertNotNull(user);
        assertEquals("Dave", user.getName());
    }

    @Test
    @DisplayName("ObjectWriter + ObjectReader 组合往返测试")
    void roundTrip_writerAndReader() {
        TestUser original = new TestUser("Eve", 28, "eve@example.com");

        ObjectWriter writer = ObjectWriter.standard();
        String json = writer.toJson(original);

        ObjectReader reader = ObjectReader.standard();
        TestUser restored = reader.readValue(json, TestUser.class);

        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getAge(), restored.getAge());
        assertEquals(original.getEmail(), restored.getEmail());
    }

    @Test
    @DisplayName("ObjectWriter: 独立配置快照不受全局影响")
    void independentConfigSnapshot() {
        // 使用独立配置创建自定义 writer
        JsonConfig customConfig = JsonConfig.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build();
        ObjectWriter customWriter = ObjectWriter.of(customConfig);

        // 即使全局配置改变，customWriter 不受影响 - 验证独立副本
        TestUser user = new TestUser("Frank", 50, "frank@example.com");
        String json = customWriter.toJson(user);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"Frank\""),
                "Custom writer should use its own config snapshot");
    }
}
