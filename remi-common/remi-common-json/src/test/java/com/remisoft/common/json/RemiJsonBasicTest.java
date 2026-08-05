package com.remisoft.common.json;

import java.util.List;
import java.util.Map;

import com.remisoft.common.json.autotype.AutoTypeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础序列化/反序列化正确性测试（P0-E4 首批）。
 */
class RemiJsonBasicTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void roundTripTopLevelBean() {
        TestBean u = new TestBean();
        u.setId(7);
        u.setName("bob");
        String json = RemiJson.toJson(u);
        TestBean back = RemiJson.toObject(json, TestBean.class);
        assertNotNull(back);
        assertEquals(7, back.getId());
        assertEquals("bob", back.getName());
    }

    @Test
    void parseMapAndList() {
        Map<String, Object> m = RemiJson.parseMap("{\"a\":1,\"b\":\"x\"}");
        assertEquals(1, ((Number) m.get("a")).intValue());
        assertEquals("x", m.get("b"));
        List<Object> l = RemiJson.parseArray("[1,2,3]");
        assertEquals(3, l.size());
    }

    @Test
    void nullAndBooleanRoundTrip() {
        assertEquals("null", RemiJson.toJson(null));
        assertTrue(RemiJson.toObject("true", Boolean.class));
        assertEquals(42, (int) RemiJson.toObject("42", Integer.class));
    }

    @Test
    void nestedStructure() {
        String json = "{\"user\":{\"id\":5,\"name\":\"zoe\"},\"tags\":[\"a\",\"b\"]}";
        Map<String, Object> m = RemiJson.parseMap(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) m.get("user");
        assertEquals(5, ((Number) user.get("id")).intValue());
        assertEquals("zoe", user.get("name"));
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) m.get("tags");
        assertEquals(2, tags.size());
    }
}
