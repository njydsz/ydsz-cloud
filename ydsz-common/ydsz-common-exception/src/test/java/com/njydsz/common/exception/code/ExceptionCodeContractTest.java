package com.njydsz.common.exception.code;

import com.njydsz.common.exception.enums.ExceptionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * common-exception 模块错误码契约测试。
 *
 * <p>锁定跨枚举的全局不变量 —— 这正是 {@link ErrorCodeTable} 在运行期启动校验所守护的约束：
 * 所有 {@code @YdszExceptionCode} 标注枚举的 code 必须全局唯一且格式合法。
 * 本测试在纯单测层先行拦截，无需等应用启动才能暴露冲突。
 *
 * <p>错误码是前后端业务契约（前端 error-codes.generated.ts 与此同源生成），
 * 任何破坏本测试的改动都会直接导致前端错误码映射漂移。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ErrorCodeTable
 * @see CoreExceptionCode
 * @see SecurityExceptionCode
 * @see RateLimitExceptionCode
 */
class ExceptionCodeContractTest {

  /** 错误码格式：1 位段字母 + 5 位数字 */
  private static final String CODE_PATTERN = "[A-Z]\\d{5}";

  /** HTTP 状态码合理区间（ExceptionCode#getHttpStatus 契约） */
  private static final int MIN_HTTP_STATUS = 400;

  private static final int MAX_HTTP_STATUS = 599;

  @Test
  @DisplayName("公共异常码枚举全部实现 ExceptionCode 契约")
  void enumsShouldImplementExceptionCode() {
    for (Class<?> enumClass :
        new Class<?>[] {CoreExceptionCode.class, SecurityExceptionCode.class, RateLimitExceptionCode.class}) {
      assertThat(ExceptionCode.class)
          .as("%s 应实现 ExceptionCode", enumClass.getSimpleName())
          .isAssignableFrom(enumClass);
    }
  }

  @Test
  @DisplayName("全部异常码符合 字母+5位数字 格式")
  void allCodesShouldMatchFormat() {
    for (ExceptionCode code : allCodes()) {
      assertThat(code.getCode())
          .as("异常码 %s 不符合格式要求", code)
          .matches(CODE_PATTERN);
    }
  }

  @Test
  @DisplayName("跨枚举异常码全局唯一（对应 ErrorCodeTable 启动期 fail-fast 校验）")
  void codesShouldBeGloballyUnique() {
    Set<String> seen = new HashSet<>();
    for (ExceptionCode code : allCodes()) {
      assertThat(seen.add(code.getCode()))
          .as("异常码 %s 跨枚举重复（违反 ErrorCodeTable 全局唯一约束）", code.getCode())
          .isTrue();
    }
  }

  @Test
  @DisplayName("HTTP 状态码合法：SUCCESS 允许 200，其余落在 400-599")
  void httpStatusShouldBeInRange() {
    for (ExceptionCode code : allCodes()) {
      if ("A00000".equals(code.getCode())) {
        // 成功码允许 200（HTTP OK），非异常语义
        assertThat(code.getHttpStatus()).isEqualTo(200);
        continue;
      }
      assertThat(code.getHttpStatus())
          .as("异常码 %s 的 HTTP 状态码越界", code.getCode())
          .isBetween(MIN_HTTP_STATUS, MAX_HTTP_STATUS);
    }
  }

  @Test
  @DisplayName("i18n 消息键非空")
  void keysShouldNotBeBlank() {
    for (ExceptionCode code : allCodes()) {
      assertThat(code.getKey()).as("异常码 %s 的 i18n key 为空", code.getCode()).isNotBlank();
    }
  }

  /** 汇总本模块全部异常码枚举常量 */
  private ExceptionCode[] allCodes() {
    ExceptionCode[] core = CoreExceptionCode.values();
    ExceptionCode[] security = SecurityExceptionCode.values();
    ExceptionCode[] rateLimit = RateLimitExceptionCode.values();
    ExceptionCode[] all = new ExceptionCode[core.length + security.length + rateLimit.length];
    System.arraycopy(core, 0, all, 0, core.length);
    System.arraycopy(security, 0, all, core.length, security.length);
    System.arraycopy(rateLimit, 0, all, core.length + security.length, rateLimit.length);
    return all;
  }
}
