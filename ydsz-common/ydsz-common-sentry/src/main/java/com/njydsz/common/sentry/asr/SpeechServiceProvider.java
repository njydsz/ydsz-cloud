package com.njydsz.common.sentry.asr;

import java.util.Iterator;
import java.util.ServiceLoader;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 语音能力 SPI 加载器。
 *
 * <p>通过 JDK {@link ServiceLoader} 加载 {@link SpeechService} 实现类，
 * 支持多后端（阿里 / 讯飞 / Whisper）按需装配。业务模块通过 {@link #load()} 获取可用实例，
 * 未装配任何后端时抛出 {@link BusinessException}。
 *
 * <p><b>SPI 配置示例：</b>
 *
 * <pre>
 * 文件 {@code META-INF/services/com.njydsz.common.sentry.asr.SpeechService} 内容：
 *   com.example.AliSpeechServiceImpl
 * </pre>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
@Slf4j
public final class SpeechServiceProvider {

  private SpeechServiceProvider() {}

  /**
   * 加载 {@link SpeechService} SPI 实例。
   *
   * <p>遍历所有 SPI 实现，返回第一个 {@link SpeechService#isAvailable()} 为 {@code true} 的实例。
   *
   * @return 可用的 SpeechService 实例
   * @throws BusinessException 未找到任何可用实现时抛出
   */
  public static SpeechService load() {
    ServiceLoader<SpeechService> loader = ServiceLoader.load(SpeechService.class);
    Iterator<SpeechService> iterator = loader.iterator();
    while (iterator.hasNext()) {
      SpeechService service = iterator.next();
      if (service.isAvailable()) {
        log.info("SpeechService SPI 加载成功，后端实现：{}", service.getClass().getName());
        return service;
      }
    }
    throw BusinessException.builder()
        .resultCode(CoreExceptionCode.FAIL)
        .key("speech.service.not.found")
        .build();
  }
}
