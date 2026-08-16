package com.njydsz.common.queue.compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列消息压缩工具
 *
 * <p>对超过阈值的消息体进行 GZIP 压缩后 Base64 编码，减少 MQ 网络传输和存储开销。 压缩后消息带 {@code GZIP:} 前缀标记，消费端根据前缀自动解压。
 *
 * <p>支持两种使用模式：
 *
 * <ul>
 *   <li><b>静态方法模式</b>：使用默认 1KB 阈值快速压缩/解压
 *   <li><b>实例方法模式</b>：通过构造参数自定义阈值
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * // 生产端：压缩
 * String compressed = MessageCompressor.compressIfNeeded(payload);
 *
 * // 消费端：解压
 * String original = MessageCompressor.decompressIfNeeded(compressed);
 * }</pre>
 *
 * <p>统一使用 {@code GZIP:} 作为压缩标记前缀，与 ydsz-common-socket 的 WebSocket 压缩器保持兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class MessageCompressor {

  /** 默认压缩阈值：1KB */
  public static final int DEFAULT_COMPRESS_THRESHOLD = 1024;

  /** 压缩标记前缀（与 WebSocket 压缩器保持兼容） */
  public static final String COMPRESSED_PREFIX = "GZIP:";

  /** 实例级压缩阈值 */
  private final int threshold;

  /** 使用默认阈值（1KB）创建压缩器。 */
  public MessageCompressor() {
    this(DEFAULT_COMPRESS_THRESHOLD);
  }

  /**
   * 使用自定义阈值创建压缩器。
   *
   * @param threshold 压缩阈值（字节），超过此值才压缩
   */
  public MessageCompressor(int threshold) {
    this.threshold = threshold;
  }

  // ==================== 静态方法（默认阈值） ====================

  /**
   * 压缩消息体（超过默认阈值 1KB 时）。
   *
   * @param body 原始消息体
   * @return 压缩后的消息体（带 {@code GZIP:} 前缀），或原始消息体（未超阈值）
   */
  public static String compressIfNeeded(String body) {
    return compressIfNeeded(body, DEFAULT_COMPRESS_THRESHOLD);
  }

  /**
   * 解压消息体（如果带 {@code GZIP:} 前缀）。
   *
   * @param body 消息体（可能带压缩标记）
   * @return 解压后的原始消息体，或原始输入（无压缩标记）
   */
  public static String decompressIfNeeded(String body) {
    if (body == null || !body.startsWith(COMPRESSED_PREFIX)) {
      return body;
    }
    try {
      String compressed = body.substring(COMPRESSED_PREFIX.length());
      return decompress(compressed);
    } catch (Exception e) {
      log.warn("[Queue-Compress] 解压失败,返回原始消息体: err={}", e.getMessage());
      return body;
    }
  }

  /**
   * 压缩消息体（超过指定阈值时）。
   *
   * @param body 原始消息体
   * @param threshold 压缩阈值（字节）
   * @return 压缩后的消息体或原始消息体
   */
  public static String compressIfNeeded(String body, int threshold) {
    if (body == null || body.length() < threshold) {
      return body;
    }
    try {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
        gzip.write(bytes);
      }
      String compressed = Base64.getEncoder().encodeToString(baos.toByteArray());
      String result = COMPRESSED_PREFIX + compressed;
      log.debug("[Queue-Compress] 压缩: original={}B compressed={}B", bytes.length, result.length());
      return result;
    } catch (IOException e) {
      log.warn("[Queue-Compress] 压缩失败,返回原始消息体: err={}", e.getMessage());
      return body;
    }
  }

  // ==================== 实例方法（自定义阈值） ====================

  /**
   * 根据实例阈值决定是否压缩消息。
   *
   * @param data 原始消息字符串
   * @return 压缩后的消息或原始消息
   */
  public String compressInstance(String data) {
    return compressIfNeeded(data, threshold);
  }

  /**
   * 判断消息是否已压缩。
   *
   * @param body 消息体
   * @return true 表示已压缩
   */
  public static boolean isCompressed(String body) {
    return body != null && body.startsWith(COMPRESSED_PREFIX);
  }

  // ==================== 内部方法 ====================

  /** GZIP 压缩 + Base64 编码。 */
  private static String compress(String data) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
      gzip.write(data.getBytes(StandardCharsets.UTF_8));
    }
    return COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(bos.toByteArray());
  }

  /** Base64 解码 + GZIP 解压。 */
  private static String decompress(String base64) throws IOException {
    byte[] compressed = Base64.getDecoder().decode(base64);
    try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        GZIPInputStream gzip = new GZIPInputStream(bis)) {
      return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
