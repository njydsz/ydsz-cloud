package com.remisoft.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.remisoft.common.json.parser.JsonParser;
import com.remisoft.common.json.parser.JsonParser.JsonToken;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JsonParser 流式解析器测试（P1-1）。
 */
class JsonParserTest {

    @Test
    @DisplayName("nextToken: 解析简单对象的所有 token")
    void nextToken_parsesSimpleObject() throws IOException {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        try (JsonParser parser = JsonParser.from(json)) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("name", parser.getCurrentName());

            assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
            assertEquals("Alice", parser.getText());

            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("age", parser.getCurrentName());

            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(30, parser.getIntValue());

            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        }
    }

    @Test
    @DisplayName("nextToken: 解析嵌套对象")
    void nextToken_parsesNestedObject() throws IOException {
        String json = "{\"outer\":{\"inner\":\"value\"}}";
        try (JsonParser parser = JsonParser.from(json)) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("outer", parser.getCurrentName());
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("inner", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_STRING, parser.nextToken());
            assertEquals("value", parser.getText());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        }
    }

    @Test
    @DisplayName("nextToken: 解析数组")
    void nextToken_parsesArray() throws IOException {
        String json = "[1,2,3]";
        try (JsonParser parser = JsonParser.from(json)) {
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(1, parser.getIntValue());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(2, parser.getIntValue());
            assertEquals(JsonToken.VALUE_NUMBER_INT, parser.nextToken());
            assertEquals(3, parser.getIntValue());
            assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        }
    }

    @Test
    @DisplayName("value reading: 各种数值类型")
    void valueReading_numberTypes() throws IOException {
        String json = "{\"int\":42,\"neg\":-100,\"float\":3.14,\"exp\":1.5e10}";
        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT

            parser.nextToken(); // FIELD_NAME "int"
            parser.nextToken(); // VALUE_NUMBER_INT
            assertEquals(42, parser.getIntValue());
            assertEquals(42L, parser.getLongValue());

            parser.nextToken(); // FIELD_NAME "neg"
            parser.nextToken(); // VALUE_NUMBER_INT
            assertEquals(-100, parser.getIntValue());

            parser.nextToken(); // FIELD_NAME "float"
            parser.nextToken(); // VALUE_NUMBER_FLOAT
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, parser.currentToken());
            assertEquals(3.14, parser.getDoubleValue(), 0.001);

            parser.nextToken(); // FIELD_NAME "exp"
            parser.nextToken(); // VALUE_NUMBER_FLOAT
            assertTrue(parser.getDoubleValue() > 1e9);
        }
    }

    @Test
    @DisplayName("value reading: 布尔值和 null")
    void valueReading_booleansAndNull() throws IOException {
        String json = "{\"t\":true,\"f\":false,\"n\":null}";
        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT

            parser.nextToken(); // FIELD_NAME "t"
            parser.nextToken(); // VALUE_TRUE
            assertEquals(JsonToken.VALUE_TRUE, parser.currentToken());
            assertTrue(parser.getBooleanValue());
            assertEquals("true", parser.getValueAsString());

            parser.nextToken(); // FIELD_NAME "f"
            parser.nextToken(); // VALUE_FALSE
            assertEquals(JsonToken.VALUE_FALSE, parser.currentToken());
            assertFalse(parser.getBooleanValue());

            parser.nextToken(); // FIELD_NAME "n"
            parser.nextToken(); // VALUE_NULL
            assertEquals(JsonToken.VALUE_NULL, parser.currentToken());
            assertNull(parser.getValueAsString());

            parser.nextToken(); // END_OBJECT
        }
    }

    @Test
    @DisplayName("skipValue: 跳过不关心的字段")
    void skipValue_skipsFields() throws IOException {
        String json = "{\"name\":\"Bob\",\"metadata\":{\"key\":\"value\"},\"score\":95}";
        List<String> values = new ArrayList<>();

        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.getCurrentName();
                parser.nextToken(); // move to value
                if ("name".equals(name)) {
                    values.add(parser.getText());
                } else {
                    parser.skipValue();
                }
            }
        }

        assertEquals(1, values.size());
        assertEquals("Bob", values.get(0));
    }

    @Test
    @DisplayName("skipChildren: 跳过嵌套对象")
    void skipChildren_skipsNested() throws IOException {
        String json = "{\"keep\":\"yes\",\"skip\":{\"a\":1,\"b\":2},\"another\":\"value\"}";
        List<String> found = new ArrayList<>();

        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.getCurrentName();
                if ("keep".equals(name) || "another".equals(name)) {
                    parser.nextToken(); // value
                    found.add(name + "=" + parser.getText());
                } else {
                    parser.nextToken(); // move to value
                    parser.skipChildren();
                }
            }
        }

        assertEquals(2, found.size());
        assertTrue(found.contains("keep=yes"));
        assertTrue(found.contains("another=value"));
    }

    @Test
    @DisplayName("from(InputStream): 从输入流解析")
    void fromInputStream() throws IOException {
        String json = "{\"stream\":true}";
        ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        try (JsonParser parser = JsonParser.from(bais)) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            assertEquals(JsonToken.FIELD_NAME, parser.nextToken());
            assertEquals("stream", parser.getCurrentName());
            assertEquals(JsonToken.VALUE_TRUE, parser.nextToken());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        }
    }

    @Test
    @DisplayName("getText: 字段名作为 text")
    void getText_fieldName() throws IOException {
        String json = "{\"key\":\"val\"}";
        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT
            parser.nextToken(); // FIELD_NAME
            assertEquals("key", parser.getText());
        }
    }

    @Test
    @DisplayName("getCurrentLocation: 位置跟踪")
    void getCurrentLocation_positionTracking() throws IOException {
        String json = "{\"a\":1}";
        try (JsonParser parser = JsonParser.from(json)) {
            int before = parser.getCurrentLocation();
            parser.nextToken(); // START_OBJECT
            int after = parser.getCurrentLocation();
            assertTrue(after > before);
        }
    }

    @Test
    @DisplayName("closed parser throws on access")
    void closedParser_throws() throws IOException {
        String json = "{}";
        JsonParser parser = JsonParser.from(json);
        parser.close();
        assertTrue(parser.isClosed());
        assertThrows(IllegalStateException.class, parser::nextToken);
    }

    @Test
    @DisplayName("getValueAsString: 对数值返回字符串")
    void getValueAsString_number() throws IOException {
        String json = "{\"num\":123}";
        try (JsonParser parser = JsonParser.from(json)) {
            parser.nextToken(); // START_OBJECT
            parser.nextToken(); // FIELD_NAME
            parser.nextToken(); // VALUE_NUMBER_INT
            assertEquals("123", parser.getValueAsString());
        }
    }

    @Test
    @DisplayName("解析空对象和空数组")
    void emptyObjectAndArray() throws IOException {
        try (JsonParser parser = JsonParser.from("{},[]")) {
            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            assertEquals(JsonToken.END_OBJECT, parser.nextToken());
            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            assertEquals(JsonToken.END_ARRAY, parser.nextToken());
        }
    }
}
