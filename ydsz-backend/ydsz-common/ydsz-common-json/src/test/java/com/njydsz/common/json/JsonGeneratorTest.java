package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.stream.JsonGenerator;

/**
 * JsonGenerator 流式生成器测试。
 *
 * @since 1.0.0
 */
class JsonGeneratorTest {

    @Test
    void testWriteSimpleObject() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartObject();
            gen.writeName("name");
            gen.writeString("John");
            gen.writeName("age");
            gen.writeNumber(30);
            gen.writeEndObject();
        }
        String json = sw.toString();
        assertEquals("{\"name\":\"John\",\"age\":30}", json);
    }

    @Test
    void testWriteArray() throws IOException {
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
    void testWriteNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartObject();
            gen.writeName("value");
            gen.writeNull();
            gen.writeEndObject();
        }
        assertEquals("{\"value\":null}", sw.toString());
    }

    @Test
    void testWriteBoolean() throws IOException {
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
    void testWriteObject() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartObject();
            gen.writeName("nested");
            gen.writeObject(Map.of("key", "value", "num", 42));
            gen.writeEndObject();
        }
        String json = sw.toString();
        assertNotNull(json);
        assertTrue(json.contains("\"key\":\"value\""));
        assertTrue(json.contains("\"num\":42"));
    }

    @Test
    void testWriteObjectNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartArray();
            gen.writeObject(null);
            gen.writeEndArray();
        }
        assertEquals("[null]", sw.toString());
    }

    @Test
    void testWriteRaw() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartObject();
            gen.writeName("raw");
            gen.writeRaw("{\"custom\":true}");
            gen.writeEndObject();
        }
        assertEquals("{\"raw\":{\"custom\":true}}", sw.toString());
    }

    @Test
    void testPrettyPrint() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw, true)) {
            gen.writeStartObject();
            gen.writeName("name");
            gen.writeString("John");
            gen.writeName("age");
            gen.writeNumber(30);
            gen.writeEndObject();
        }
        String json = sw.toString();
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  "));
    }

    @Test
    void testNestedStructures() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = JsonGenerator.of(sw)) {
            gen.writeStartObject();
            gen.writeName("items");
            gen.writeStartArray();
            gen.writeStartObject();
            gen.writeName("id");
            gen.writeNumber(1);
            gen.writeEndObject();
            gen.writeStartObject();
            gen.writeName("id");
            gen.writeNumber(2);
            gen.writeEndObject();
            gen.writeEndArray();
            gen.writeEndObject();
        }
        String json = sw.toString();
        assertNotNull(json);
        assertTrue(json.contains("\"items\":[{\"id\":1},{\"id\":2}]"));
    }
}
