package com.njydsz.system.domain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigDTO} 单元测试。
 *
 * <p>覆盖：Lombok getter/setter、equals/hashCode、toString 字段信息。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("系统配置 DTO 单元测试")
class ConfigDTOTest {

  @Nested
  @DisplayName("getter/setter")
  class Accessors {

    @Test
    @DisplayName("setter 正确写入全部字段")
    void should_set_all_fields() {
      ConfigDTO dto = new ConfigDTO();
      dto.setId("id-1");
      dto.setConfigGroup("SYSTEM");
      dto.setConfigKey("max-upload-size");
      dto.setConfigValue("10485760");
      dto.setValueType("NUMBER");
      dto.setDefaultValue("1024");
      dto.setDescription("最大上传文件大小");
      dto.setIsPublic(Boolean.TRUE);
      dto.setSort(1);
      dto.setStatus("ENABLED");

      assertThat(dto.getId()).isEqualTo("id-1");
      assertThat(dto.getConfigGroup()).isEqualTo("SYSTEM");
      assertThat(dto.getConfigKey()).isEqualTo("max-upload-size");
      assertThat(dto.getConfigValue()).isEqualTo("10485760");
      assertThat(dto.getValueType()).isEqualTo("NUMBER");
      assertThat(dto.getDefaultValue()).isEqualTo("1024");
      assertThat(dto.getDescription()).isEqualTo("最大上传文件大小");
      assertThat(dto.getIsPublic()).isTrue();
      assertThat(dto.getSort()).isEqualTo(1);
      assertThat(dto.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("无参构造字段为 null")
    void should_have_null_fields_by_default() {
      ConfigDTO dto = new ConfigDTO();

      assertThat(dto.getId()).isNull();
      assertThat(dto.getConfigGroup()).isNull();
      assertThat(dto.getConfigKey()).isNull();
      assertThat(dto.getConfigValue()).isNull();
      assertThat(dto.getValueType()).isNull();
      assertThat(dto.getIsPublic()).isNull();
      assertThat(dto.getSort()).isNull();
      assertThat(dto.getStatus()).isNull();
    }
  }

  @Nested
  @DisplayName("SuperBuilder")
  class Builder {

    @Test
    @DisplayName("builder 构造并填充字段")
    void should_build_with_builder() {
      ConfigDTO dto = ConfigDTO.builder()
          .configGroup("SYSTEM")
          .configKey("k1")
          .valueType("STRING")
          .build();

      assertThat(dto.getConfigGroup()).isEqualTo("SYSTEM");
      assertThat(dto.getConfigKey()).isEqualTo("k1");
      assertThat(dto.getValueType()).isEqualTo("STRING");
      assertThat(dto.getId()).isNull();
    }
  }

  @Nested
  @DisplayName("equals/hashCode")
  class Equality {

    @Test
    @DisplayName("相同字段相等")
    void should_be_equal() {
      ConfigDTO a = ConfigDTO.builder().configGroup("G").configKey("K").build();
      ConfigDTO b = ConfigDTO.builder().configGroup("G").configKey("K").build();

      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("不同字段不等")
    void should_not_be_equal() {
      ConfigDTO a = ConfigDTO.builder().configKey("K1").build();
      ConfigDTO b = ConfigDTO.builder().configKey("K2").build();

      assertThat(a).isNotEqualTo(b);
    }
  }

  @Nested
  @DisplayName("toString")
  class ToString {

    @Test
    @DisplayName("包含关键字段")
    void should_contain_fields() {
      ConfigDTO dto = ConfigDTO.builder().configGroup("G").configKey("K").build();

      assertThat(dto.toString()).contains("G").contains("K");
    }
  }
}
