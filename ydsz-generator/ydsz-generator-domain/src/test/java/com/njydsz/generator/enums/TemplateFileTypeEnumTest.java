package com.njydsz.generator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TemplateFileTypeEnum} 单元测试。
 *
 * <p>覆盖：code/description getter、枚举值数量与顺序。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("模板文件类型枚举")
class TemplateFileTypeEnumTest {

  @Nested
  @DisplayName("getter")
  class Accessors {

    @ParameterizedTest
    @CsvSource({
      "BACKEND, BACKEND, generator.filetype.backend",
      "FRONTEND, FRONTEND, generator.filetype.frontend"
    })
    @DisplayName("每个枚举的 code 与 description 一致")
    void codeAndDescription(
        TemplateFileTypeEnum type, String expectedCode, String expectedDesc) {
      assertThat(type.getCode()).isEqualTo(expectedCode);
      assertThat(type.getDescription()).isEqualTo(expectedDesc);
    }
  }

  @Test
  @DisplayName("枚举值数量为 2")
  void valueCount() {
    assertThat(TemplateFileTypeEnum.values()).hasSize(2);
  }

  @Test
  @DisplayName("枚举值顺序为 BACKEND/FRONTEND")
  void valueOrder() {
    assertThat(TemplateFileTypeEnum.values())
        .containsExactly(TemplateFileTypeEnum.BACKEND, TemplateFileTypeEnum.FRONTEND);
  }
}
