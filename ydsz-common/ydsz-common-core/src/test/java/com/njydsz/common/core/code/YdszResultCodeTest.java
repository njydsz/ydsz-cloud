package com.njydsz.common.core.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YdszResultCode 契约测试。
 *
 * <p>锁定平台通用结果码的结构不变量：码格式（字母 + 5 位数字）、全局唯一性、
 * 成功码固定为 {@code A00000}、三要素（code / msg）非空。
 *
 * <p>错误码是前后端业务契约（前端 error-codes.generated.ts 与此同源生成），
 * 任何破坏本测试的改动都会直接导致前端错误码映射漂移。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see YdszResultCode
 * @see ResultCode
 */
class YdszResultCodeTest {

  /** 错误码格式：1 位段字母 + 5 位数字 */
  private static final String CODE_PATTERN = "[A-Z]\\d{5}";

  @Test
  @DisplayName("成功码固定为 A00000")
  void successCodeShouldBeA00000() {
    assertThat(YdszResultCode.SUCCESS.getCode()).isEqualTo("A00000");
  }

  @Test
  @DisplayName("全部结果码符合 字母+5位数字 格式")
  void allCodesShouldMatchFormat() {
    for (YdszResultCode resultCode : YdszResultCode.values()) {
      assertThat(resultCode.getCode())
          .as("结果码 %s 不符合格式要求（%s）", resultCode, CODE_PATTERN)
          .matches(CODE_PATTERN);
    }
  }

  @Test
  @DisplayName("枚举内结果码无重复")
  void codesShouldBeUnique() {
    Set<String> seen = new HashSet<>();
    for (YdszResultCode resultCode : YdszResultCode.values()) {
      assertThat(seen.add(resultCode.getCode()))
          .as("结果码 %s 重复注册", resultCode.getCode())
          .isTrue();
    }
  }

  @Test
  @DisplayName("三要素中 code 与 msg 均非空")
  void codeAndMsgShouldNotBeBlank() {
    for (YdszResultCode resultCode : YdszResultCode.values()) {
      assertThat(resultCode.getCode()).as("%s 的 code 为空", resultCode).isNotBlank();
      assertThat(resultCode.getMsg()).as("%s 的 msg 为空", resultCode).isNotBlank();
    }
  }
}
