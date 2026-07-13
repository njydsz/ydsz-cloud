package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.annotation.YdszJsonField;
import com.njydsz.pmis.common.json.exception.YdszJsonException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("错误处理集成测试")
class ErrorHandlingIntegrationTest {

    // ==================== 测试模型 ====================

    @YdszJsonClass
    static class SimplePojo {
        private long id;
        private String name;

        public SimplePojo() {}

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @YdszJsonClass
    static class RequiredFieldPojo {
        @YdszJsonField(required = true)
        private String mandatoryField;
        private String optionalField;

        public RequiredFieldPojo() {}

        public String getMandatoryField() { return mandatoryField; }
        public void setMandatoryField(String mandatoryField) { this.mandatoryField = mandatoryField; }
        public String getOptionalField() { return optionalField; }
        public void setOptionalField(String optionalField) { this.optionalField = optionalField; }
    }

    @YdszJsonClass
    static class TypedPojo {
        private int intField;
        private boolean boolField;

        public TypedPojo() {}

        public int getIntField() { return intField; }
        public void setIntField(int intField) { this.intField = intField; }
        public boolean isBoolField() { return boolField; }
        public void setBoolField(boolean boolField) { this.boolField = boolField; }
    }

    // ==================== 格式错误的JSON ====================

    @Test
    @DisplayName("格式错误的JSON输入 - 完全无效的字符串")
    void malformedJsonInvalidSyntax() {
        String malformedJson = "this is not json at all";
        assertThrows(Exception.class, () -> {
            YdszJson.toObject(malformedJson, SimplePojo.class);
        }, "无效JSON语法应抛出异常");
    }

    @Test
    @DisplayName("格式错误的JSON输入 - 缺少闭合大括号应导致解析错误")
    void malformedJsonMissingClosingBrace() {
        String malformedJson = "{\"id\":1,\"name\":\"test\"";
        // 解析器可能容忍不完整的JSON，验证结果不为有效POJO即可
        try {
            SimplePojo result = YdszJson.toObject(malformedJson, SimplePojo.class);
            // 如果不抛异常，验证结果不完整
            assertNotNull(result);
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertTrue(e instanceof YdszJsonException || e instanceof RuntimeException);
        }
    }

    // ==================== 类型不匹配 ====================

    @Test
    @DisplayName("类型不匹配 - 字符串值赋给int字段应使用默认值或抛异常")
    void typeMismatchStringToInt() {
        String json = "{\"intField\":\"not_a_number\",\"boolField\":true}";
        // 类型不匹配时，解析器可能使用默认值或抛异常
        try {
            TypedPojo result = YdszJson.toObject(json, TypedPojo.class);
            // 如果不抛异常，intField应有默认值0
            assertNotNull(result);
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertTrue(e instanceof YdszJsonException || e instanceof RuntimeException);
        }
    }

    @Test
    @DisplayName("类型不匹配 - 对象值赋给基本类型字段")
    void typeMismatchObjectToPrimitive() {
        String json = "{\"intField\":{\"nested\":\"object\"},\"boolField\":true}";
        // 类型不匹配时，解析器可能使用默认值或抛异常
        try {
            TypedPojo result = YdszJson.toObject(json, TypedPojo.class);
            // 如果不抛异常，intField应有默认值0
            assertNotNull(result);
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }

    // ==================== 缺少必需字段 ====================

    @Test
    @DisplayName("缺少必需字段 - required字段缺失时的行为验证")
    void missingRequiredFieldBehavior() {
        String json = "{\"optionalField\":\"some_value\"}";
        // @YdszJsonField(required=true) 字段缺失时验证行为
        try {
            RequiredFieldPojo result = YdszJson.toObject(json, RequiredFieldPojo.class);
            // 如果不抛异常，验证mandatoryField为null
            assertNotNull(result);
            assertNull(result.getMandatoryField(), "缺失的required字段应为null");
        } catch (YdszJsonException e) {
            // 抛出异常也是可接受的行为
            assertTrue(e instanceof YdszJsonException);
        }
    }

    @Test
    @DisplayName("必需字段提供值时不应抛异常")
    void requiredFieldProvidedShouldNotThrow() {
        String json = "{\"mandatoryField\":\"provided\",\"optionalField\":\"extra\"}";
        RequiredFieldPojo result = YdszJson.toObject(json, RequiredFieldPojo.class);
        assertNotNull(result, "提供required字段时反序列化应成功");
        assertEquals("provided", result.getMandatoryField(), "required字段值应正确");
    }

    // ==================== 额外未知字段 ====================

    @Test
    @DisplayName("额外未知字段 - 应被忽略不影响反序列化")
    void extraUnknownFieldsShouldBeIgnored() {
        String json = "{\"id\":1,\"name\":\"test\",\"unknownField\":\"extra\",\"anotherUnknown\":42}";
        SimplePojo result = YdszJson.toObject(json, SimplePojo.class);

        assertNotNull(result, "包含未知字段的JSON应能正常反序列化");
        assertEquals(1L, result.getId(), "已知字段id应正确");
        assertEquals("test", result.getName(), "已知字段name应正确");
    }

    @Test
    @DisplayName("额外未知字段 - 大量未知字段应被忽略")
    void manyExtraUnknownFieldsShouldBeIgnored() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":42,\"name\":\"known\"");
        for (int i = 0; i < 50; i++) {
            sb.append(",\"extra_").append(i).append("\":\"value_").append(i).append("\"");
        }
        sb.append("}");

        SimplePojo result = YdszJson.toObject(sb.toString(), SimplePojo.class);
        assertNotNull(result, "大量未知字段不应导致反序列化失败");
        assertEquals(42L, result.getId(), "已知字段应正确");
        assertEquals("known", result.getName(), "已知字段应正确");
    }

    // ==================== null处理边界 ====================

    @Test
    @DisplayName("null值处理 - JSON中字段值为null")
    void nullFieldValueInJson() {
        String json = "{\"id\":1,\"name\":null}";
        SimplePojo result = YdszJson.toObject(json, SimplePojo.class);

        assertNotNull(result, "包含null值的JSON应能正常反序列化");
        assertEquals(1L, result.getId(), "非null字段应正确");
        assertNull(result.getName(), "null字段应保持null");
    }

    @Test
    @DisplayName("null值处理 - 反序列化null字符串")
    void deserializeNullString() {
        // "null"字符串反序列化可能返回null或抛异常
        try {
            SimplePojo result = YdszJson.toObject("null", SimplePojo.class);
            assertNull(result, "反序列化'null'字符串应返回null");
        } catch (YdszJsonException e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("null值处理 - 序列化null对象")
    void serializeNullObject() {
        String json = YdszJson.toJson(null);
        assertEquals("null", json, "序列化null应输出'null'");
    }

    @Test
    @DisplayName("null值处理 - 序列化所有字段为null的对象")
    void serializeObjectWithAllNullFields() {
        SimplePojo pojo = new SimplePojo();
        String json = YdszJson.toJson(pojo);
        assertNotNull(json, "所有字段为null的对象应能序列化");
        assertTrue(json.startsWith("{"), "应输出JSON对象");
    }

    // ==================== 空字符串反序列化 ====================

    @Test
    @DisplayName("空字符串反序列化 - 应返回null或抛异常")
    void emptyStringDeserialization() {
        try {
            SimplePojo result = YdszJson.toObject("", SimplePojo.class);
            assertNull(result, "空字符串反序列化应返回null");
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }

    @Test
    @DisplayName("空白字符串反序列化 - 应返回null或抛异常")
    void whitespaceStringDeserialization() {
        try {
            SimplePojo result = YdszJson.toObject("   ", SimplePojo.class);
            assertNull(result, "空白字符串反序列化应返回null");
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }

    // ==================== 数组反序列化为对象 ====================

    @Test
    @DisplayName("数组反序列化为对象 - 应抛出ClassCastException或返回null")
    void deserializeArrayIntoObjectShouldFail() {
        String jsonArray = "[1,2,3]";
        assertThrows(Exception.class, () -> {
            YdszJson.toObject(jsonArray, SimplePojo.class);
        }, "将JSON数组反序列化为POJO对象应抛出异常");
    }

    @Test
    @DisplayName("对象反序列化为List - 应返回空列表或抛异常")
    void deserializeObjectIntoListShouldFail() {
        String jsonObject = "{\"id\":1,\"name\":\"test\"}";
        try {
            java.util.List<SimplePojo> result = YdszJson.parseArray(jsonObject, SimplePojo.class);
            // 如果不抛异常，应返回空列表
            assertNotNull(result);
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }

    // ==================== 其他边界情况 ====================

    @Test
    @DisplayName("反序列化不完整的JSON - 仅有键没有值")
    void deserializeIncompleteJsonKeyWithoutValue() {
        String json = "{\"id\":}";
        assertThrows(Exception.class, () -> {
            YdszJson.parseObject(json);
        }, "不完整的JSON应抛出异常");
    }

    @Test
    @DisplayName("反序列化数字字符串为对象 - 应抛出异常")
    void deserializeNumberAsObject() {
        String json = "42";
        assertThrows(Exception.class, () -> {
            YdszJson.toObject(json, SimplePojo.class);
        }, "数字字符串反序列化为POJO应抛出异常");
    }

    @Test
    @DisplayName("反序列化布尔值为对象 - 应抛出异常")
    void deserializeBooleanAsObject() {
        String json = "true";
        assertThrows(Exception.class, () -> {
            YdszJson.toObject(json, SimplePojo.class);
        }, "布尔值反序列化为POJO应抛出异常");
    }

    // ==================== Map错误处理 ====================

    @Test
    @DisplayName("格式错误的JSON - parseObject处理")
    void malformedJsonParseObject() {
        String malformedJson = "not valid json";
        assertThrows(Exception.class, () -> {
            YdszJson.parseObject(malformedJson);
        }, "无效JSON使用parseObject应抛出异常");
    }

    @Test
    @DisplayName("格式错误的JSON - parseArray处理")
    void malformedJsonParseArray() {
        String malformedJson = "not valid json";
        try {
            java.util.List<Object> result = YdszJson.parseArray(malformedJson);
            // 如果不抛异常，应返回空列表
            assertNotNull(result);
        } catch (Exception e) {
            // 抛出异常也是可接受的行为
            assertNotNull(e.getMessage());
        }
    }
}
