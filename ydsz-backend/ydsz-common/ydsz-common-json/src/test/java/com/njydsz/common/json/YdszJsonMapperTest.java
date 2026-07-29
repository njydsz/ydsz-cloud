package com.njydsz.common.json;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.type.YdszJsonType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YdszJsonMapper 单元测试
 *
 * <p>覆盖实例化 API：toJson/toObject/convertValue/treeToValue/readTree/
 * writeValueAsString/writeValueAsBytes/readValue/fromJson/fromJsonBytes 等</p>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
class YdszJsonMapperTest {

    private final YdszJsonMapper mapper = new YdszJsonMapper();

    // ==================== 基础序列化/反序列化 ====================

    @Test
    void testToJson_and_fromJson_roundtrip() {
        TestBean bean = new TestBean();
        bean.setName("test");
        bean.setAge(25);
        String json = mapper.toJson(bean);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        TestBean result = mapper.fromJson(json, TestBean.class);
        assertEquals("test", result.getName());
        assertEquals(25, result.getAge());
    }

    @Test
    void testToJson_null() {
        assertEquals("null", mapper.toJson(null));
    }

    @Test
    void testToJsonBytes() {
        TestBean bean = new TestBean();
        bean.setName("bytes");
        byte[] bytes = mapper.toJsonBytes(bean);
        assertNotNull(bytes);
        TestBean result = mapper.fromJsonBytes(bytes, TestBean.class);
        assertEquals("bytes", result.getName());
    }

    @Test
    void testToJson_pretty() {
        TestBean bean = new TestBean();
        bean.setName("pretty");
        String json = mapper.toJson(bean, true);
        assertNotNull(json);
        assertTrue(json.contains("\n") || json.contains("  "));
    }

    // ==================== convertValue ====================

    @Test
    void testConvertValue_class() {
        TestBean bean = new TestBean();
        bean.setName("convert");
        bean.setAge(30);
        TestBean result = mapper.convertValue(bean, TestBean.class);
        assertEquals("convert", result.getName());
        assertEquals(30, result.getAge());
    }

    @Test
    void testConvertValue_fromMap() {
        Map<String, Object> map = Map.of("name", "fromMap", "age", 20);
        TestBean result = mapper.convertValue(map, TestBean.class);
        assertEquals("fromMap", result.getName());
        assertEquals(20, result.getAge());
    }

    @Test
    void testConvertValue_null() {
        assertNull(mapper.convertValue(null, TestBean.class));
    }

    // ==================== 树模型 ====================

    @Test
    void testReadTree() {
        String json = "{\"name\":\"tree\",\"age\":42}";
        JsonNode node = mapper.readTree(json);
        assertNotNull(node);
        assertTrue(node.isObject());
    }

    @Test
    void testValueToTree() {
        TestBean bean = new TestBean();
        bean.setName("valueToTree");
        JsonNode node = mapper.valueToTree(bean);
        assertNotNull(node);
        assertTrue(node.isObject());
    }

    @Test
    void testTreeToValue() {
        String json = "{\"name\":\"treeToValue\",\"age\":15}";
        JsonNode node = mapper.readTree(json);
        TestBean result = mapper.treeToValue(node, TestBean.class);
        assertEquals("treeToValue", result.getName());
        assertEquals(15, result.getAge());
    }

    @Test
    void testTreeToValue_null() {
        assertNull(mapper.treeToValue(null, TestBean.class));
    }

    // ==================== Jackson 兼容 API ====================

    @Test
    void testWriteValueAsString() {
        TestBean bean = new TestBean();
        bean.setName("writeValue");
        String json = mapper.writeValueAsString(bean);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"writeValue\""));
    }

    @Test
    void testWriteValueAsBytes() {
        TestBean bean = new TestBean();
        bean.setName("writeBytes");
        byte[] bytes = mapper.writeValueAsBytes(bean);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void testReadValue_string_type() {
        String json = "{\"name\":\"readValue\",\"age\":50}";
        TestBean result = mapper.readValue(json, TestBean.class);
        assertEquals("readValue", result.getName());
        assertEquals(50, result.getAge());
    }

    // ==================== format ====================

    @Test
    void testFormat() {
        TestBean bean = new TestBean();
        bean.setName("format");
        String json = mapper.format(bean);
        assertNotNull(json);
        assertTrue(json.contains("\n") || json.contains("  "));
    }

    // ==================== 集合类型 ====================

    @Test
    void testParseArray() {
        String json = "[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]";
        List<TestBean> list = mapper.parseArray(json, TestBean.class);
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("a", list.get(0).getName());
        assertEquals("b", list.get(1).getName());
    }

    @Test
    void testParseMap() {
        String json = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        Map<String, Object> map = mapper.parseMap(json);
        assertNotNull(map);
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
    }

    // ==================== copy ====================

    @Test
    void testCopy_independentConfig() {
        YdszJsonMapper copy = mapper.copy();
        assertNotNull(copy);
        assertNotSame(mapper, copy);
        assertNotSame(mapper.getConfig(), copy.getConfig());
    }

    // ==================== fromJsonBytes ====================

    @Test
    void testFromJsonBytes_empty() {
        assertNull(mapper.fromJsonBytes(null, TestBean.class));
        assertNull(mapper.fromJsonBytes(new byte[0], TestBean.class));
    }

    // ==================== toObject with YdszJsonType ====================

    @Test
    void testToObject_withTypeRef() {
        String json = "[{\"name\":\"x\",\"age\":1}]";
        List<TestBean> result = mapper.toObject(json, new YdszJsonType<List<TestBean>>() {});
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("x", result.get(0).getName());
    }

    // ==================== warmup ====================

    @Test
    void testWarmup() {
        assertDoesNotThrow(() -> mapper.warmup(TestBean.class));
    }

    // ==================== 测试 Bean ====================

    public static class TestBean {
        private String name;
        private int age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
}
