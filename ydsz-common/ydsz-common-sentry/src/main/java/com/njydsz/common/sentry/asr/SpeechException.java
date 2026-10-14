package com.njydsz.common.sentry.asr;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 语音能力业务异常。
 *
 * <p>ASR 识别失败、TTS 合成失败、后端通道异常等场景抛出。 错误码使用 {@link SpeechExceptionCode}，
 * 消息通过 i18n key 走国际化解析。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public class SpeechException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 使用语音异常码构造异常
   *
   * @param exceptionCode 语音异常码
   */
  public SpeechException(SpeechExceptionCode exceptionCode) {
    super(exceptionCode);
  }

  /**
   * 使用语音异常码和原始异常构造异常
   *
   * @param exceptionCode 语音异常码
   * @param cause 原始异常
   */
  public SpeechException(SpeechExceptionCode exceptionCode, Throwable cause) {
    super(exceptionCode, cause);
  }

  /**
   * 使用语音异常码和自定义消息构造异常
   *
   * <p>保留用于需要补充动态上下文（如具体后端名称、错误详情）的场景。
   *
   * @param exceptionCode 语音异常码
   * @param message 自定义消息
   */
  public SpeechException(SpeechExceptionCode exceptionCode, String message) {
    super(exceptionCode);
    setMessage(message);
  }

  /**
   * 使用通用异常码和原始异常构造异常
   *
   * <p>保留用于包装通用异常（如 CoreExceptionCode）的场景。
   *
   * @param exceptionCode 通用异常码
   * @param cause 原始异常
   */
  protected SpeechException(ExceptionCode exceptionCode, Throwable cause) {
    super(exceptionCode, cause);
  }
}
