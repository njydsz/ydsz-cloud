package com.njydsz.common.json;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.DeserializationProvider;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.reader.JSONReader;
import com.njydsz.common.json.writer.JSONWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心引擎直接单元测试（P0-2）。
 *
 * <p>覆盖 {@link SerializationProvider} / {@link DeserializationProvider} /
 * {@link JSONReader} / {@link JSONWriter} / {@link JsonParserUtil} 五大核心组件，
 * 独立于 {@link YdszJson} / {@link JsonMapper} 高层 API，验证底层正确性。</p>
 */
class CoreEngineTest {

    @BeforeEach
    void setUp() {
        SerializationProvider.clearThreadLocals();
        JsonParserUtil.clearThreadLocals();
        JsonConfig.getInstance().apply();
    }

    @AfterEach
    void tearDown() {
        SerializationProvider.clearThreadLocals();
        JsonParserUtil.clearThreadLocals();
        JsonConfig.getInstance().apply();
    }

    // ==================== JsonParserUtil ====================

    @Test
    void parserUtilParseEmptyAndBlankReturnsNull() {
        assertNull(JsonParserUtil.parse(null));
        assertNull(JsonParserUtil.parse(""));
        assertNull(JsonParserUtil.parse("   "));
        assertNull(JsonParserUtil.parse("\t\n"));
    }

    @Test
    void parserUtilParseObjectReturnsMap() {
        Map<String, Object> m = JsonParserUtil.parseObject("{\"id\":7,\"name\":\"alice\"}");
        assertNotNull(m);
        assertEquals(7, ((Number) m.get("id")).intValue());
        assertEquals("alice", m.get("name"));
    }

    @Test
    void parserUtilParseArrayReturnsList() {
        List<Object> l = JsonParserUtil.parseArray("[1,2,3]");
        assertNotNull(l);
        assertEquals(3, l.size());
        assertEquals(1, ((Number) l.get(0)).intValue());
        assertEquals(3, ((Number) l.get(2)).intValue());
    }

    @Test
    void parserUtilParseAutoDispatchesByFirstChar() {
        Object obj = JsonParserUtil.parse("{\"k\":\"v\"}");
        assertTrue(obj instanceof Map, () -> "object JSON should return Map, got: " + obj.getClass());
        Object arr = JsonParserUtil.parse("[1,2]");
        assertTrue(arr instanceof List, () -> "array JSON should return List, got: " + arr.getClass());
    }

    @Test
    void parserUtilParseInvalidThrows() {
        assertThrows(Exception.class, () -> JsonParserUtil.parse("not-json"));
    }

    @Test
    void parserUtilParseStringField() {
        String v = JsonParserUtil.parseStringField("{\"name\":\"bob\"}", "name");
        assertEquals("bob", v);
    }

    @Test
    void parserUtilParseIntField() {
        int v = JsonParserUtil.parseIntField("{\"id\":42}", "id");
        assertEquals(42, v);
    }

    @Test
    void parserUtilParseLongField() {
        long v = JsonParserUtil.parseLongField("{\"id\":9876543210}", "id");
        assertEquals(9876543210L, v);
    }

    @Test
    void parserUtilParseBooleanField() {
        assertTrue(JsonParserUtil.parseBooleanField("{\"flag\":true}", "flag"));
        assertFalse(JsonParserUtil.parseBooleanField("{\"flag\":false}", "flag"));
    }

    @Test
    void parserUtilParseDoubleField() {
        double v = JsonParserUtil.parseDoubleField("{\"score\":3.14}", "score");
        assertEquals(3.14, v, 1e-9);
    }

    @Test
    void parserUtilParseObjectFieldReturnsMap() {
        Object v = JsonParserUtil.parseObjectField("{\"outer\":{\"inner\":1}}", "outer");
        assertTrue(v instanceof Map);
        assertEquals(1, ((Number) ((Map<?, ?>) v).get("inner")).intValue());
    }

    @Test
    void parserUtilSetUseBigDecimalAffectsNumberType() {
        try {
            JsonParserUtil.setUseBigDecimal(true);
            Map<String, Object> m = JsonParserUtil.parseObject("{\"v\":3.14}");
            Object num = m.get("v");
            assertTrue(num instanceof BigDecimal,
                () -> "useBigDecimal=true should parse as BigDecimal, got: " + num.getClass());
        } finally {
            JsonParserUtil.setUseBigDecimal(false);
        }
    }

    @Test
    void parserUtilClearThreadLocalsIsIdempotent() {
        // 多次调用不应抛异常
        JsonParserUtil.clearThreadLocals();
        JsonParserUtil.clearThreadLocals();
    }

    // ==================== JSONReader ====================

    @Test
    void jsonReaderReadCharAndPeek() {
        JSONReader r = new JSONReader("abc");
        assertEquals('a', r.peek());
        assertEquals('a', r.readChar());
        assertEquals('b', r.readChar());
        assertEquals('c', r.peekChar());
    }

    @Test
    void jsonReaderReadCharAtEndThrows() {
        JSONReader r = new JSONReader("a");
        r.readChar();
        assertThrows(IllegalStateException.class, r::readChar);
    }

    @Test
    void jsonReaderReadInt() {
        JSONReader r = new JSONReader("42");
        assertEquals(42, r.readInt());
    }

    @Test
    void jsonReaderReadLong() {
        JSONReader r = new JSONReader("9876543210");
        assertEquals(9876543210L, r.readLong());
    }

    @Test
    void jsonReaderReadDouble() {
        JSONReader r = new JSONReader("3.14");
        assertEquals(3.14, r.readDouble(), 1e-9);
    }

    @Test
    void jsonReaderReadBoolean() {
        assertTrue(new JSONReader("true").readBoolean());
        assertFalse(new JSONReader("false").readBoolean());
    }

    @Test
    void jsonReaderReadString() {
        JSONReader r = new JSONReader("\"hello\"");
        assertEquals("hello", r.readString());
    }

    @Test
    void jsonReaderReadObjectAndArrayMarkers() {
        JSONReader r = new JSONReader("{\"k\":[1]}");
        r.readObjectStart();
        assertEquals('\"', r.peek());
        r.readArrayStart();
        assertEquals('1', r.peek());
        r.readArrayEnd();
        r.readObjectEnd();
    }

    @Test
    void jsonReaderReadObjectStartThrowsOnArray() {
        JSONReader r = new JSONReader("[1]");
        assertThrows(IllegalStateException.class, r::readObjectStart);
    }

    @Test
    void jsonReaderReadArrayStartThrowsOnObject() {
        JSONReader r = new JSONReader("{}");
        assertThrows(IllegalStateException.class, r::readArrayStart);
    }

    @Test
    void jsonReaderSkipWhitespaceAndNextChar() {
        JSONReader r = new JSONReader("  {  ");
        r.skipWhitespace();
        assertEquals('{', r.nextChar());
        r.skipWhitespace();
        assertTrue(!r.isEnd());
    }

    @Test
    void jsonReaderIsNullDetectsNullLiteral() {
        JSONReader r = new JSONReader("null");
        assertTrue(r.isNull());
    }

    @Test
    void jsonReaderResetReusesBuffer() {
        JSONReader r = new JSONReader("abc");
        r.readChar();
        r.reset("xyz");
        assertEquals('x', r.peek());
    }

    @Test
    void jsonReaderGetPooledReaderResetsState() {
        JSONReader r = JSONReader.getPooledReader("{\"a\":1}");
        assertEquals('{', r.peek());
        JSONReader.returnPooledReader(r);
        // 同一线程再借一个，应是同实例（池化）
        JSONReader r2 = JSONReader.getPooledReader("[1]");
        assertEquals('[', r2.peek());
        JSONReader.returnPooledReader(r2);
    }

    @Test
    void jsonReaderSetMaxDepthMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> JSONReader.setMaxDepth(0));
        assertThrows(IllegalArgumentException.class, () -> JSONReader.setMaxDepth(-1));
    }

    @Test
    void jsonReaderSetMaxDepthRoundTrip() {
        int original = JSONReader.getMaxDepth();
        try {
            JSONReader.setMaxDepth(64);
            assertEquals(64, JSONReader.getMaxDepth());
        } finally {
            JSONReader.setMaxDepth(original);
        }
    }

    // ==================== JSONWriter ====================

    @Test
    void jsonWriterWriteCharAndString() {
        JSONWriter w = new JSONWriter(16);
        w.write('a');
        w.write("bc");
        assertEquals("abc", w.toString());
    }

    @Test
    void jsonWriterWriteInt() {
        JSONWriter w = new JSONWriter(16);
        w.writeInt(42);
        assertEquals("42", w.toString());
        JSONWriter neg = new JSONWriter(16);
        neg.writeInt(-7);
        assertEquals("-7", neg.toString());
    }

    @Test
    void jsonWriterWriteLong() {
        JSONWriter w = new JSONWriter(32);
        w.writeLong(9876543210L);
        assertEquals("9876543210", w.toString());
    }

    @Test
    void jsonWriterWriteStringDirectHandlesEscapes() {
        JSONWriter w = new JSONWriter(32);
        w.writeStringDirect("a\"b\\c");
        String out = w.toString();
        // 必须用引号包裹并转义
        assertTrue(out.startsWith("\""), () -> "expected leading quote, got: " + out);
        assertTrue(out.endsWith("\""), () -> "expected trailing quote, got: " + out);
        assertTrue(out.contains("\\\""), () -> "expected escaped quote, got: " + out);
        assertTrue(out.contains("\\\\"), () -> "expected escaped backslash, got: " + out);
    }

    @Test
    void jsonWriterWriteStringWithSbExternal() {
        StringBuilder sb = new StringBuilder();
        JSONWriter w = new JSONWriter(sb);
        w.write("hello-");
        w.writeInt(7);
        assertEquals("hello-7", sb.toString());
        assertEquals("hello-7", w.toString());
    }

    @Test
    void jsonWriterResetClearsPosition() {
        JSONWriter w = new JSONWriter(16);
        w.writeInt(123);
        assertEquals(3, w.size());
        w.reset();
        assertEquals(0, w.size());
        assertEquals("", w.toString());
    }

    @Test
    void jsonWriterResetShrinksOversizedBuffer() {
        JSONWriter w = new JSONWriter(16);
        // 触发扩容到 > MAX_RESET_CAPACITY (65536)
        w.preAllocate(100_000);
        int bigCapacity = w.capacity();
        assertTrue(bigCapacity >= 100_000, () -> "expected expanded capacity, got: " + bigCapacity);
        w.reset();
        // 缩容后回到默认 4096
        assertEquals(4096, w.capacity());
    }

    @Test
    void jsonWriterToUtf8BytesAscii() {
        JSONWriter w = new JSONWriter(16);
        w.write("hello");
        byte[] bytes = w.toUtf8Bytes();
        assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII), bytes);
    }

    @Test
    void jsonWriterToUtf8BytesNonAscii() {
        JSONWriter w = new JSONWriter(16);
        w.write("中文");
        byte[] bytes = w.toUtf8Bytes();
        assertArrayEquals("中文".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void jsonWriterWriteToOutputStream() throws Exception {
        JSONWriter w = new JSONWriter(16);
        w.write("stream-data");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        w.writeTo(out);
        assertArrayEquals("stream-data".getBytes(StandardCharsets.US_ASCII), out.toByteArray());
    }

    @Test
    void jsonWriterWriteCollection() {
        JSONWriter w = new JSONWriter(32);
        w.writeCollection(Arrays.asList(1, 2, 3));
        String out = w.toString();
        // 集合应为 [1,2,3]（具体分隔符取决于实现，但应包含方括号和元素）
        assertTrue(out.startsWith("["), () -> "expected '[' prefix, got: " + out);
        assertTrue(out.endsWith("]"), () -> "expected ']' suffix, got: " + out);
        assertTrue(out.contains("1") && out.contains("2") && out.contains("3"),
            () -> "expected all elements, got: " + out);
    }

    @Test
    void jsonWriterWriteMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", "x");
        JSONWriter w = new JSONWriter(32);
        w.writeMap(m);
        String out = w.toString();
        assertTrue(out.startsWith("{"), () -> "expected '{' prefix, got: " + out);
        assertTrue(out.endsWith("}"), () -> "expected '}' suffix, got: " + out);
        assertTrue(out.contains("\"a\""), () -> "expected key 'a', got: " + out);
        assertTrue(out.contains("\"b\""), () -> "expected key 'b', got: " + out);
    }

    @Test
    void jsonWriterNeedsEscapeDetectsSpecialChars() {
        assertTrue(JSONWriter.needsEscape("a\"b"));
        assertTrue(JSONWriter.needsEscape("a\\b"));
        assertTrue(JSONWriter.needsEscape("a\tb"));
        assertFalse(JSONWriter.needsEscape("plain"));
        assertFalse(JSONWriter.needsEscape(""));
    }

    @Test
    void jsonWriterGetPositionAndSetPosition() {
        JSONWriter w = new JSONWriter(16);
        w.writeInt(123);
        assertEquals(3, w.getPosition());
        w.setPosition(1);
        assertEquals(1, w.getPosition());
    }

    @Test
    void jsonWriterFeatureFlags() {
        long flags = JSONWriter.of(JSONWriter.Feature.WriteNulls, JSONWriter.Feature.PrettyPrint);
        assertTrue(JSONWriter.Feature.WriteNulls.isEnabled(flags));
        assertTrue(JSONWriter.Feature.PrettyPrint.isEnabled(flags));
        assertFalse(JSONWriter.Feature.UseSingleQuotes.isEnabled(flags));
    }

    // ==================== SerializationProvider ====================

    @Test
    void serializationProviderSerializeNullReturnsNullLiteral() {
        assertEquals("null", SerializationProvider.serialize(null));
    }

    @Test
    void serializationProviderSerializePrimitive() {
        assertEquals("42", SerializationProvider.serialize(42));
        assertEquals("true", SerializationProvider.serialize(true));
        assertEquals("\"hello\"", SerializationProvider.serialize("hello"));
    }

    @Test
    void serializationProviderSerializeCollection() {
        String json = SerializationProvider.serialize(Arrays.asList(1, 2, 3));
        assertTrue(json.startsWith("["), () -> "expected array, got: " + json);
        assertTrue(json.contains("1") && json.contains("2") && json.contains("3"));
    }

    @Test
    void serializationProviderSerializeMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 7);
        m.put("name", "bob");
        String json = SerializationProvider.serialize(m);
        assertTrue(json.startsWith("{"), () -> "expected object, got: " + json);
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"bob\""));
    }

    @Test
    void serializationProviderSerializeBytesMatchesString() {
        String json = SerializationProvider.serialize(Arrays.asList(1, 2, 3));
        byte[] bytes = SerializationProvider.serializeToBytes(Arrays.asList(1, 2, 3));
        assertEquals(json, new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void serializationProviderFormatProducesMultiline() {
        String json = SerializationProvider.format(Arrays.asList(1, 2));
        assertTrue(json.contains("\n"), () -> "pretty output should be multi-line, got: " + json);
    }

    @Test
    void serializationProviderSerializeWithViewPreservesFields() {
        TestBean b = new TestBean();
        b.setId(9);
        b.setName("alice");
        String json = SerializationProvider.serializeWithView(b, Object.class);
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"alice\""));
    }

    @Test
    void serializationProviderThreadLocalAccessors() {
        SerializationProvider.setWriteNulls(true);
        assertTrue(SerializationProvider.isWriteNulls());
        SerializationProvider.setWriteNulls(false);
        assertFalse(SerializationProvider.isWriteNulls());

        SerializationProvider.setPrettyPrint(true);
        assertTrue(SerializationProvider.isPrettyPrint());
        SerializationProvider.setPrettyPrint(false);

        SerializationProvider.setSerializeEnumUsingOrdinal(true);
        assertTrue(SerializationProvider.isSerializeEnumUsingOrdinal());
        SerializationProvider.setSerializeEnumUsingOrdinal(false);

        SerializationProvider.setCircularReferenceStrategy("IGNORE");
        assertEquals("IGNORE", SerializationProvider.getCircularReferenceStrategy());
        SerializationProvider.setCircularReferenceStrategy("REF");

        SerializationProvider.setDateFormat("yyyy-MM-dd");
        assertEquals("yyyy-MM-dd", SerializationProvider.getDateFormat());

        SerializationProvider.setFailOnError(true);
        assertTrue(SerializationProvider.isFailOnError());
        SerializationProvider.setFailOnError(false);
    }

    @Test
    void serializationProviderExcludedFields() {
        assertNull(SerializationProvider.getExcludedFields());
        SerializationProvider.setExcludedFields(java.util.Set.of("secret"));
        assertNotNull(SerializationProvider.getExcludedFields());
        assertTrue(SerializationProvider.isFieldExcluded("secret"));
        assertTrue(SerializationProvider.isFieldExcluded("\"secret\":"));
        assertFalse(SerializationProvider.isFieldExcluded("public"));
        SerializationProvider.setExcludedFields(null);
        assertNull(SerializationProvider.getExcludedFields());
    }

    @Test
    void serializationProviderSerializeToStream() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SerializationProvider.serializeToStream("hello", out);
        assertEquals("\"hello\"", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void serializationProviderSerializeToWriter() throws Exception {
        java.io.StringWriter sw = new java.io.StringWriter();
        SerializationProvider.serializeToWriter("hello", sw);
        assertEquals("\"hello\"", sw.toString());
    }

    @Test
    void serializationProviderClearThreadLocalsIsIdempotent() {
        SerializationProvider.clearThreadLocals();
        SerializationProvider.clearThreadLocals();
    }

    @Test
    void serializationProviderThreadLocalSnapshotRestoresState() {
        SerializationProvider.setWriteNulls(false);
        SerializationProvider.ThreadLocalSnapshot snap = new SerializationProvider.ThreadLocalSnapshot();
        SerializationProvider.setWriteNulls(true);
        assertTrue(SerializationProvider.isWriteNulls());
        snap.restore();
        assertFalse(SerializationProvider.isWriteNulls());
    }

    @Test
    void serializationProviderGetAsmDowngradeCountNonNegative() {
        assertTrue(SerializationProvider.getAsmDowngradeCount() >= 0);
    }

    // ==================== DeserializationProvider ====================

    @Test
    void deserializationProviderDeserializeNullReturnsNull() {
        assertNull(DeserializationProvider.deserialize((String) null, Object.class));
        assertNull(DeserializationProvider.deserialize("", Object.class));
    }

    @Test
    void deserializationProviderDeserializePrimitive() {
        assertEquals(Integer.valueOf(42), DeserializationProvider.deserialize("42", Integer.class));
        assertEquals(Long.valueOf(9876543210L), DeserializationProvider.deserialize("9876543210", Long.class));
        assertEquals(Double.valueOf(3.14), DeserializationProvider.deserialize("3.14", Double.class));
        assertEquals(Boolean.TRUE, DeserializationProvider.deserialize("true", Boolean.class));
        assertEquals("hello", DeserializationProvider.deserialize("\"hello\"", String.class));
    }

    @Test
    void deserializationProviderDeserializeBean() {
        TestBean b = DeserializationProvider.deserialize("{\"id\":7,\"name\":\"alice\"}", TestBean.class);
        assertNotNull(b);
        assertEquals(7, b.getId());
        assertEquals("alice", b.getName());
    }

    @Test
    void deserializationProviderDeserializeBytes() {
        byte[] bytes = "{\"id\":7,\"name\":\"bob\"}".getBytes(StandardCharsets.UTF_8);
        TestBean b = DeserializationProvider.deserialize(bytes, TestBean.class);
        assertNotNull(b);
        assertEquals(7, b.getId());
        assertEquals("bob", b.getName());
    }

    @Test
    void deserializationProviderDeserializeBytesAsciiFastPath() {
        // 纯 ASCII 应直接 char[] 构造，结果应与 String 路径一致
        byte[] bytes = "42".getBytes(StandardCharsets.US_ASCII);
        Integer v = DeserializationProvider.deserialize(bytes, Integer.class);
        assertEquals(Integer.valueOf(42), v);
    }

    @Test
    void deserializationProviderDeserializeBytesNonAscii() {
        byte[] bytes = "\"中文\"".getBytes(StandardCharsets.UTF_8);
        String v = DeserializationProvider.deserialize(bytes, String.class);
        assertEquals("中文", v);
    }

    @Test
    void deserializationProviderDeserializeToList() {
        java.lang.reflect.Type listType = new com.njydsz.common.json.type.JsonType<List<Integer>>() {}.getType();
        List<Integer> l = DeserializationProvider.deserialize("[1,2,3]", listType);
        assertNotNull(l);
        assertEquals(3, l.size());
        assertEquals(Integer.valueOf(1), l.get(0));
        assertEquals(Integer.valueOf(3), l.get(2));
    }

    @Test
    void deserializationProviderDeserializeToMap() {
        java.lang.reflect.Type mapType = new com.njydsz.common.json.type.JsonType<Map<String, Integer>>() {}.getType();
        Map<String, Integer> m = DeserializationProvider.deserialize("{\"a\":1,\"b\":2}", mapType);
        assertNotNull(m);
        assertEquals(Integer.valueOf(1), m.get("a"));
        assertEquals(Integer.valueOf(2), m.get("b"));
    }

    @Test
    void deserializationProviderDeserializeToObjectInfersType() {
        Object obj = DeserializationProvider.deserializeToObject("{\"k\":\"v\"}", Map.class);
        assertTrue(obj instanceof Map);
        assertEquals("v", ((Map<?, ?>) obj).get("k"));

        Object arr = DeserializationProvider.deserializeToObject("[1,2]", List.class);
        assertTrue(arr instanceof List);
        assertEquals(2, ((List<?>) arr).size());
    }
}
