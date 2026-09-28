package com.njydsz.agent.domain.code;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.locales.util.I18n;

/**
 * 代码执行异常。
 *
 * <p>当 Python 代码块沙箱执行遇到安全问题、超时、编译错误或运行时异常时抛出。
 * 即使执行失败也会携带 {@link CodeExecutionResult} 提供错误上下文。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
public class CodeExecutionException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 执行结果（可能为 null） */
  private final CodeExecutionResult result;

  /**
   * 构造异常。
   *
   * @param message 错误描述
   * @param result 执行结果（可能为 null）
   */
  public CodeExecutionException(String message, CodeExecutionResult result) {
    super(message);
    this.result = result;
  }

  /**
   * 构造异常（携带根因）。
   *
   * @param message 错误描述
   * @param result 执行结果（可能为 null）
   * @param cause 根因异常
   */
  public CodeExecutionException(String message, CodeExecutionResult result, Throwable cause) {
    super(message, cause);
    this.result = result;
  }

  /**
   * 获取执行结果。
   *
   * @return 执行结果（可能为 null）
   */
  public CodeExecutionResult getResult() {
    return result;
  }

  /**
   * 创建超时异常。
   *
   * @param timeoutSeconds 超时秒数
   * @param durationMs 已执行耗时
   * @return 超时异常实例
   */
  public static CodeExecutionException timeout(int timeoutSeconds, long durationMs) {
    return new CodeExecutionException(
        I18n.message("agent.error.code.execution.timeout", new Object[]{timeoutSeconds}),
        CodeExecutionResult.timeout(timeoutSeconds, durationMs));
  }

  /**
   * 创建模块白名单拒绝异常。
   *
   * @param module 被拒绝的模块名
   * @return 模块拒绝异常实例
   */
  public static CodeExecutionException moduleNotAllowed(String module) {
    return new CodeExecutionException(
        I18n.message("agent.error.code.module_not_allowed", new Object[]{module}),
        null);
  }

  /**
   * 创建编译错误异常。
   *
   * @param msg 编译错误信息
   * @return 编译异常实例
   */
  public static CodeExecutionException compilationError(String msg) {
    return new CodeExecutionException(
        I18n.message("agent.error.code.compilation_error", new Object[]{msg}), null);
  }

  /**
   * 创建运行时错误异常。
   *
   * @param msg 运行时错误信息
   * @return 运行时异常实例
   */
  public static CodeExecutionException runtimeError(String msg) {
    return new CodeExecutionException(
        I18n.message("agent.error.code.runtime_error", new Object[]{msg}), null);
  }
}
