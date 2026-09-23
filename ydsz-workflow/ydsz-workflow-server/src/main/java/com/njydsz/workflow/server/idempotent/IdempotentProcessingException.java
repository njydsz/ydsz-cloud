package com.njydsz.workflow.server.idempotent;

import com.njydsz.common.exception.custom.BusinessException;

import java.io.Serial;

/**
 * 幂等处理中异常。
 *
 * <p>当幂等守卫检测到相同幂等键的请求正在处理中（PROCESSING 状态）且未配置等待时抛出。
 * 调用方（通常为 Controller 层）应捕获此异常并返回 HTTP 409 Conflict 或友好提示。
 *
 * <p>P1-8 整改：原为裸 RuntimeException，现继承 common-exception 的 {@link BusinessException}，
 * 纳入统一异常体系（统一错误码、i18n、BaseExceptionHandler 自动映射 ProblemDetail）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public class IdempotentProcessingException extends BusinessException {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 幂等作用域 */
  private final String scope;
  /** 幂等键明文 */
  private final String keyRaw;

  /**
   * 构造幂等处理中异常。
   *
   * @param scope 幂等作用域
   * @param keyRaw 幂等键明文
   */
  public IdempotentProcessingException(String scope, String keyRaw) {
    super();
    setMessage(String.format("请求正在处理中，请稍后重试 [scope=%s, key=%s]", scope, keyRaw));
    this.scope = scope;
    this.keyRaw = keyRaw;
  }

  public String getScope() {
    return scope;
  }

  public String getKeyRaw() {
    return keyRaw;
  }
}
