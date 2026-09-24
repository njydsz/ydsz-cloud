package com.njydsz.literule.domain.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpressionFunctionDef} 单元测试
 *
 * <p>验证函数市场默认清单的完整性、字段约束和分类覆盖。确保前端函数补全、签名展示与 hover 说明
 * 具备正确的数据基础。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
class ExpressionFunctionDefTest {

  @Nested
  @DisplayName("defaults() 默认函数清单")
  class Defaults {

    @Test
    @DisplayName("默认函数列表不应为空")
    void defaultsShouldNotBeEmpty() {
      List<ExpressionFunctionDef> defaults = ExpressionFunctionDef.defaults();
      assertThat(defaults).isNotEmpty();
    }

    @Test
    @DisplayName("默认函数数量应不少于 10 个")
    void defaultsShouldHaveAtLeastTenFunctions() {
      assertThat(ExpressionFunctionDef.defaults()).size().isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("每个默认函数应包含必需的字段（name/signature/description）")
    void eachDefaultFunctionShouldHaveRequiredFields() {
      List<ExpressionFunctionDef> defaults = ExpressionFunctionDef.defaults();

      for (ExpressionFunctionDef def : defaults) {
        assertThat(def.getName())
            .as("函数名不应为空")
            .isNotNull()
            .isNotBlank();
        assertThat(def.getSignature())
            .as("函数签名不应为空: " + def.getName())
            .isNotNull()
            .isNotBlank();
        assertThat(def.getDescription())
            .as("函数描述不应为空: " + def.getName())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("函数名不应重复")
    void functionNamesShouldBeUnique() {
      List<ExpressionFunctionDef> defaults = ExpressionFunctionDef.defaults();
      Set<String> uniqueNames = Set.copyOf(
          defaults.stream().map(ExpressionFunctionDef::getName).toList());

      assertThat(uniqueNames).hasSize(defaults.size());
    }

    @Test
    @DisplayName("应覆盖指定的核心函数（abs/max/min/concat/if）")
    void shouldContainCoreFunctions() {
      Set<String> names = Set.copyOf(
          ExpressionFunctionDef.defaults().stream()
              .map(ExpressionFunctionDef::getName)
              .toList());

      assertThat(names).contains("abs", "max", "min", "concat", "if", "isNull", "isNotNull");
    }

    @Test
    @DisplayName("所有函数的 supportedEngines 字段不应为空")
    void supportedEnginesShouldNotBeEmpty() {
      List<ExpressionFunctionDef> defaults = ExpressionFunctionDef.defaults();
      for (ExpressionFunctionDef def : defaults) {
        assertThat(def.getSupportedEngines())
            .as("supportedEngines 不应为空: " + def.getName())
            .isNotNull()
            .isNotBlank();
      }
    }

    @Test
    @DisplayName("returns 应为不可变列表（修改应抛出异常）")
    void defaultsShouldBeImmutable() {
      List<ExpressionFunctionDef> defaults = ExpressionFunctionDef.defaults();
      assertThatThrownBy(() -> defaults.add(
          new ExpressionFunctionDef("hack", "hack()", "入侵", "", "", "")))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("分类覆盖")
  class CategoryCoverage {

    @Test
    @DisplayName("应包含 string 分类函数")
    void shouldContainStringCategory() {
      Set<String> stringFunctions = ExpressionFunctionDef.defaults().stream()
          .filter(def -> "string".equals(def.getCategory()))
          .map(ExpressionFunctionDef::getName)
          .collect(Collectors.toSet());

      assertThat(stringFunctions).isNotEmpty();
    }

    @Test
    @DisplayName("应包含 math 分类函数")
    void shouldContainMathCategory() {
      Set<String> mathFunctions = ExpressionFunctionDef.defaults().stream()
          .filter(def -> "math".equals(def.getCategory()))
          .map(ExpressionFunctionDef::getName)
          .collect(Collectors.toSet());

      assertThat(mathFunctions).contains("abs", "max", "min", "round");
    }

    @Test
    @DisplayName("应包含 datetime 分类函数")
    void shouldContainDatetimeCategory() {
      boolean hasDatetime = ExpressionFunctionDef.defaults().stream()
          .anyMatch(def -> "datetime".equals(def.getCategory()));

      assertThat(hasDatetime).isTrue();
    }

    @Test
    @DisplayName("应包含 type 分类函数")
    void shouldContainTypeCategory() {
      boolean hasType = ExpressionFunctionDef.defaults().stream()
          .anyMatch(def -> "type".equals(def.getCategory()));

      assertThat(hasType).isTrue();
    }
  }
}
