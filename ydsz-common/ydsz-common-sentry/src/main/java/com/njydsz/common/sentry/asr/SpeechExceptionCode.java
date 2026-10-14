package com.njydsz.common.sentry.asr;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 语音能力异常码枚举。
 *
 * <p>编码区间：
 *
 * <ul>
 *   <li>S80*** — 后端通道异常（未装配 / 超时 / 限流）
 *   <li>S81*** — ASR 语音识别异常（格式不支持 / 识别失败）
 *   <li>S82*** — TTS 文本转语音异常（发音人不存在 / 合成失败）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@Getter
@YdszExceptionCode(module = "sentry-asr", description = "语音能力")
public enum SpeechExceptionCode implements ExceptionCode {

  // ── 后端通道 S80 ──────────────────────────────────────────────────────────────

  /** 后端 Provider 未装配或不可用（HTTP 503） */
  PROVIDER_UNAVAILABLE(
      "S80001", "speech.provider.unavailable", 503,
      ExceptionLevel.ERROR, ExceptionCategory.EXTERNAL),

  /** 后端通道连接超时（HTTP 504 + retryable） */
  PROVIDER_TIMEOUT(
      "S80002", "speech.provider.timeout", 504,
      ExceptionLevel.ERROR, ExceptionCategory.EXTERNAL) {
    @Override
    public boolean retryable() {
      return true;
    }
  },

  /** 后端限流（HTTP 429 + retryable） */
  PROVIDER_RATE_LIMITED(
      "S80003", "speech.provider.rate.limited", 429,
      ExceptionLevel.WARN, ExceptionCategory.RATE_LIMIT) {
    @Override
    public boolean retryable() {
      return true;
    }
  },

  // ── ASR S81 ────────────────────────────────────────────────────────────────────

  /** 音频格式不支持（HTTP 400） */
  ASR_FORMAT_UNSUPPORTED(
      "S81001", "speech.asr.format.unsupported", 400,
      ExceptionLevel.WARN, ExceptionCategory.BUSINESS),

  /** 语音识别失败（HTTP 500） */
  ASR_RECOGNIZE_FAILED(
      "S81002", "speech.asr.recognize.failed", 500,
      ExceptionLevel.ERROR, ExceptionCategory.EXTERNAL),

  // ── TTS S82 ────────────────────────────────────────────────────────────────────

  /** 发音人不存在（HTTP 400） */
  TTS_VOICE_NOT_FOUND(
      "S82001", "speech.tts.voice.not.found", 400,
      ExceptionLevel.WARN, ExceptionCategory.BUSINESS),

  /** 文本转语音失败（HTTP 500） */
  TTS_SYNTHESIZE_FAILED(
      "S82002", "speech.tts.synthesize.failed", 500,
      ExceptionLevel.ERROR, ExceptionCategory.EXTERNAL);

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  /** 异常级别 */
  private final ExceptionLevel level;

  /** 异常分类 */
  private final ExceptionCategory category;

  SpeechExceptionCode(
      String code, String key, int httpStatus, ExceptionLevel level, ExceptionCategory category) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
    this.level = level;
    this.category = category;
  }
}
