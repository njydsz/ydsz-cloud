package com.njydsz.literule.server.engine.liteexpr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link FunctionRegistry} 单元测试
 *
 * <p>验证内置函数注册、自定义函数注册、函数查找、函数签名与描述等功能。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Tag("unit")
class FunctionRegistryTest {

  /** 测试用函数名 */
  private static final String TEST_FUNCTION_NAME = "triple";

  /** 测试用函数签名 */
  private static final String TEST_FUNCTION_SIGNATURE = "triple(n)";

  /** 测试用函数描述 */
  private static final String TEST_FUNCTION_DESCRIPTION = "乘以3";

  private FunctionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new FunctionRegistry();
  }

  @Test
  @DisplayName("构造时应自动注册全部内置函数（abs/max/min 等）")
  void constructorShouldRegisterAllBuiltinFunctions() {
    assertThat(registry.contains("abs")).isTrue();
    assertThat(registry.contains("max")).isTrue();
    assertThat(registry.contains("min")).isTrue();
    assertThat(registry.contains("concat")).isTrue();
    assertThat(registry.contains("size")).isTrue();
  }

  @Test
  @DisplayName("未注册函数应返回 contains=false")
  void containsShouldReturnFalseForUnregisteredFunction() {
    assertThat(registry.contains("nonExistentFunction12345")).isFalse();
  }

  @Test
  @DisplayName("获取已注册函数的签名应返回非空字符串")
  void getSignatureShouldReturnNonNullForBuiltinFunction() {
    String signature = registry.getSignature("abs");
    assertThat(signature).isNotNull();
  }

  @Test
  @DisplayName("获取未注册函数的签名应返回 null")
  void getSignatureShouldReturnNullForUnregisteredFunction() {
    assertThat(registry.getSignature("nonExistentFunction12345")).isNull();
  }

  @Test
  @DisplayName("获取已注册函数的描述应返回非空字符串")
  void getDescriptionShouldReturnNonNullForBuiltinFunction() {
    String description = registry.getDescription("abs");
    assertThat(description).isNotNull();
  }

  @Test
  @DisplayName("自定义函数注册后应可被查找")
  void customFunctionShouldBeFindableAfterRegistration() {
    registry.register(
        TEST_FUNCTION_NAME,
        args -> {
          if (args.length == 0 || args[0] == null) {
            return BigDecimal.ZERO;
          }
          return new BigDecimal(args[0].toString()).multiply(BigDecimal.valueOf(3));
        },
        TEST_FUNCTION_SIGNATURE,
        TEST_FUNCTION_DESCRIPTION);

    assertThat(registry.contains(TEST_FUNCTION_NAME)).isTrue();
    assertThat(registry.getSignature(TEST_FUNCTION_NAME)).isEqualTo(TEST_FUNCTION_SIGNATURE);
    assertThat(registry.getDescription(TEST_FUNCTION_NAME)).isEqualTo(TEST_FUNCTION_DESCRIPTION);
  }

  @Test
  @DisplayName("内置函数数量应不少于 10 个")
  void builtinFunctionCountShouldBeAtLeastTen() {
    assertThat(registry.getFunctionNames().size()).isGreaterThanOrEqualTo(10);
  }

  @Test
  @DisplayName("lookup 内置函数应返回非空实例")
  void lookupBuiltinFunctionShouldReturnNonNull() {
    LiteExprFunction absFunction = registry.lookup("abs");
    assertThat(absFunction).isNotNull();
  }

  @Test
  @DisplayName("lookup 未注册函数应返回 null")
  void lookupUnregisteredFunctionShouldReturnNull() {
    assertThat(registry.lookup("nonExistentFunction12345")).isNull();
  }
}
