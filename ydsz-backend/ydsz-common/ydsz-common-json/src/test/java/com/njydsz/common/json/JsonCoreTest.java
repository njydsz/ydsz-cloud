package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.annotation.JsonAlias;

import java.nio.charset.StandardCharsets;
/**
 * YdszJson 序列化/反序列化核心功能测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class JsonCoreTest {

    @Test
    void testToJsonBasic() {
        assertEquals("null", YdszJson.toJson(null));
        assertEquals("\"hello\"", YdszJson.toJson("hello"));
        assertEquals("42", YdszJson.toJson(42));
        assertEquals("true", YdszJson.toJson(true));
    }

    @Test
    void testToJsonMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "John");
        map.put("age", 30);
        String json = YdszJson.toJson(map);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"John\""));
        assertTrue(json.contains("\"age\":30"));
    }

    @Test
    void testToObjectBasic() {
        User user = YdszJson.toObject("{\"name\":\"John\",\"age\":30}", User.class);
        assertNotNull(user);
        assertEquals("John", user.name);
        assertEquals(30, user.age);
    }

    @Test
    void testToObjectWithAlias() {
        AliasedUser user = YdszJson.toObject("{\"userName\":\"John\"}", AliasedUser.class);
        assertNotNull(user);
        assertEquals("John", user.username);
    }

    @Test
    void testToObjectWithAlias2() {
        AliasedUser user = YdszJson.toObject("{\"loginName\":\"Jane\"}", AliasedUser.class);
        assertNotNull(user);
        assertEquals("Jane", user.username);
    }

    @Test
    void testToJsonWithWriter() throws IOException {
        StringWriter writer = new StringWriter();
        YdszJson.toJson(Map.of("key", "value"), writer);
        String result = writer.toString();
        assertNotNull(result);
        assertTrue(result.contains("\"key\":\"value\""));
    }

    @Test
    void testSerializeDateTypes() {
        String json = YdszJson.toJson(LocalDate.of(2026, 7, 15));
        assertNotNull(json);
        assertTrue(json.contains("2026-07-15"));

        json = YdszJson.toJson(LocalDateTime.of(2026, 7, 15, 10, 30, 0));
        assertNotNull(json);
        assertTrue(json.contains("2026-07-15"));
        assertTrue(json.contains("10:30:00"));

        json = YdszJson.toJson(LocalTime.of(10, 30, 0));
        assertNotNull(json);
        assertTrue(json.contains("10:30:00"));

        json = YdszJson.toJson(Year.of(2026));
        assertNotNull(json);
        assertTrue(json.contains("2026"));

        json = YdszJson.toJson(YearMonth.of(2026, 7));
        assertNotNull(json);
        assertTrue(json.contains("2026-07"));

        json = YdszJson.toJson(ZonedDateTime.parse("2026-07-15T10:30:00+08:00"));
        assertNotNull(json);

        json = YdszJson.toJson(OffsetDateTime.parse("2026-07-15T10:30:00+08:00"));
        assertNotNull(json);

        json = YdszJson.toJson(new Date());
        assertNotNull(json);
    }

    @Test
    void testSerializeBigDecimal() {
        BigDecimal bd = new BigDecimal("123.456");
        String json = YdszJson.toJson(bd);
        assertNotNull(json);
        assertEquals("123.456", json);
    }

    @Test
    void testSerializeBigDecimalHighPrecision() {
        BigDecimal bd = new BigDecimal("999999999999999999999999.999999999999");
        String json = YdszJson.toJson(bd);
        assertNotNull(json);
        assertTrue(json.contains("999999999999999999999999"));
    }

    @Test
    void testParseMap() {
        Map<String, Object> map = YdszJson.parseMap("{\"a\":1,\"b\":\"hello\",\"c\":true}");
        assertNotNull(map);
        assertEquals(1, map.get("a"));
        assertEquals("hello", map.get("b"));
        assertEquals(true, map.get("c"));
    }

    @Test
    void testToJsonBytes() {
        byte[] bytes = YdszJson.toJsonBytes(Map.of("key", "value"));
        assertNotNull(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"key\":\"value\""));
    }

    // --- Test helper classes ---

    public static class User {
        public String name;
        public int age;
    }

    public static class AliasedUser {
        @JsonAlias({"userName", "loginName"})
        public String username;
    }
}
