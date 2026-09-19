package com.njydsz.common.exception.code;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 限流模块异常码。
 *
 * <p>限流、熔断、降级、流控相关异常码（A04 系列中的频率控制 + D01xxx）。 覆盖请求限流、操作频率限制、流量控制拒绝等场景。
 *
 * <p><b>分类注解（26.09.19 增强）：</b>通过 {@link #getCategory()} 显式返回 {@link
 * ExceptionCategory#RATE_LIMIT}，消除基于 key 前缀推断的脆弱性。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CoreExceptionCode
 * @see SecurityExceptionCode
 */
@Getter
@YdszExceptionCode(
    module = "ratelimit",
    description = "限流模块限流熔断降级异常码",
    category = ExceptionCategory.RATE_LIMIT)
public enum RateLimitExceptionCode implements ExceptionCode {

  // ==================== A04xx 请求频率相关 ====================

  /**
   * 请求过于频繁（原 ResponseCode.RATE_LIMIT 100429）
   *
   * @return 处理结果
   */
  RATE_LIMIT("A04057", "rate.limit", 429),
  /**
   * 请求过于频繁（限流）
   *
   * @return 处理结果
   */
  REQUEST_TOO_FREQUENT("A04058", "request.too.frequent", 429),
  /**
   * 操作过于频繁
   *
   * @return 处理结果
   */
  OPERATION_TOO_FREQUENT("A04059", "operation.too.frequent", 429),
  /**
   * 限流异常
   *
   * @return 处理结果
   */
  RATE_LIMIT_EXCEEDED("A04060", "rate.limit.exceeded", 429);

  // ==================== 字段定义 ====================

  /** 异常错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  RateLimitExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }

  @Override
  public int getHttpStatus() {
    return httpStatus;
  }

  /**
   * 统一返回 {@link ExceptionCategory#RATE_LIMIT}（26.09.19 增强）。
   *
   * <p>该模块所有错误码（限流/熔断/降级/频率）均归为 RATE_LIMIT 分类。
   *
   * @return {@link ExceptionCategory#RATE_LIMIT}
   */
  @Override
  public ExceptionCategory getCategory() {
    return ExceptionCategory.RATE_LIMIT;
  }
}
