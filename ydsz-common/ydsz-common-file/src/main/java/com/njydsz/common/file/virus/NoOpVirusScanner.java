package com.njydsz.common.file.virus;

import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;

/**
 * NoOp virus scanner (default fallback).
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class NoOpVirusScanner implements VirusScanner {

  /**
   * 空实现：直接放行，不读取也不关闭输入流。
   *
   * <p><b>流契约：</b>本实现<b>完全不消费</b> {@code inputStream}， 因此流位置保持不变，调用方后续仍可从头读取并自行负责关闭。
   * 这一点与真实扫描引擎不同——真实引擎会读完整个流， 调用方必须传入可重复读取的流（如包装为 {@code BufferedInputStream} 并 mark/reset）。
   *
   * @param inputStream 待扫描的文件流；本实现忽略该参数，允许为 {@code null}
   * @param fileName 原始文件名，仅用于 debug 日志定位
   * @return 恒为 {@link ScanResult#CLEAN}，即视为无病毒
   */
  @Override
  public ScanResult scan(InputStream inputStream, String fileName) {
    log.debug("VirusScan NoOp skipping: {}", fileName);
    return ScanResult.CLEAN;
  }

  /**
   * 恒定返回可用，保证上传链路在未接入真实杀毒引擎时也能正常放行。
   *
   * <p>若此处返回 {@code false}，上层会记录"扫描引擎不可用"告警， 而默认场景下这属于噪音，故统一声明为可用。
   *
   * @return 恒为 {@code true}
   */
  @Override
  public boolean isAvailable() {
    return true;
  }
}
