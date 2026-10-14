package com.njydsz.common.sentry.asr;

/**
 * 语音能力抽象接口骨架。
 *
 * <p>ASR（语音识别）+ TTS（文本转语音）统一入口。
 *
 * <p>实现类通过 SPI 加载，按需装配阿里/讯飞/Whisper 后端。
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public interface SpeechService {

  /**
   * ASR — 语音识别。
   *
   * @param audioBytes 音频字节（PCM/mp3/wav）
   * @param format 音频格式（pcm/mp3/wav）
   * @param sampleRate 采样率（16000）
   * @return 识别文本
   */
  String recognize(byte[] audioBytes, String format, int sampleRate) throws Exception;

  /**
   * TTS — 文本转语音。
   *
   * @param text 待合成文本
   * @param voice 发音人（"xiaoyun"/"xiaogang"）
   * @param speed 语速（0-100）
   * @param volume 音量（0-100）
   * @return 音频字节（mp3）
   */
  byte[] synthesize(String text, String voice, int speed, int volume) throws Exception;

  /**
   * 是否可用（后端 Provider 已装配）。
   *
   * @return true - 至少有一个后端 Provider 可用；false - 未装配任何后端
   */
  boolean isAvailable();
}
