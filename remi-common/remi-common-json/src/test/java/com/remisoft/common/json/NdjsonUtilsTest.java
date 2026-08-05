package com.remisoft.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.remisoft.common.json.ndjson.NdjsonUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NDJSON（Newline Delimited JSON）工具类测试。
 */
class NdjsonUtilsTest {

    /** 测试用简单 POJO */
    record TestUser(String name, int age, String email) {}

    // ==================== parse 测试 ====================

    @Test
    @DisplayName("parse: 解析基础 NDJSON 字符串")
    void parse_basicNdjson() {
        String jsonl = """
                {"name":"Alice","age":30,"email":"alice@example.com"}
                {"name":"Bob","age":25,"email":"bob@example.com"}
                """;

        List<TestUser> users = NdjsonUtils.parse(jsonl, TestUser.class);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).name());
        assertEquals(30, users.get(0).age());
        assertEquals("Bob", users.get(1).name());
    }

    @Test
    @DisplayName("parse: 跳过空行")
    void parse_skipsBlankLines() {
        String jsonl = """
                {"name":"Alice","age":30,"email":"a@e.com"}

                {"name":"Bob","age":25,"email":"b@e.com"}
                """;

        List<TestUser> users = NdjsonUtils.parse(jsonl, TestUser.class);
        assertEquals(2, users.size());
    }

    @Test
    @DisplayName("parse: 空字符串返回空 List")
    void parse_emptyString() {
        assertTrue(NdjsonUtils.parse("", TestUser.class).isEmpty());
        assertTrue(NdjsonUtils.parse((String) null, TestUser.class).isEmpty());
    }

    @Test
    @DisplayName("parse: CRLF 兼容")
    void parse_crlfCompatible() {
        String jsonl = "{\"name\":\"A\",\"age\":1,\"email\":\"a@e.com\"}\r\n{\"name\":\"B\",\"age\":2,\"email\":\"b@e.com\"}";
        List<TestUser> users = NdjsonUtils.parse(jsonl, TestUser.class);
        assertEquals(2, users.size());
    }

    // ==================== write 测试 ====================

    @Test
    @DisplayName("write: 序列化 List 为 NDJSON 字节流")
    void write_toOutputStream() throws IOException {
        List<TestUser> users = Arrays.asList(
                new TestUser("Alice", 30, "alice@example.com"),
                new TestUser("Bob", 25, "bob@example.com"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NdjsonUtils.write(users, baos);

        String result = baos.toString(StandardCharsets.UTF_8);
        String[] lines = result.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("\"Alice\""));
        assertTrue(lines[1].contains("\"Bob\""));
    }

    @Test
    @DisplayName("writeStream: 序列化对象流为 NDJSON")
    void writeStream_streamToOutputStream() throws IOException {
        Stream<TestUser> userStream = Stream.of(
                new TestUser("X", 1, "x@e.com"),
                new TestUser("Y", 2, "y@e.com"),
                new TestUser("Z", 3, "z@e.com"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NdjsonUtils.writeStream(userStream, baos);

        String result = baos.toString(StandardCharsets.UTF_8);
        assertEquals(3, result.split("\n").length);
    }

    // ==================== Stream 解析测试 ====================

    @Test
    @DisplayName("parseStream: 从 InputStream 流式解析 NDJSON")
    void parseStream_fromInputStream() {
        String jsonl = """
                {"name":"Alice","age":30,"email":"a@e.com"}
                {"name":"Bob","age":25,"email":"b@e.com"}
                {"name":"Charlie","age":35,"email":"c@e.com"}
                """;
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8));

        try (Stream<TestUser> stream = NdjsonUtils.parseStream(bais, TestUser.class)) {
            List<TestUser> users = stream.collect(Collectors.toList());
            assertEquals(3, users.size());
            assertEquals("Charlie", users.get(2).name());
        }
    }

    // ==================== RemiJson 入口方法测试 ====================

    @Test
    @DisplayName("RemiJson.readNdjson: 入口方法委托正常")
    void remiJson_readNdjson() {
        String jsonl = """
                {"name":"Tom","age":40,"email":"tom@e.com"}
                """;
        List<TestUser> users = RemiJson.readNdjson(jsonl, TestUser.class);
        assertEquals(1, users.size());
        assertEquals("Tom", users.get(0).name());
    }

    @Test
    @DisplayName("RemiJson.writeNdjson: 入口方法委托正常")
    void remiJson_writeNdjson() throws IOException {
        List<TestUser> users = Arrays.asList(new TestUser("Joe", 22, "joe@e.com"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RemiJson.writeNdjson(users, baos);
        String result = baos.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith("{\"name\":\"Joe\""));
        assertTrue(result.endsWith("\n"));
    }

    @Test
    @DisplayName("RemiJson.isValidNdjson: 校验方法")
    void remiJson_isValidNdjson() {
        assertTrue(RemiJson.isValidNdjson("{\"a\":1}\n{\"b\":2}"));
        assertTrue(RemiJson.isValidNdjson(""));
    }

    @Test
    @DisplayName("RemiJson.isValidJson: 校验方法")
    void remiJson_isValidJson() {
        assertTrue(RemiJson.isValidJson("{\"a\":1}"));
        assertTrue(RemiJson.isValidJson("[1,2,3]"));
        assertTrue(RemiJson.isValidJson("\"string\""));
        assertTrue(RemiJson.isValidJson("42"));
        assertTrue(RemiJson.isValidJson("true"));
        assertTrue(RemiJson.isValidJson("false"));
        assertTrue(RemiJson.isValidJson("null"));
        assertFalse(RemiJson.isValidJson(""));
        assertFalse(RemiJson.isValidJson(null));
    }
}
