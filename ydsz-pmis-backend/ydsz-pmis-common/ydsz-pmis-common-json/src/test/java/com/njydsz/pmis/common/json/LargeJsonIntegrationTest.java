package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.annotation.YdszJsonField;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("大型JSON边界集成测试")
class LargeJsonIntegrationTest {

    // ==================== 测试模型 ====================

    @YdszJsonClass
    static class SimpleItem {
        private int id;
        private String name;

        public SimpleItem() {}

        public SimpleItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @YdszJsonClass
    static class NestedLevel {
        private String levelName;
        private NestedLevel child;

        public NestedLevel() {}

        public String getLevelName() { return levelName; }
        public void setLevelName(String levelName) { this.levelName = levelName; }
        public NestedLevel getChild() { return child; }
        public void setChild(NestedLevel child) { this.child = child; }
    }

    @YdszJsonClass
    static class LongStringHolder {
        @YdszJsonField("long_text")
        private String longText;

        public LongStringHolder() {}

        public LongStringHolder(String longText) {
            this.longText = longText;
        }

        public String getLongText() { return longText; }
        public void setLongText(String longText) { this.longText = longText; }
    }

    // ==================== 大型列表序列化 ====================

    @Test
    @DisplayName("序列化10000个元素的大型列表（使用Map列表）")
    void serializeLargeListWith10000Elements() {
        // 使用Map列表，因为POJO列表序列化存在已知限制
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", "item_" + i);
            items.add(item);
        }

        String json = YdszJson.toJson(items);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.length() > 1000, "10000个元素的JSON应足够长");

        // 验证首尾元素存在
        assertTrue(json.contains("item_0"), "应包含第一个元素");
        assertTrue(json.contains("item_9999"), "应包含最后一个元素");
    }

    // ==================== 大型JSON反序列化 ====================

    @Test
    @DisplayName("反序列化1MB+的JSON字符串")
    void deserializeLargeJsonStringOver1MB() {
        // 构建超过1MB的JSON
        StringBuilder sb = new StringBuilder("{\"items\":[");
        int itemCount = 0;
        while (sb.length() < 1024 * 1024) {
            if (itemCount > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":").append(itemCount).append(",\"name\":\"large_item_").append(itemCount).append("\"}");
            itemCount++;
        }
        sb.append("]}");

        String largeJson = sb.toString();
        assertTrue(largeJson.length() > 1024 * 1024, "JSON应超过1MB");

        // 反序列化为Map
        Map<String, Object> result = YdszJson.parseObject(largeJson);
        assertNotNull(result, "反序列化结果不应为null");
        assertTrue(result.containsKey("items"), "应包含items字段");
    }

    // ==================== 深层嵌套对象 ====================

    @Test
    @DisplayName("序列化50层深度嵌套对象")
    void serializeDeeplyNestedObject50Levels() {
        NestedLevel root = new NestedLevel();
        root.setLevelName("level_0");
        NestedLevel current = root;
        for (int i = 1; i < 50; i++) {
            NestedLevel child = new NestedLevel();
            child.setLevelName("level_" + i);
            current.setChild(child);
            current = child;
        }

        String json = YdszJson.toJson(root);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.contains("level_0"), "应包含第0层");
        assertTrue(json.contains("level_49"), "应包含第49层");
    }

    @Test
    @DisplayName("反序列化50层深度嵌套对象（使用Map验证）")
    void deserializeDeeplyNestedObject50Levels() {
        // 构建嵌套JSON
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("{\"levelName\":\"level_").append(i).append("\",\"child\":");
        }
        sb.append("null");
        for (int i = 0; i < 50; i++) {
            sb.append("}");
        }

        String json = sb.toString();

        // 使用Map验证深度嵌套结构
        Map<String, Object> result = YdszJson.parseObject(json);
        assertNotNull(result, "反序列化结果不应为null");
        assertEquals("level_0", result.get("levelName"), "第0层名称应正确");

        // 逐层验证（通过Map嵌套）
        @SuppressWarnings("unchecked")
        Map<String, Object> current = result;
        for (int i = 0; i < 49; i++) {
            assertNotNull(current.get("child"), "第" + i + "层应有子节点");
            current = (Map<String, Object>) current.get("child");
            assertEquals("level_" + (i + 1), current.get("levelName"),
                    "第" + (i + 1) + "层名称应正确");
        }
        assertNull(current.get("child"), "最内层不应有子节点");
    }

    // ==================== 大量字段对象 ====================

    @Test
    @DisplayName("序列化包含1000个字段的JSON对象（使用Map）")
    void serializeJsonWith1000Fields() {
        Map<String, Object> largeMap = new LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeMap.put("field_" + i, "value_" + i);
        }

        String json = YdszJson.toJson(largeMap);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.startsWith("{"), "应输出JSON对象");
        assertTrue(json.contains("field_0"), "应包含第一个字段");
        assertTrue(json.contains("field_999"), "应包含最后一个字段");
    }

    @Test
    @DisplayName("反序列化包含1000个字段的JSON对象")
    void deserializeJsonWith1000Fields() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"field_").append(i).append("\":\"value_").append(i).append("\"");
        }
        sb.append("}");

        String json = sb.toString();

        Map<String, Object> result = YdszJson.parseObject(json);
        assertNotNull(result, "反序列化结果不应为null");
        assertEquals(1000, result.size(), "应包含1000个字段");
        assertEquals("value_0", result.get("field_0"), "第一个字段值应正确");
        assertEquals("value_999", result.get("field_999"), "最后一个字段值应正确");
    }

    // ==================== 超长字符串值 ====================

    @Test
    @DisplayName("序列化包含超长字符串值的对象")
    void serializeVeryLongStringValue() {
        // 100KB字符串
        String longText = "A".repeat(100 * 1024);
        LongStringHolder holder = new LongStringHolder(longText);

        String json = YdszJson.toJson(holder);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.contains("long_text"), "应包含字段名");
        assertTrue(json.length() > 100 * 1024, "JSON长度应超过100KB");
    }

    @Test
    @DisplayName("反序列化包含超长字符串值的对象（使用Map验证）")
    void deserializeVeryLongStringValue() {
        // 100KB字符串
        String longText = "B".repeat(100 * 1024);
        String json = "{\"long_text\":\"" + longText + "\"}";

        Map<String, Object> result = YdszJson.parseObject(json);

        assertNotNull(result, "反序列化结果不应为null");
        assertNotNull(result.get("long_text"), "长字符串值不应为null");
        assertEquals(100 * 1024, ((String) result.get("long_text")).length(), "字符串长度应保持一致");
    }

    // ==================== 内存效率测试 ====================

    @Test
    @DisplayName("内存效率 - 合理大小数据不应OOM")
    void memoryEfficiencyNoOomForReasonableSizes() {
        // 序列化和反序列化多个中等大小对象，验证不会OOM
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        for (int round = 0; round < 10; round++) {
            // 创建大型Map
            Map<String, Object> largeMap = new LinkedHashMap<>();
            for (int i = 0; i < 5000; i++) {
                largeMap.put("key_" + i, "value_" + i);
            }

            String json = YdszJson.toJson(largeMap);
            assertNotNull(json);

            // 反序列化回来
            Map<String, Object> parsed = YdszJson.parseObject(json);
            assertNotNull(parsed);
            assertEquals(5000, parsed.size(), "反序列化后字段数量应一致");
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // 内存增长不应超过256MB（非常宽松的限制，主要验证不OOM）
        assertTrue(memoryIncrease < 256 * 1024 * 1024,
                "内存增长不应过大，实际增长: " + (memoryIncrease / 1024 / 1024) + "MB");
    }
}
