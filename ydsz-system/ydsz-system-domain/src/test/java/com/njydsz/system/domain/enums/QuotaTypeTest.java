package com.njydsz.system.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QuotaType} 单元测试。
 *
 * <p>覆盖：fromJsonKey 查找、JSON 键名/显示名/值类型映射、未知键返回 null。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("租户配额类型枚举解析")
class QuotaTypeTest {

  @Nested
  @DisplayName("fromJsonKey 合法值")
  class FromJsonKeyValid {

    @ParameterizedTest(name = "fromJsonKey(\"{0}\") 应返回 {1}")
    @CsvSource({
        "maxUsers, MAX_USERS",
        "maxProjects, MAX_PROJECTS",
        "maxConfigs, MAX_CONFIGS",
        "maxDictTypes, MAX_DICT_TYPES",
        "maxVariables, MAX_VARIABLES",
        "storageGb, STORAGE_GB"
    })
    @DisplayName("全量 JSON 键均可查找")
    void should_find_all_keys(String key, QuotaType expected) {
      assertThat(QuotaType.fromJsonKey(key)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("fromJsonKey 非法值返回 null")
  class FromJsonKeyInvalid {

    @Test
    @DisplayName("null 返回 null")
    void should_return_null_for_null() {
      assertThat(QuotaType.fromJsonKey(null)).isNull();
    }

    @ParameterizedTest(name = "fromJsonKey(\"{0}\") 未知键返回 null")
    @ValueSource(strings = {"", "maxusers", "unknown", "MAX_USERS", "1"})
    @DisplayName("未知键返回 null")
    void should_return_null_for_unknown(String key) {
      assertThat(QuotaType.fromJsonKey(key)).isNull();
    }
  }

  @Nested
  @DisplayName("字段映射")
  class Fields {

    @ParameterizedTest(name = "{0} 的 JSON 键应为 \"{1}\"")
    @CsvSource({
        "MAX_USERS, maxUsers",
        "MAX_PROJECTS, maxProjects",
        "MAX_CONFIGS, maxConfigs",
        "MAX_DICT_TYPES, maxDictTypes",
        "MAX_VARIABLES, maxVariables",
        "STORAGE_GB, storageGb"
    })
    @DisplayName("jsonKey 映射正确")
    void should_have_expected_json_key(QuotaType type, String expected) {
      assertThat(type.getJsonKey()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} 的值类型应为 {1}")
    @CsvSource({
        "MAX_USERS, java.lang.Integer",
        "MAX_PROJECTS, java.lang.Integer",
        "MAX_CONFIGS, java.lang.Integer",
        "MAX_DICT_TYPES, java.lang.Integer",
        "MAX_VARIABLES, java.lang.Integer",
        "STORAGE_GB, java.lang.Double"
    })
    @DisplayName("valueType 映射正确")
    void should_have_expected_value_type(QuotaType type, String expectedClassName) {
      Class<?> expected;
      if ("java.lang.Double".equals(expectedClassName)) {
        expected = Double.class;
      } else {
        expected = Integer.class;
      }
      assertThat(type.getValueType()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("枚举值总量")
  class Values {

    @Test
    @DisplayName("共 6 个配额维度")
    void should_have_six_types() {
      assertThat(QuotaType.values()).hasSize(6);
    }
  }
}
