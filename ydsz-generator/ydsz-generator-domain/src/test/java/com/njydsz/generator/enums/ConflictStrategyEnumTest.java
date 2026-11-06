package com.njydsz.generator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConflictStrategyEnum} 单元测试。
 *
 * <p>覆盖：code/description getter、枚举值数量与顺序。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("文件冲突策略枚举")
class ConflictStrategyEnumTest {

  @Nested
  @DisplayName("getter")
  class Accessors {

    @ParameterizedTest
    @CsvSource({
      "SKIP, SKIP, generator.conflict.skip",
      "OVERRIDE, OVERRIDE, generator.conflict.override",
      "APPEND, APPEND, generator.conflict.append"
    })
    @DisplayName("每个枚举的 code 与 description 一致")
    void codeAndDescription(
        ConflictStrategyEnum strategy, String expectedCode, String expectedDesc) {
      assertThat(strategy.getCode()).isEqualTo(expectedCode);
      assertThat(strategy.getDescription()).isEqualTo(expectedDesc);
    }
  }

  @Test
  @DisplayName("枚举值数量为 3")
  void valueCount() {
    assertThat(ConflictStrategyEnum.values()).hasSize(3);
  }

  @Test
  @DisplayName("枚举值顺序为 SKIP/OVERRIDE/APPEND")
  void valueOrder() {
    assertThat(ConflictStrategyEnum.values())
        .containsExactly(
            ConflictStrategyEnum.SKIP,
            ConflictStrategyEnum.OVERRIDE,
            ConflictStrategyEnum.APPEND);
  }
}
