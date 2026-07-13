package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.stream.JsonGenerator;
import com.njydsz.pmis.common.json.stream.JsonParser;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YdszJson Streaming API 测试")
class YdszJsonStreamTest {

    // ==================== JsonParser ====================

    @Nested
    @DisplayName("JsonParser 测试")
    class JsonParserTests {

        @Test
        @DisplayName("解析简单对象 - START_OBJECT 和 END_OBJECT")
        void parseSimpleObjectTokens() throws IOException {
            String json = "{\"name\":\"John\"}";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                // 字段名 token
                JsonParser.Token fieldToken = parser.nextToken();
                assertTrue(fieldToken == JsonParser.Token.FIELD_NAME || fieldToken == JsonParser.Token.VALUE_STRING);
                // 值 token
                JsonParser.Token valueToken = parser.nextToken();
                assertTrue(valueToken == JsonParser.Token.VALUE_STRING);
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析简单数组")
        void parseSimpleArray() throws IOException {
            String json = "[1,2,3]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(1, parser.intValue());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(2, parser.intValue());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(3, parser.intValue());
                assertEquals(JsonParser.Token.END_ARRAY, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析布尔值")
        void parseBooleans() throws IOException {
            String json = "[true,false]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_TRUE, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_FALSE, parser.nextToken());
                assertEquals(JsonParser.Token.END_ARRAY, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析 null 值")
        void parseNull() throws IOException {
            String json = "[null]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_NULL, parser.nextToken());
                assertEquals(JsonParser.Token.END_ARRAY, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析浮点数")
        void parseFloatNumber() throws IOException {
            String json = "[3.14]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_NUMBER_FLOAT, parser.nextToken());
                assertEquals(3.14, parser.doubleValue(), 0.001);
            }
        }

        @Test
        @DisplayName("解析负数")
        void parseNegativeNumber() throws IOException {
            String json = "[-42]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(-42, parser.intValue());
            }
        }

        @Test
        @DisplayName("解析空对象")
        void parseEmptyObject() throws IOException {
            String json = "{}";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析空数组")
        void parseEmptyArray() throws IOException {
            String json = "[]";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.END_ARRAY, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析字符串中的转义字符")
        void parseEscapedString() throws IOException {
            String json = "{\"value\":\"hello\\\\nworld\"}";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                // 跳过字段名
                parser.nextToken();
                // 获取值
                JsonParser.Token token = parser.nextToken();
                assertTrue(token == JsonParser.Token.VALUE_STRING);
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
            }
        }

        @Test
        @DisplayName("解析结束后返回 null")
        void parseEndReturnsNull() throws IOException {
            String json = "42";
            try (JsonParser parser = JsonParser.of(json)) {
                parser.nextToken();
                assertNull(parser.nextToken());
            }
        }

        @Test
        @DisplayName("currentToken 返回当前令牌")
        void currentTokenReturnsCurrent() throws IOException {
            String json = "{}";
            try (JsonParser parser = JsonParser.of(json)) {
                parser.nextToken();
                assertEquals(JsonParser.Token.START_OBJECT, parser.currentToken());
            }
        }

        @Test
        @DisplayName("关闭后再操作抛出异常")
        void closedParserThrows() throws IOException {
            JsonParser parser = JsonParser.of("{}");
            parser.close();
            assertThrows(IllegalStateException.class, parser::nextToken);
        }

        @Test
        @DisplayName("of 工厂方法创建解析器")
        void ofFactoryMethod() {
            JsonParser parser = JsonParser.of("{\"key\":\"value\"}");
            assertNotNull(parser);
        }

        @Test
        @DisplayName("of 工厂方法创建解析器（空对象）")
        void ofFactoryMethodEmptyObject() {
            JsonParser parser = JsonParser.of("{}");
            assertNotNull(parser);
        }

        @Test
        @DisplayName("嵌套对象解析")
        void nestedObjectParsing() throws IOException {
            String json = "{\"a\":{\"b\":1}}";
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                // 字段名 "a"
                parser.nextToken();
                // 嵌套对象
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                // 字段名 "b"
                parser.nextToken();
                // 值 1
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(1, parser.intValue());
                // 内层结束
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
                // 外层结束
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
            }
        }
    }

    // ==================== JsonGenerator ====================

    @Nested
    @DisplayName("JsonGenerator 测试")
    class JsonGeneratorTests {

        @Test
        @DisplayName("写入简单对象")
        void writeSimpleObject() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartObject();
                gen.writeName("name");
                gen.writeString("John");
                gen.writeName("age");
                gen.writeNumber(30);
                gen.writeEndObject();
            }
            assertEquals("{\"name\":\"John\",\"age\":30}", sw.toString());
        }

        @Test
        @DisplayName("写入简单数组")
        void writeSimpleArray() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNumber(1);
                gen.writeNumber(2);
                gen.writeNumber(3);
                gen.writeEndArray();
            }
            assertEquals("[1,2,3]", sw.toString());
        }

        @Test
        @DisplayName("写入布尔值")
        void writeBooleans() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeBoolean(true);
                gen.writeBoolean(false);
                gen.writeEndArray();
            }
            assertEquals("[true,false]", sw.toString());
        }

        @Test
        @DisplayName("写入 null 值")
        void writeNull() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNull();
                gen.writeEndArray();
            }
            assertEquals("[null]", sw.toString());
        }

        @Test
        @DisplayName("写入浮点数")
        void writeDouble() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNumber(3.14);
                gen.writeEndArray();
            }
            String result = sw.toString();
            assertTrue(result.contains("3.14"));
        }

        @Test
        @DisplayName("写入长整数")
        void writeLong() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNumber(9999999999L);
                gen.writeEndArray();
            }
            assertTrue(sw.toString().contains("9999999999"));
        }

        @Test
        @DisplayName("写入 null 字符串")
        void writeNullString() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeString(null);
                gen.writeEndArray();
            }
            assertEquals("[null]", sw.toString());
        }

        @Test
        @DisplayName("写入原始 JSON")
        void writeRawJson() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartObject();
                gen.writeName("data");
                gen.writeRaw("{\"nested\":true}");
                gen.writeEndObject();
            }
            assertEquals("{\"data\":{\"nested\":true}}", sw.toString());
        }

        @Test
        @DisplayName("格式化输出")
        void prettyPrintOutput() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw, true)) {
                gen.writeStartObject();
                gen.writeName("name");
                gen.writeString("John");
                gen.writeEndObject();
            }
            String result = sw.toString();
            assertTrue(result.contains("\n") || result.contains("  "));
        }

        @Test
        @DisplayName("NaN 和 Infinity 写入 null")
        void nanAndInfinityWriteNull() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNumber(Double.NaN);
                gen.writeNumber(Double.POSITIVE_INFINITY);
                gen.writeEndArray();
            }
            assertEquals("[null,null]", sw.toString());
        }

        @Test
        @DisplayName("关闭后再操作抛出异常")
        void closedGeneratorThrows() throws IOException {
            StringWriter sw = new StringWriter();
            JsonGenerator gen = JsonGenerator.of(sw);
            gen.close();
            assertThrows(IllegalStateException.class, gen::writeStartObject);
        }

        @Test
        @DisplayName("字符串转义")
        void stringEscape() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartObject();
                gen.writeName("value");
                gen.writeString("line1\nline2\ttab");
                gen.writeEndObject();
            }
            String result = sw.toString();
            assertTrue(result.contains("\\n"));
            assertTrue(result.contains("\\t"));
        }

        @Test
        @DisplayName("of 工厂方法")
        void ofFactoryMethod() {
            StringWriter sw = new StringWriter();
            JsonGenerator gen = JsonGenerator.of(sw);
            assertNotNull(gen);
        }
    }

    // ==================== Streaming Round-trip ====================

    @Nested
    @DisplayName("流式往返测试")
    class StreamingRoundTripTests {

        @Test
        @DisplayName("生成后解析往返 - 数组")
        void generateThenParseArray() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartArray();
                gen.writeNumber(1);
                gen.writeNumber(2);
                gen.writeNumber(3);
                gen.writeEndArray();
            }

            String json = sw.toString();
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_ARRAY, parser.nextToken());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(1, parser.intValue());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(2, parser.intValue());
                assertEquals(JsonParser.Token.VALUE_NUMBER_INT, parser.nextToken());
                assertEquals(3, parser.intValue());
                assertEquals(JsonParser.Token.END_ARRAY, parser.nextToken());
            }
        }

        @Test
        @DisplayName("生成后解析往返 - 对象")
        void generateThenParseObject() throws IOException {
            StringWriter sw = new StringWriter();
            try (JsonGenerator gen = JsonGenerator.of(sw)) {
                gen.writeStartObject();
                gen.writeName("name");
                gen.writeString("Alice");
                gen.writeEndObject();
            }

            String json = sw.toString();
            try (JsonParser parser = JsonParser.of(json)) {
                assertEquals(JsonParser.Token.START_OBJECT, parser.nextToken());
                // 字段名
                JsonParser.Token fieldToken = parser.nextToken();
                assertTrue(fieldToken == JsonParser.Token.FIELD_NAME || fieldToken == JsonParser.Token.VALUE_STRING);
                // 值
                JsonParser.Token valueToken = parser.nextToken();
                assertTrue(valueToken == JsonParser.Token.VALUE_STRING);
                assertEquals(JsonParser.Token.END_OBJECT, parser.nextToken());
            }
        }
    }
}
