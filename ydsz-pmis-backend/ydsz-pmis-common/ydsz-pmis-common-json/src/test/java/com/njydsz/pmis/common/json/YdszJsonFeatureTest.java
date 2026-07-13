package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import com.njydsz.pmis.common.json.reader.JSONReader;
import com.njydsz.pmis.common.json.writer.JSONWriter;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YdszJson Feature 系统测试")
class YdszJsonFeatureTest {

    // ==================== JSONWriter.Feature.WriteNulls ====================

    @Nested
    @DisplayName("JSONWriter.Feature.WriteNulls 测试")
    class WriteNullsTests {

        @YdszJsonClass
        static class UserWithNull {
            private String name = "John";
            private String email = null;

            public String getName() { return name; }
            public String getEmail() { return email; }
        }

        @Test
        @DisplayName("默认不输出 null 值")
        void defaultNoNulls() {
            UserWithNull user = new UserWithNull();
            String json = YdszJson.toJson(user);
            assertTrue(json.contains("John"));
            assertFalse(json.contains("email"));
        }

        @Test
        @DisplayName("WriteNulls 输出 null 值")
        void writeNullsFeature() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "John");
            data.put("email", null);
            String json = YdszJson.toJson(data, JSONWriter.Feature.WriteNulls);
            assertTrue(json.contains("John"));
            assertTrue(json.contains("email"));
            assertTrue(json.contains("null"));
        }

        @Test
        @DisplayName("Feature.mask 计算正确")
        void featureMaskCalculation() {
            long mask = JSONWriter.Feature.WriteNulls.mask();
            assertTrue(JSONWriter.Feature.WriteNulls.isEnabled(mask));
            assertFalse(JSONWriter.Feature.PrettyPrint.isEnabled(mask));
        }
    }

    // ==================== JSONWriter.Feature.PrettyPrint ====================

    @Nested
    @DisplayName("JSONWriter.Feature.PrettyPrint 测试")
    class PrettyPrintTests {

        @Test
        @DisplayName("PrettyPrint 格式化输出")
        void prettyPrintFeature() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "John");
            data.put("age", 30);
            String json = YdszJson.toJson(data, JSONWriter.Feature.PrettyPrint);
            assertTrue(json.contains("\n") || json.contains("  "));
        }

        @Test
        @DisplayName("PrettyPrint 与 WriteNulls 组合使用")
        void prettyPrintWithWriteNulls() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "John");
            data.put("email", null);
            String json = YdszJson.toJson(data, JSONWriter.Feature.PrettyPrint, JSONWriter.Feature.WriteNulls);
            assertTrue(json.contains("null"));
            assertTrue(json.contains("\n") || json.contains("  "));
        }
    }

    // ==================== JSONReader.Feature.UseBigDecimalForNumbers ====================

    @Nested
    @DisplayName("JSONReader.Feature.UseBigDecimalForNumbers 测试")
    class UseBigDecimalForNumbersTests {

        @Test
        @DisplayName("Feature 默认不启用")
        void useBigDecimalDefaultDisabled() {
            assertFalse(JSONReader.Feature.UseBigDecimalForNumbers.isEnabledByDefault());
        }

        @Test
        @DisplayName("Feature mask 计算正确")
        void featureMaskCalculation() {
            long mask = JSONReader.Feature.UseBigDecimalForNumbers.mask();
            assertTrue(JSONReader.Feature.UseBigDecimalForNumbers.isEnabled(mask));
        }
    }

    // ==================== JSONReader.Feature.LimitDepth ====================

    @Nested
    @DisplayName("JSONReader.Feature.LimitDepth 测试")
    class LimitDepthTests {

        @Test
        @DisplayName("LimitDepth 默认启用")
        void limitDepthEnabledByDefault() {
            assertTrue(JSONReader.Feature.LimitDepth.isEnabledByDefault());
        }

        @Test
        @DisplayName("LimitDepth mask 计算正确")
        void limitDepthMaskCalculation() {
            long mask = JSONReader.Feature.LimitDepth.mask();
            assertTrue(JSONReader.Feature.LimitDepth.isEnabled(mask));
        }

        @Test
        @DisplayName("使用 Feature 反序列化")
        void deserializeWithFeature() {
            String json = "{\"name\":\"John\",\"age\":30}";
            Map<String, Object> result = YdszJson.toObject(json, Map.class, JSONReader.Feature.LimitDepth);
            assertNotNull(result);
            assertEquals("John", result.get("name"));
        }
    }

    // ==================== Feature of() 计算 ====================

    @Nested
    @DisplayName("Feature of() 计算测试")
    class FeatureOfTests {

        @Test
        @DisplayName("JSONWriter.of(null) 返回 0")
        void writerOfNull() {
            assertEquals(0, JSONWriter.of((JSONWriter.Feature[]) null));
        }

        @Test
        @DisplayName("JSONWriter.of(Set) 计算正确")
        void writerOfSet() {
            Set<JSONWriter.Feature> features = EnumSet.of(JSONWriter.Feature.WriteNulls, JSONWriter.Feature.PrettyPrint);
            long value = JSONWriter.of(features);
            assertTrue(JSONWriter.Feature.WriteNulls.isEnabled(value));
            assertTrue(JSONWriter.Feature.PrettyPrint.isEnabled(value));
            assertFalse(JSONWriter.Feature.EscapeNonAscii.isEnabled(value));
        }

        @Test
        @DisplayName("JSONReader.of(null) 返回 0")
        void readerOfNull() {
            assertEquals(0, JSONReader.of((JSONReader.Feature[]) null));
        }

        @Test
        @DisplayName("JSONReader.of(Set) 计算正确")
        void readerOfSet() {
            Set<JSONReader.Feature> features = EnumSet.of(JSONReader.Feature.UseBigDecimalForNumbers, JSONReader.Feature.LimitDepth);
            long value = JSONReader.of(features);
            assertTrue(JSONReader.Feature.UseBigDecimalForNumbers.isEnabled(value));
            assertTrue(JSONReader.Feature.LimitDepth.isEnabled(value));
        }
    }

    // ==================== Feature 枚举完整性 ====================

    @Nested
    @DisplayName("Feature 枚举完整性测试")
    class FeatureEnumTests {

        @Test
        @DisplayName("JSONWriter.Feature 包含所有预期特性")
        void writerFeaturesComplete() {
            assertNotNull(JSONWriter.Feature.WriteNulls);
            assertNotNull(JSONWriter.Feature.PrettyPrint);
            assertNotNull(JSONWriter.Feature.UseISO8601DateFormat);
            assertNotNull(JSONWriter.Feature.EscapeNonAscii);
            assertNotNull(JSONWriter.Feature.DisableCircularReferenceDetect);
            assertNotNull(JSONWriter.Feature.WriteMapTypeName);
            assertNotNull(JSONWriter.Feature.UseSingleQuotes);
            assertNotNull(JSONWriter.Feature.SortMapKeys);
            assertNotNull(JSONWriter.Feature.WriteClassName);
        }

        @Test
        @DisplayName("JSONReader.Feature 包含所有预期特性")
        void readerFeaturesComplete() {
            assertNotNull(JSONReader.Feature.SupportSingleQuotes);
            assertNotNull(JSONReader.Feature.IgnoreControlChars);
            assertNotNull(JSONReader.Feature.AllowComment);
            assertNotNull(JSONReader.Feature.AllowTrailingComma);
            assertNotNull(JSONReader.Feature.UseBigDecimalForNumbers);
            assertNotNull(JSONReader.Feature.AutoCloseJson);
            assertNotNull(JSONReader.Feature.LimitDepth);
            assertNotNull(JSONReader.Feature.IgnoreUnknownFields);
            assertNotNull(JSONReader.Feature.LimitStringLength);
            assertNotNull(JSONReader.Feature.LimitObjectSize);
            assertNotNull(JSONReader.Feature.LimitArraySize);
            assertNotNull(JSONReader.Feature.SafeMode);
            assertNotNull(JSONReader.Feature.StrictMode);
        }

        @Test
        @DisplayName("每个 Feature 的 mask 不为 0")
        void eachFeatureMaskNonZero() {
            for (JSONWriter.Feature f : JSONWriter.Feature.values()) {
                assertTrue(f.mask() != 0, f.name() + " mask should not be 0");
            }
            for (JSONReader.Feature f : JSONReader.Feature.values()) {
                assertTrue(f.mask() != 0, f.name() + " mask should not be 0");
            }
        }
    }

    // ==================== JSONWriter 直接操作 ====================

    @Nested
    @DisplayName("JSONWriter 直接操作测试")
    class JSONWriterDirectTests {

        @Test
        @DisplayName("JSONWriter 构造和基本写入")
        void jsonWriterBasicWrite() {
            JSONWriter writer = new JSONWriter();
            writer.write('{');
            writer.writeStringDirect("name");
            writer.write(':');
            writer.writeStringDirect("John");
            writer.write('}');
            assertEquals("{\"name\":\"John\"}", writer.toString());
        }

        @Test
        @DisplayName("JSONWriter writeInt")
        void jsonWriterWriteInt() {
            JSONWriter writer = new JSONWriter();
            writer.writeInt(42);
            assertEquals("42", writer.toString());
        }

        @Test
        @DisplayName("JSONWriter writeLong")
        void jsonWriterWriteLong() {
            JSONWriter writer = new JSONWriter();
            writer.writeLong(9999999999L);
            assertTrue(writer.toString().contains("9999999999"));
        }

        @Test
        @DisplayName("JSONWriter reset")
        void jsonWriterReset() {
            JSONWriter writer = new JSONWriter();
            writer.write("hello");
            writer.reset();
            assertEquals(0, writer.size());
            assertEquals("", writer.toString());
        }

        @Test
        @DisplayName("JSONWriter capacity")
        void jsonWriterCapacity() {
            JSONWriter writer = new JSONWriter(1024);
            assertTrue(writer.capacity() >= 1024);
        }

        @Test
        @DisplayName("JSONWriter needsEscape 检查")
        void jsonWriterNeedsEscape() {
            assertFalse(JSONWriter.needsEscape("hello"));
            assertTrue(JSONWriter.needsEscape("hello\"world"));
            assertTrue(JSONWriter.needsEscape("hello\nworld"));
            assertTrue(JSONWriter.needsEscape("hello\\world"));
        }
    }

    // ==================== JSONReader 直接操作 ====================

    @Nested
    @DisplayName("JSONReader 直接操作测试")
    class JSONReaderDirectTests {

        @Test
        @DisplayName("JSONReader 构造和基本读取")
        void jsonReaderBasicRead() {
            JSONReader reader = new JSONReader("{\"name\":\"John\"}");
            reader.skipWhitespace();
            assertEquals('{', reader.nextChar());
        }

        @Test
        @DisplayName("JSONReader readInt")
        void jsonReaderReadInt() {
            JSONReader reader = new JSONReader("42");
            assertEquals(42, reader.readInt());
        }

        @Test
        @DisplayName("JSONReader readLong")
        void jsonReaderReadLong() {
            JSONReader reader = new JSONReader("9999999999");
            assertEquals(9999999999L, reader.readLong());
        }

        @Test
        @DisplayName("JSONReader readDouble")
        void jsonReaderReadDouble() {
            JSONReader reader = new JSONReader("3.14");
            assertEquals(3.14, reader.readDouble(), 0.001);
        }

        @Test
        @DisplayName("JSONReader readBoolean")
        void jsonReaderReadBoolean() {
            JSONReader reader = new JSONReader("true");
            assertTrue(reader.readBoolean());

            JSONReader reader2 = new JSONReader("false");
            assertFalse(reader2.readBoolean());
        }

        @Test
        @DisplayName("JSONReader readString")
        void jsonReaderReadString() {
            JSONReader reader = new JSONReader("\"hello\"");
            assertEquals("hello", reader.readString());
        }

        @Test
        @DisplayName("JSONReader isNull")
        void jsonReaderIsNull() {
            JSONReader reader = new JSONReader("null");
            assertTrue(reader.isNull());
        }

        @Test
        @DisplayName("JSONReader isEnd")
        void jsonReaderIsEnd() {
            JSONReader reader = new JSONReader("42");
            reader.readInt();
            assertTrue(reader.isEnd());
        }

        @Test
        @DisplayName("JSONReader reset 复用")
        void jsonReaderReset() {
            JSONReader reader = new JSONReader("42");
            reader.readInt();
            reader.reset("100");
            assertEquals(100, reader.readInt());
        }

        @Test
        @DisplayName("JSONReader readObjectMap")
        void jsonReaderReadObjectMap() {
            JSONReader reader = new JSONReader("{\"name\":\"John\",\"age\":30}");
            Map<String, Object> map = reader.readObjectMap();
            assertEquals("John", map.get("name"));
            assertEquals(30L, ((Number) map.get("age")).longValue());
        }

        @Test
        @DisplayName("JSONReader fnv1aHash")
        void jsonReaderFnv1aHash() {
            long hash1 = JSONReader.fnv1aHash("name");
            long hash2 = JSONReader.fnv1aHash("name");
            assertEquals(hash1, hash2);
            assertNotEquals(hash1, JSONReader.fnv1aHash("age"));
        }
    }
}
