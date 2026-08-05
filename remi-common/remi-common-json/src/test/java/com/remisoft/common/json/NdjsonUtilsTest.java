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

import com.remisoft.common.json.autotype.AutoTypeChecker;
import com.remisoft.common.json.ndjson.NdjsonUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NDJSON（Newline Delimited JSON）工具类测试（使用已验证的 TestBean）。
 */
class NdjsonUtilsTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        com.remisoft.common.json.provider.SerializationProvider.SerializationContext.clear();
    }

    // ==================== parse 测试 ====================

    @Test
    @DisplayName("parse: 解析基础 NDJSON 字符串")
    void parse_basicNdjson() {
        String jsonl = "{\"id\":1,\"name\":\"Alice\"}\n"
                + "{\"id\":2,\"name\":\"Bob\"}";

        List<TestBean> beans = NdjsonUtils.parse(jsonl, TestBean.class);
        assertEquals(2, beans.size());
        assertEquals("Alice", beans.get(0).getName());
        assertEquals(1, beans.get(0).getId());
        assertEquals("Bob", beans.get(1).getName());
    }

    @Test
    @DisplayName("parse: 跳过空行")
    void parse_skipsBlankLines() {
        String jsonl = "{\"id\":1,\"name\":\"Alice\"}\n\n"
                + "{\"id\":2,\"name\":\"Bob\"}";

        List<TestBean> beans = NdjsonUtils.parse(jsonl, TestBean.class);
        assertEquals(2, beans.size());
    }

    @Test
    @DisplayName("parse: 空字符串返回空 List")
    void parse_emptyString() {
        assertTrue(NdjsonUtils.parse("", TestBean.class).isEmpty());
        assertTrue(NdjsonUtils.parse((String) null, TestBean.class).isEmpty());
    }

    @Test
    @DisplayName("parse: CRLF 兼容")
    void parse_crlfCompatible() {
        String jsonl = "{\"id\":1,\"name\":\"A\"}\r\n"
                + "{\"id\":2,\"name\":\"B\"}";
        List<TestBean> beans = NdjsonUtils.parse(jsonl, TestBean.class);
        assertEquals(2, beans.size());
    }

    // ==================== write 测试 ====================

    @Test
    @DisplayName("write: 序列化 List 为 NDJSON 字节流")
    void write_toOutputStream() throws IOException {
        List<TestBean> beans = Arrays.asList(
                createBean(1, "Alice"),
                createBean(2, "Bob"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NdjsonUtils.write(beans, baos);

        String result = baos.toString(StandardCharsets.UTF_8);
        String[] lines = result.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("\"Alice\""));
        assertTrue(lines[1].contains("\"Bob\""));
    }

    @Test
    @DisplayName("writeStream: 序列化对象流为 NDJSON")
    void writeStream_streamToOutputStream() throws IOException {
        Stream<TestBean> stream = Stream.of(
                createBean(1, "X"),
                createBean(2, "Y"),
                createBean(3, "Z"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NdjsonUtils.writeStream(stream, baos);

        String result = baos.toString(StandardCharsets.UTF_8);
        assertEquals(3, result.split("\n").length);
    }

    // ==================== Stream 解析测试 ====================

    @Test
    @DisplayName("parseStream: 从 InputStream 流式解析 NDJSON")
    void parseStream_fromInputStream() {
        String jsonl = "{\"id\":1,\"name\":\"Alice\"}\n"
                + "{\"id\":2,\"name\":\"Bob\"}\n"
                + "{\"id\":3,\"name\":\"Charlie\"}\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8));

        try (Stream<TestBean> stream = NdjsonUtils.parseStream(bais, TestBean.class)) {
            List<TestBean> beans = stream.collect(Collectors.toList());
            assertEquals(3, beans.size());
            assertEquals("Charlie", beans.get(2).getName());
        }
    }

    // ==================== RemiJson 入口方法测试 ====================

    @Test
    @DisplayName("RemiJson.readNdjson: 入口方法委托正常")
    void remiJson_readNdjson() {
        String jsonl = "{\"id\":10,\"name\":\"Tom\"}\n";
        List<TestBean> beans = RemiJson.readNdjson(jsonl, TestBean.class);
        assertEquals(1, beans.size());
        assertEquals("Tom", beans.get(0).getName());
    }

    @Test
    @DisplayName("RemiJson.writeNdjson: 入口方法委托正常")
    void remiJson_writeNdjson() throws IOException {
        List<TestBean> beans = Arrays.asList(createBean(1, "Joe"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RemiJson.writeNdjson(beans, baos);
        String result = baos.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("\"name\""));
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

    private static TestBean createBean(int id, String name) {
        TestBean bean = new TestBean();
        bean.setId(id);
        bean.setName(name);
        return bean;
    }
}
