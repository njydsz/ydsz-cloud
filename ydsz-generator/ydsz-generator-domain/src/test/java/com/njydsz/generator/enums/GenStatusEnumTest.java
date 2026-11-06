package com.njydsz.generator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GenStatusEnum} 单元测试。
 *
 * <p>覆盖：code/description getter、枚举值数量与顺序。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("代码生成任务状态枚举")
class GenStatusEnumTest {

  @Nested
  @DisplayName("getter")
  class Accessors {

    @ParameterizedTest
    @CsvSource({
      "RUNNING, RUNNING, generator.genstatus.running",
      "SUCCESS, SUCCESS, generator.genstatus.success",
      "PARTIAL, PARTIAL, generator.genstatus.partial",
      "FAILED, FAILED, generator.genstatus.failed"
    })
    @DisplayName("每个枚举的 code 与 description 一致")
    void codeAndDescription(GenStatusEnum status, String expectedCode, String expectedDesc) {
      assertThat(status.getCode()).isEqualTo(expectedCode);
      assertThat(status.getDescription()).isEqualTo(expectedDesc);
    }
  }

  @Test
  @DisplayName("枚举值数量为 4")
  void valueCount() {
    assertThat(GenStatusEnum.values()).hasSize(4);
  }

  @Test
  @DisplayName("枚举值顺序为 RUNNING/SUCCESS/PARTIAL/FAILED")
  void valueOrder() {
    assertThat(GenStatusEnum.values())
        .containsExactly(
            GenStatusEnum.RUNNING,
            GenStatusEnum.SUCCESS,
            GenStatusEnum.PARTIAL,
            GenStatusEnum.FAILED);
  }
}
