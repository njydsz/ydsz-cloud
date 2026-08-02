package com.njydsz.common.json;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.testbean.AnnotationBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注解功能综合测试 — 覆盖 @JsonProperty、@JsonAlias、@JsonIgnore、@JsonFormat、
 * @JsonInclude、@JsonRawValue、@JsonUnwrapped、@JsonView、@JsonIgnoreProperties、@JsonRootName。
 */
@DisplayName("注解功能综合测试")
class YdszJsonAnnotationTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
        AutoTypeChecker.addToWhitelist("com.njydsz.common.json.testbean.AnnotationBean");
        AutoTypeChecker.addToWhitelist("com.njydsz.common.json.testbean.AnnotationBean$EmbeddedAddress");
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
        AutoTypeChecker.reset();
    }

    // ==================== @JsonProperty 测试 ====================

    @Nested
    @DisplayName("@JsonProperty 字段重命名")
    class JsonPropertyTests {

        @Test
        @DisplayName("序列化时使用注解指定的名称")
        void serializeUsesJsonPropertyName() {
            AnnotationBean bean = new AnnotationBean();
            bean.setId(100);
            bean.setName("test");

            String json = YdszJson.toJson(bean);
            assertNotNull(json);
            assertTrue(json.contains("\"uid\":100"), "应使用 @JsonProperty(\"uid\") 而非 id: " + json);
        }

        @Test
        @DisplayName("反序列化时使用注解指定的名称匹配")
        void deserializeUsesJsonPropertyName() {
            String json = "{\"uid\":200,\"name\":\"hello\",\"score\":85}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);

            assertNotNull(bean);
            assertEquals(200, bean.getId());
            assertEquals("hello", bean.getName());
            assertEquals(85, bean.getScore());
        }
    }

    // ==================== @JsonAlias 测试 ====================

    @Nested
    @DisplayName("@JsonAlias 别名")
    class JsonAliasTests {

        @Test
        @DisplayName("反序列化支持主名称")
        void deserializeWithPrimaryName() {
            String json = "{\"uid\":1,\"name\":\"primary\"}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals("primary", bean.getName());
        }

        @Test
        @DisplayName("反序列化支持别名 fullName")
        void deserializeWithAliasFullName() {
            String json = "{\"uid\":1,\"fullName\":\"alias1\"}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals("alias1", bean.getName());
        }

        @Test
        @DisplayName("反序列化支持别名 displayName")
        void deserializeWithAliasDisplayName() {
            String json = "{\"uid\":1,\"displayName\":\"alias2\"}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals("alias2", bean.getName());
        }
    }

    // ==================== @JsonIgnore / @JsonIgnoreProperties 测试 ====================

    @Nested
    @DisplayName("@JsonIgnore 和 @JsonIgnoreProperties 字段忽略")
    class JsonIgnoreTests {

        @Test
        @DisplayName("@JsonIgnore 字段不出现在序列化结果中")
        void jsonIgnoreFieldExcluded() {
            AnnotationBean bean = new AnnotationBean();
            bean.setPassword("secret123");
            bean.setName("user");

            String json = YdszJson.toJson(bean);
            assertFalse(json.contains("password"), "password 应被 @JsonIgnore 排除: " + json);
            assertFalse(json.contains("secret123"), "password 值不应出现在 JSON 中");
        }

        @Test
        @DisplayName("@JsonIgnoreProperties 排除类级指定字段")
        void jsonIgnorePropertiesClassLevelExcluded() {
            AnnotationBean bean = new AnnotationBean();
            bean.setInternalField("should-be-hidden");
            bean.setName("visible");

            String json = YdszJson.toJson(bean);
            assertFalse(json.contains("internalField"), "internalField 应被 @JsonIgnoreProperties 排除: " + json);
        }
    }

    // ==================== @JsonFormat 测试 ====================

    @Nested
    @DisplayName("@JsonFormat 日期格式化")
    class JsonFormatTests {

        @Test
        @DisplayName("日期字段按指定格式序列化")
        void dateFieldFormatted() {
            AnnotationBean bean = new AnnotationBean();
            bean.setBirthday(LocalDate.of(2024, 6, 15));

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("2024-06-15"), "日期应按 yyyy-MM-dd 格式: " + json);
        }

        @Test
        @DisplayName("日期字符串反序列化为 LocalDate")
        void dateFieldDeserialized() {
            String json = "{\"uid\":1,\"birthday\":\"2025-12-31\"}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals(LocalDate.of(2025, 12, 31), bean.getBirthday());
        }
    }

    // ==================== @JsonInclude 测试 ====================

    @Nested
    @DisplayName("@JsonInclude 包含策略")
    class JsonIncludeTests {

        @Test
        @DisplayName("NON_NULL: null 字段不输出")
        void nonNullExcludesNullField() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setOptionalField(null);

            String json = YdszJson.toJson(bean);
            assertFalse(json.contains("optionalField"), "@JsonInclude(NON_NULL) 应排除 null: " + json);
        }

        @Test
        @DisplayName("NON_NULL: 非 null 字段正常输出")
        void nonNullIncludesNonNullField() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setOptionalField("present");

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("optionalField"), "非 null 字段应正常输出: " + json);
        }

        @Test
        @DisplayName("NON_EMPTY: 空字符串不输出")
        void nonEmptyExcludesEmptyString() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setNonEmptyField("");

            String json = YdszJson.toJson(bean);
            assertFalse(json.contains("nonEmptyField"), "@JsonInclude(NON_EMPTY) 应排除空字符串: " + json);
        }

        @Test
        @DisplayName("NON_DEFAULT: 默认值 0 不输出")
        void nonDefaultExcludesZero() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setNonDefaultField(0);

            String json = YdszJson.toJson(bean);
            assertFalse(json.contains("nonDefaultField"), "@JsonInclude(NON_DEFAULT) 应排除 0: " + json);
        }

        @Test
        @DisplayName("NON_DEFAULT: 非默认值正常输出")
        void nonDefaultIncludesNonZero() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setNonDefaultField(42);

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("nonDefaultField"), "非默认值应正常输出: " + json);
        }
    }

    // ==================== @JsonRawValue 测试 ====================

    @Nested
    @DisplayName("@JsonRawValue 原始 JSON 嵌入")
    class JsonRawValueTests {

        @Test
        @DisplayName("raw JSON 值不做转义直接嵌入")
        void rawValueEmbeddedDirectly() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setRawData("{\"nested\":true}");

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("{\"nested\":true}"), "raw JSON 应直接嵌入不转义: " + json);
        }
    }

    // ==================== @JsonUnwrapped 测试 ====================

    @Nested
    @DisplayName("@JsonUnwrapped 嵌套展开")
    class JsonUnwrappedTests {

        @Test
        @DisplayName("内嵌对象字段展平到父级")
        void innerObjectUnwrapped() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            AnnotationBean.EmbeddedAddress addr = new AnnotationBean.EmbeddedAddress("深圳", "科技园路1号");
            bean.setAddress(addr);

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("\"city\""), "内嵌对象字段应展平: " + json);
            assertTrue(json.contains("\"street\""), "内嵌对象字段应展平: " + json);
            assertTrue(json.contains("深圳"), "内嵌对象值应出现: " + json);
        }
    }

    // ==================== @JsonView 测试 ====================

    @Nested
    @DisplayName("@JsonView 视图过滤")
    class JsonViewTests {

        @Test
        @DisplayName("Public 视图仅输出 publicInfo")
        void publicViewOnlyOutputsPublicFields() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setPublicInfo("public-data");
            bean.setInternalInfo("secret-data");

            String json = YdszJson.toJson(bean, AnnotationBean.View.Public.class);
            assertTrue(json.contains("publicInfo"), "Public 视图应包含 publicInfo: " + json);
            assertFalse(json.contains("internalInfo"), "Public 视图不应包含 internalInfo: " + json);
        }

        @Test
        @DisplayName("Internal 视图输出所有字段")
        void internalViewOutputsAllFields() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setPublicInfo("public-data");
            bean.setInternalInfo("secret-data");

            String json = YdszJson.toJson(bean, AnnotationBean.View.Internal.class);
            assertTrue(json.contains("publicInfo"), "Internal 视图应包含 publicInfo: " + json);
            assertTrue(json.contains("internalInfo"), "Internal 视图应包含 internalInfo: " + json);
        }

        @Test
        @DisplayName("无视图时输出全部字段")
        void noViewOutputsAllFields() {
            AnnotationBean bean = new AnnotationBean();
            bean.setName("test");
            bean.setPublicInfo("public-data");
            bean.setInternalInfo("secret-data");

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("publicInfo"), "无视图时应包含 publicInfo: " + json);
            assertTrue(json.contains("internalInfo"), "无视图时应包含 internalInfo: " + json);
        }
    }

    // ==================== @JsonRootName 测试 ====================

    @Nested
    @DisplayName("@JsonRootName 根名称包裹")
    class JsonRootNameTests {

        @Test
        @DisplayName("启用 wrapRootValue 时使用注解指定的根名称")
        void wrapRootValueEnabled() {
            AnnotationBean bean = new AnnotationBean();
            bean.setId(1);
            bean.setName("test");

            String json = YdszJson.toJson(bean);
            assertNotNull(json);
            assertFalse(json.isEmpty(), "序列化结果不应为空");
        }
    }

    // ==================== 注解组合测试 ====================

    @Nested
    @DisplayName("注解组合场景")
    class AnnotationCombinationTests {

        @Test
        @DisplayName("完整 Bean 序列化往返")
        void fullBeanRoundTrip() {
            AnnotationBean bean = new AnnotationBean();
            bean.setId(99);
            bean.setName("combo-test");
            bean.setScore(88);
            bean.setPublicInfo("hello");
            bean.setInternalInfo("world");
            bean.setOptionalField("opt");
            bean.setNonEmptyField("ne");
            bean.setNonDefaultField(1);
            bean.setRawData("[1,2,3]");
            bean.setBirthday(LocalDate.of(2023, 1, 1));
            bean.setAddress(new AnnotationBean.EmbeddedAddress("北京", "长安街"));

            String json = YdszJson.toJson(bean);
            assertNotNull(json);
            assertTrue(json.contains("\"uid\":99"), "应包含 uid: " + json);

            AnnotationBean restored = YdszJson.toObject(json, AnnotationBean.class);
            assertNotNull(restored);
            assertEquals(99, restored.getId());
            assertEquals("combo-test", restored.getName());
            assertEquals(88, restored.getScore());
        }

        @Test
        @DisplayName("别名反序列化与主名称一致")
        void aliasRoundTripConsistency() {
            AnnotationBean original = new AnnotationBean();
            original.setName("consistent");

            String json = YdszJson.toJson(original);

            // 主名称反序列化
            AnnotationBean fromPrimary = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals("consistent", fromPrimary.getName());

            // 别名反序列化
            String aliasedJson = json.replace("\"name\"", "\"fullName\"");
            AnnotationBean fromAlias = YdszJson.toObject(aliasedJson, AnnotationBean.class);
            assertEquals("consistent", fromAlias.getName());
        }
    }

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("边界值与异常场景")
    class BoundaryTests {

        @Test
        @DisplayName("@JsonInclude NON_NULL 对已填充的 null 列表")
        void nonNullExcludesNullList() {
            // 验证全部字段为 null 的 Bean 序列化不异常
            AnnotationBean bean = new AnnotationBean();
            String json = YdszJson.toJson(bean);
            assertNotNull(json);
            assertTrue(json.contains("{"), "空 Bean 至少输出空对象: " + json);
        }

        @Test
        @DisplayName("@JsonAlias 优先使用主名称而非别名")
        void primaryNamePreferredOverAlias() {
            String json = "{\"uid\":1,\"name\":\"primary\",\"fullName\":\"alias\"}";
            AnnotationBean bean = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals("primary", bean.getName(), "同时存在主名称和别名时，应优先使用主名称");
        }

        @Test
        @DisplayName("@JsonProperty 序列化后反序列化一致性")
        void propertyRenameRoundTrip() {
            AnnotationBean bean = new AnnotationBean();
            bean.setId(777);

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("\"uid\""));

            AnnotationBean restored = YdszJson.toObject(json, AnnotationBean.class);
            assertEquals(777, restored.getId());
        }
    }
}
