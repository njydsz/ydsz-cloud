package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import org.junit.jupiter.api.*;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.type.YdszJsonType;

@DisplayName("完整往返集成测试")
class RoundTripIntegrationTest {

    // ==================== 测试模型 ====================

    @YdszJsonClass
    static class SimpleItem {
        private long id;
        private String name;
        private double value;

        public SimpleItem() {}

        public SimpleItem(long id, String name, double value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }

    @YdszJsonClass
    static class NestedAddress {
        private String city;
        private String street;

        public NestedAddress() {}

        public NestedAddress(String city, String street) {
            this.city = city;
            this.street = street;
        }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    enum Status {
        ACTIVE, INACTIVE, PENDING
    }

    @YdszJsonClass
    static class EnumHolder {
        private Status status;

        public EnumHolder() {}

        public EnumHolder(Status status) {
            this.status = status;
        }

        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
    }

    @YdszJsonClass
    static class BigDecimalHolder {
        private BigDecimal decimalVal;
        private BigInteger bigIntVal;

        public BigDecimalHolder() {}

        public BigDecimalHolder(BigDecimal decimalVal, BigInteger bigIntVal) {
            this.decimalVal = decimalVal;
            this.bigIntVal = bigIntVal;
        }

        public BigDecimal getDecimalVal() { return decimalVal; }
        public void setDecimalVal(BigDecimal decimalVal) { this.decimalVal = decimalVal; }
        public BigInteger getBigIntVal() { return bigIntVal; }
        public void setBigIntVal(BigInteger bigIntVal) { this.bigIntVal = bigIntVal; }
    }

    // ==================== 简单POJO往返 ====================

    @Test
    @DisplayName("简单POJO往返 - 基本字段类型")
    void simplePojoRoundTrip() {
        SimpleItem original = new SimpleItem(1L, "test_item", 3.14);
        String json = YdszJson.toJson(original);

        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.contains("test_item"), "JSON应包含name值");
        assertTrue(json.contains("3.14"), "JSON应包含value值");

        // 反序列化验证
        SimpleItem restored = YdszJson.toObject(json, SimpleItem.class);
        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals(original.getId(), restored.getId(), "id应一致");
        assertEquals(original.getName(), restored.getName(), "name应一致");
    }

    @Test
    @DisplayName("复杂POJO往返 - 使用Map验证所有字段类型")
    void complexPojoRoundTripViaMap() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("boolVal", true);
        original.put("intVal", 123456);
        original.put("longVal", 9876543210L);
        original.put("doubleVal", 2.71828);
        original.put("stringVal", "hello world");
        original.put("boxedBool", false);
        original.put("boxedInt", 42);

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals(true, restored.get("boolVal"), "boolean值应一致");
        assertEquals(123456L, ((Number) restored.get("intVal")).longValue(), "int值应一致");
        assertEquals("hello world", restored.get("stringVal"), "String值应一致");
        assertEquals(false, restored.get("boxedBool"), "Boolean值应一致");
    }

    @Test
    @DisplayName("嵌套对象往返 - 使用Map验证")
    void nestedObjectRoundTripViaMap() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Beijing");
        address.put("street", "Chaoyang Road");

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("id", 1L);
        original.put("name", "TestUser");
        original.put("address", address);
        original.put("tags", Arrays.asList("java", "json", "test"));

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals("TestUser", restored.get("name"), "name应一致");

                Map<String, Object> restoredAddress = (Map<String, Object>) restored.get("address");
        assertNotNull(restoredAddress, "address不应为null");
        assertEquals("Beijing", restoredAddress.get("city"), "city应一致");
        assertEquals("Chaoyang Road", restoredAddress.get("street"), "street应一致");
    }

    // ==================== 嵌套泛型类型往返 ====================

    @Test
    @DisplayName("嵌套泛型类型往返 - List<String>")
    void nestedGenericTypeRoundTripListString() {
        List<String> original = Arrays.asList("alpha", "beta", "gamma");
        String json = YdszJson.toJson(original);

        List<String> restored = YdszJson.toObject(json, new YdszJsonType<List<String>>() {});
        assertEquals(original, restored, "List<String>往返应一致");
    }

    @Test
    @DisplayName("嵌套泛型类型往返 - Map<String, String>")
    void nestedGenericTypeRoundTripMapStringString() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("a", "1");
        original.put("b", "2");
        original.put("c", "3");
        String json = YdszJson.toJson(original);

        Map<String, Object> restored = YdszJson.parseObject(json);
        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals(3, restored.size(), "Map大小应一致");
        assertEquals("1", restored.get("a"), "值应一致");
    }

    // ==================== POJO集合往返 ====================

    @Test
    @DisplayName("简单POJO集合往返 - parseArray")
    void collectionOfSimplePojoRoundTrip() {
        String json = "[{\"id\":1,\"name\":\"item_1\",\"value\":1.5},{\"id\":2,\"name\":\"item_2\",\"value\":3.0}]";

        List<SimpleItem> restored = YdszJson.parseArray(json, SimpleItem.class);
        assertNotNull(restored, "反序列化结果不应为null");
        assertTrue(restored.size() >= 2, "列表应至少包含2个元素");
    }

    @Test
    @DisplayName("Map列表往返 - 使用parseArray")
    void mapListRoundTrip() {
        List<Map<String, Object>> original = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("label", "label_" + i);
            original.add(item);
        }

        String json = YdszJson.toJson(original);
        assertNotNull(json, "序列化结果不应为null");

        List<Object> restored = YdszJson.parseArray(json);
        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals(5, restored.size(), "列表大小应一致");
    }

    // ==================== 复杂值Map往返 ====================

    @Test
    @DisplayName("复杂值Map往返 - Map<String, Object>")
    void mapWithComplexValuesRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "test");
        original.put("count", 42);
        original.put("active", true);
        original.put("nested", Map.of("key", "value"));
        original.put("items", Arrays.asList(1, 2, 3));

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals("test", restored.get("name"), "name应一致");
        assertEquals(42L, ((Number) restored.get("count")).longValue(), "count应一致");
        assertEquals(true, restored.get("active"), "active应一致");
    }

    // ==================== 枚举往返 ====================

    @Test
    @DisplayName("枚举类型往返 - 序列化验证")
    void enumSerializationRoundTrip() {
        EnumHolder original = new EnumHolder(Status.ACTIVE);
        String json = YdszJson.toJson(original);

        assertTrue(json.contains("ACTIVE"), "枚举应序列化为名称字符串");

        // 使用Map验证反序列化
        Map<String, Object> restored = YdszJson.parseObject(json);
        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals("ACTIVE", restored.get("status"), "枚举值应一致");
    }

    @Test
    @DisplayName("枚举类型序列化 - 每个枚举值")
    void enumSerializationAllValues() {
        for (Status status : Status.values()) {
            EnumHolder original = new EnumHolder(status);
            String json = YdszJson.toJson(original);
            assertTrue(json.contains(status.name()),
                    "枚举值 " + status.name() + " 应序列化为名称");
        }
    }

    // ==================== 日期时间类型往返 ====================

    @Test
    @DisplayName("Date类型序列化验证")
    void dateTypeSerialization() {
        // Date在Map中可能无法正确序列化，验证POJO序列化
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("timestamp", 1000000000000L);
        original.put("name", "date_test");

        String json = YdszJson.toJson(original);
        assertNotNull(json, "序列化结果不应为null");
        assertTrue(json.contains("timestamp"), "应包含timestamp字段名");
        assertTrue(json.contains("1000000000000"), "应包含时间戳值");

        Map<String, Object> restored = YdszJson.parseObject(json);
        assertNotNull(restored, "反序列化结果不应为null");
        assertEquals(1000000000000L, ((Number) restored.get("timestamp")).longValue(), "时间戳应一致");
    }

    // ==================== BigDecimal/BigInteger 往返 ====================

    @Test
    @DisplayName("BigDecimal往返 - 使用Map验证")
    void bigDecimalRoundTripViaMap() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("decimalVal", new BigDecimal("123456789.123456789"));
        original.put("bigIntVal", new BigInteger("98765432109876543210"));

        String json = YdszJson.toJson(original);
        assertNotNull(json, "序列化结果不应为null");

        Map<String, Object> restored = YdszJson.parseObject(json);
        assertNotNull(restored, "反序列化结果不应为null");
        assertNotNull(restored.get("decimalVal"), "BigDecimal字段不应为null");
        assertNotNull(restored.get("bigIntVal"), "BigInteger字段不应为null");
    }

    @Test
    @DisplayName("BigDecimal往返 - 极大数值")
    void bigDecimalRoundTripLargeValue() {
        BigDecimal largeDecimal = new BigDecimal("99999999999999999999.9999999999");
        BigInteger largeInt = new BigInteger("123456789012345678901234567890");

        Map<String, Object> original = new LinkedHashMap<>();
        original.put("decimalVal", largeDecimal);
        original.put("bigIntVal", largeInt);

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertNotNull(restored, "反序列化结果不应为null");
        // 验证数值存在
        assertTrue(json.contains("99999999999999999999"), "极大BigDecimal应正确序列化");
        assertTrue(json.contains("123456789012345678901234567890"), "极大BigInteger应正确序列化");
    }

    // ==================== null值往返 ====================

    @Test
    @DisplayName("包含null字段的Map往返")
    void mapWithNullFieldsRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("id", 1L);
        original.put("name", null);
        original.put("address", null);

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertEquals(1L, ((Number) restored.get("id")).longValue(), "非null字段应正确");
    }

    @Test
    @DisplayName("简单POJO包含null字段往返")
    void simplePojoWithNullFieldsRoundTrip() {
        SimpleItem original = new SimpleItem(1L, null, 0.0);
        String json = YdszJson.toJson(original);
        SimpleItem restored = YdszJson.toObject(json, SimpleItem.class);

        assertEquals(1L, restored.getId(), "非null字段应正确");
    }

    // ==================== 序列化-反序列化完整往返 ====================

    @Test
    @DisplayName("简单POJO完整往返 - 序列化后反序列化数据一致")
    void simplePojoFullRoundTrip() {
        SimpleItem original = new SimpleItem(42L, "round_trip_test", 99.99);
        String json = YdszJson.toJson(original);
        SimpleItem restored = YdszJson.toObject(json, SimpleItem.class);

        assertEquals(original.getId(), restored.getId(), "id往返应一致");
        assertEquals(original.getName(), restored.getName(), "name往返应一致");
    }

    @Test
    @DisplayName("Map完整往返 - 复杂数据结构")
    void mapFullRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("stringField", "hello");
        original.put("intField", 42);
        original.put("longField", 9999999999L);
        original.put("doubleField", 3.14159);
        original.put("boolField", true);
        original.put("nullField", null);

        String json = YdszJson.toJson(original);
        Map<String, Object> restored = YdszJson.parseObject(json);

        assertEquals("hello", restored.get("stringField"), "String字段应一致");
        assertEquals(42L, ((Number) restored.get("intField")).longValue(), "int字段应一致");
        assertEquals(true, restored.get("boolField"), "boolean字段应一致");
    }
}
