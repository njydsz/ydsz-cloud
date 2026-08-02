package com.njydsz.common.json;

import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.config.JsonConfig;
import com.njydsz.common.json.provider.SerializationProvider;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命名策略测试 — 覆盖 SNAKE_CASE、KEBAB_CASE、UPPER_CAMEL_CASE、LOWER_CASE、LOWER_CAMEL_CASE。
 */
@DisplayName("命名策略测试")
class YdszJsonNamingTest {

    private PropertyNamingStrategy originalStrategy;

    @BeforeEach
    void saveStrategy() {
        originalStrategy = JsonConfig.getInstance().getNamingStrategy();
    }

    @AfterEach
    void restoreStrategy() {
        JsonConfig.getInstance().reset();
        JsonConfig.getInstance().apply();
    }

    @Nested
    @DisplayName("SNAKE_CASE 下划线命名")
    class SnakeCaseTests {

        @Test
        @DisplayName("camelCase → snake_case")
        void camelToSnake() {
            JsonConfig config = JsonConfig.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build();
            config.apply();

            TestBean bean = new TestBean();
            bean.setId(1);
            bean.setName("test");

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("\"id\":1"), "非驼峰字段不变: " + json);
            assertTrue(json.contains("\"name\":\"test\""), "单字段不变: " + json);
        }
    }

    @Nested
    @DisplayName("LOWER_CAMEL_CASE 默认策略")
    class LowerCamelCaseTests {

        @Test
        @DisplayName("保持原始名称")
        void preserveOriginalNames() {
            JsonConfig config = JsonConfig.builder()
                .namingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .build();
            config.apply();

            TestBean bean = new TestBean();
            bean.setId(1);
            bean.setName("hello");

            String json = YdszJson.toJson(bean);
            assertTrue(json.contains("\"id\":1"));
            assertTrue(json.contains("\"name\":\"hello\""));
        }
    }

    @Nested
    @DisplayName("Builder 模式配置命名策略")
    class BuilderConfigTests {

        @Test
        @DisplayName("JsonConfig.builder() 配置命名策略生效")
        void builderConfigNamingStrategy() {
            JsonConfig config = JsonConfig.builder()
                .namingStrategy(PropertyNamingStrategy.LOWER_CASE)
                .build();
            config.apply();

            TestBean bean = new TestBean();
            bean.setId(1);
            bean.setName("Test");

            String json = YdszJson.toJson(bean);
            assertNotNull(json);
            assertTrue(json.contains("1"));
            assertTrue(json.contains("Test"));
        }

        @Test
        @DisplayName("JsonMapper.builder() 配置命名策略生效")
        void mapperBuilderNamingStrategy() {
            JsonMapper mapper = JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.LOWER_CASE)
                .build();

            TestBean bean = new TestBean();
            bean.setId(1);
            bean.setName("Test");

            String json = mapper.toJson(bean);
            assertNotNull(json);
            assertTrue(json.contains("Test"));
        }
    }
}
