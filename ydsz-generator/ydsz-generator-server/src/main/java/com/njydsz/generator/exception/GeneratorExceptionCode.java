package com.njydsz.generator.exception;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代码生成器模块异常码枚举。
 *
 * <p>预分配编码区间 B96xxx，具体异常常量待实际场景明确后补充。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@AllArgsConstructor
@YdszExceptionCode(module = "generator", description = "代码生成器模块异常码", since = "26.09.01")
public enum GeneratorExceptionCode implements ExceptionCode {

  // TODO: 预分配编码，等实际异常场景明确后补充
  // TEMPLATE_RENDER_FAILED("B96001", "generator.template.render_failed", 500, false, 0),
  // CODE_GEN_FAILED("B96002", "generator.code.gen_failed", 500, false, 0),
  ;

  /** 错误码字符串（如 "B96001"） */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 是否可恢复（客户端可重试） */
  private final boolean retryable;

  /** 建议重试等待秒数 */
  private final int retryAfterSeconds;

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }

  @Override
  public boolean retryable() {
    return retryable;
  }

  @Override
  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
