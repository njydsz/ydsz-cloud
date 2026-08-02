package com.njydsz.common.json;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.config.JsonConfig;
import com.njydsz.common.json.exception.JsonException;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonMapper 实例化 + 边界值 + 工具 API 测试。
 */
@DisplayName("JsonMapper 与边界值测试")
class YdszJsonMapperAndBoundaryTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    // ==================== JsonMapper 实例化测试 ====================

    @Nested
    @DisplayName("JsonMapper 实例化与 Builder")
    class JsonMapperInstanceTests {

        @Test
        @DisplayName("默认 Mapper 实例可正常序列化")
        void defaultMapperWorks() {
            JsonMapper mapper = new JsonMapper();
            TestBean bean = new TestBean();
            bean.setId(1);
            bean.setName("db");

            String json = mapper.toJson(bean);
            assertTrue(json.contains("\"id\":1"));
        }

        @Test
        @DisplayName("Builder 构建自定义 Mapper")
        void builderCreatesCustomMapper() {
            JsonMapper mapper = JsonMapper.builder()
                .dateFormat("yyyy-MM-dd")
                .maxJsonSize(5 * 1024 * 1024)
                .maxDepth(128)
                .build();

            TestBean bean = new TestBean();
            bean.setId(99);
            bean.setName("builder");

            String json = mapper.toJson(bean);
            assertTrue(json.contains("99"));
        }

        @Test
        @DisplayName("copy() 创建独立实例")
        void copyCreatesIndependentInstance() {
            JsonMapper mapper = new JsonMapper();
            JsonMapper copy = mapper.copy();

            assertNotNull(copy);
            assertNotSame(mapper, copy);
        }

        @Test
        @DisplayName("readerFor / writerFor 创建绑定型读写器")
        void readerForWriterFor() {
            JsonMapper mapper = new JsonMapper();
            JsonReader<TestBean> reader = mapper.readerFor(TestBean.class);
            JsonWriter<TestBean> writer = mapper.writerFor(TestBean.class);

            assertNotNull(reader);
            assertNotNull(writer);
        }

        @Test
        @DisplayName("convertValue 类型转换")
        void convertValue() {
            JsonMapper mapper = new JsonMapper();

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", 42);
            map.put("name", "converted");

            TestBean bean = mapper.convertValue(map, TestBean.class);
            assertNotNull(bean);
            assertEquals(42, bean.getId());
            assertEquals("converted", bean.getName());
        }

        @Test
        @DisplayName("getDefault() 返回默认单例")
        void getDefaultSingleton() {
            JsonMapper d1 = JsonMapper.getDefault();
            JsonMapper d2 = JsonMapper.getDefault();
            assertSame(d1, d2, "getDefault() 应返回同一实例");
        }
    }

    // ==================== 字节数组与流测试 ====================

    @Nested
    @DisplayName("字节数组与流序列化")
    class ByteAndStreamTests {

        @Test
        @DisplayName("toJsonBytes 往返一致")
        void toJsonBytesRoundTrip() {
            TestBean bean = new TestBean();
            bean.setId(42);
            bean.setName("bytes");

            byte[] bytes = YdszJson.toJsonBytes(bean);
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);

            TestBean restored = YdszJson.fromJsonBytes(bytes, TestBean.class);
            assertEquals(42, restored.getId());
            assertEquals("bytes", restored.getName());
        }

        @Test
        @DisplayName("toJsonBytes null → [n,u,l,l]")
        void toJsonBytesNull() {
            byte[] bytes = YdszJson.toJsonBytes(null);
            assertArrayEquals(new byte[]{'n', 'u', 'l', 'l'}, bytes);
        }
    }

    // ==================== 边界值与异常测试 ====================

    @Nested
    @DisplayName("边界值与异常处理")
    class BoundaryTests {

        @Test
        @DisplayName("空字符串反序列化返回 null")
        void emptyStringReturnsNull() {
            assertNull(YdszJson.toObject("", TestBean.class));
            assertNull(YdszJson.toObject("   ", TestBean.class));
        }

        @Test
        @DisplayName("null JSON 反序列化返回 null")
        void nullJsonReturnsNull() {
            assertNull(YdszJson.toObject(null, TestBean.class));
            assertNull(YdszJson.parseMap(null));
            assertNull(YdszJson.parseArray(null, String.class));
        }

        @Test
        @DisplayName("合法 JSON 验证通过")
        void validJsonCheck() {
            assertTrue(YdszJson.isValid("{\"a\":1}"));
            assertTrue(YdszJson.isValid("[1,2,3]"));
            assertTrue(YdszJson.isValid("\"hello\""));
            assertTrue(YdszJson.isValid("42"));
            assertTrue(YdszJson.isValid("true"));
        }

        @Test
        @DisplayName("非法 JSON 验证不通过")
        void invalidJsonCheck() {
            assertFalse(YdszJson.isValid("{invalid}"));
            assertFalse(YdszJson.isValid("[1,2,]]"));
            assertFalse(YdszJson.isValid(null));
            assertFalse(YdszJson.isValid(""));
        }

        @Test
        @DisplayName("超大 JSON 被拒绝")
        void exceedsMaxSize() {
            JsonConfig config = JsonConfig.builder()
                .maxJsonSize(100)
                .build();
            config.apply();

            StringBuilder sb = new StringBuilder(200);
            sb.append("{\"key\":\"");
            for (int i = 0; i < 180; i++) sb.append('x');
            sb.append("\"}");

            assertThrows(JsonException.class, () ->
                YdszJson.toObject(sb.toString(), TestBean.class));
        }

        @Test
        @DisplayName("带默认值的容错反序列化")
        void fallbackDefaultValue() {
            TestBean defaultValue = new TestBean();
            defaultValue.setId(-1);
            defaultValue.setName("fallback");

            TestBean result = YdszJson.toObject("{invalid}", TestBean.class, defaultValue);
            assertSame(defaultValue, result, "解析失败应返回默认值");
        }
    }

    // ==================== Merge / Diff 测试 ====================

    @Nested
    @DisplayName("JSON Merge / Diff")
    class MergeDiffTests {

        @Test
        @DisplayName("merge 覆盖字段")
        void mergeOverwritesFields() {
            String target = "{\"a\":1,\"b\":2}";
            String patch = "{\"b\":3,\"c\":4}";
            String merged = YdszJson.merge(target, patch);

            Map<String, Object> result = YdszJson.parseMap(merged);
            assertEquals(1, ((Number) result.get("a")).intValue());
            assertEquals(3, ((Number) result.get("b")).intValue());
            assertEquals(4, ((Number) result.get("c")).intValue());
        }

        @Test
        @DisplayName("diff 计算差异")
        void diffComputesDifference() {
            String source = "{\"a\":1,\"b\":2}";
            String target = "{\"a\":1,\"b\":3}";
            String diff = YdszJson.diff(source, target);

            assertNotNull(diff);
            assertTrue(diff.contains("b"));
        }
    }

    // ==================== ScopedContext 测试 ====================

    @Nested
    @DisplayName("ScopedContext try-with-resources")
    class ScopedContextTests {

        @Test
        @DisplayName("ScopedContext 自动清理 ThreadLocal")
        void scopedContextAutoCleanup() throws Exception {
            // 验证 try-with-resources 不会抛出异常
            try (var ctx = YdszJson.scopedContext()) {
                TestBean bean = new TestBean();
                bean.setName("ctx");
                String json = YdszJson.toJson(bean);
                assertTrue(json.contains("ctx"));
            }
            // 上下文关闭后仍可正常使用（下次调用会重建 SerializationContext）
            TestBean bean = new TestBean();
            bean.setName("after");
            assertTrue(YdszJson.toJson(bean).contains("after"));
        }

        @Test
        @DisplayName("cleanup() 手动清理")
        void manualCleanup() {
            TestBean bean = new TestBean();
            bean.setName("manual");
            String json = YdszJson.toJson(bean);
            assertNotNull(json);

            // 手动清理不应抛异常
            assertDoesNotThrow(YdszJson::cleanup);

            // 清理后可继续使用
            assertDoesNotThrow(() -> YdszJson.toJson(bean));
        }
    }

    // ==================== 构建器 API 测试 ====================

    @Nested
    @DisplayName("Builder 链式构建")
    class BuilderAPITests {

        @Test
        @DisplayName("YdszJson.object() 创建空 JSON 对象")
        void createEmptyObject() {
            var obj = YdszJson.object();
            assertNotNull(obj);
            assertEquals("{}", obj.toString());
        }

        @Test
        @DisplayName("YdszJson.array() 创建空 JSON 数组")
        void createEmptyArray() {
            var arr = YdszJson.array();
            assertNotNull(arr);
            assertEquals("[]", arr.toString());
        }
    }
}
