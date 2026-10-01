package com.njydsz.common.file.virus;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 ClamAV 的病毒扫描器实现（ydsz-common-file 内置可选实现）。
 *
 * <p>通过 TCP 连接 ClamAV daemon（默认端口 3310）并发送 INSTREAM 命令进行流式病毒扫描。
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * @Configuration
 * public class VirusScanConfig {
 *     @Bean
 *     @Primary
 *     public VirusScanner clamAvVirusScanner(VirusScanConnectionProperties props) {
 *         return new ClamAvVirusScanner(props.getHost(), props.getPort(),
 *             props.getMaxFileSize(), props.isEnabled());
 *     }
 * }
 * }</pre>
 *
 * <p>注册为 Spring Bean 后，{@code FileConfiguration} 通过 {@code @ConditionalOnMissingBean} 检测到已有实现，
 * 自动跳过 {@link NoOpVirusScanner} 装配。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see VirusScanner
 * @see NoOpVirusScanner
 */
@Slf4j
public class ClamAvVirusScanner implements VirusScanner {

  /** ClamAV INSTREAM 单次发送分块大小（字节） */
  private static final int CHUNK_SIZE = 4096;

  /** chunk 长度头字节数（INSTREAM 协议为 4 字节大端序） */
  private static final int CHUNK_LENGTH_BYTES = 4;

  private final String host;
  private final int port;
  private final long maxFileSize;
  private final boolean enabled;

  /**
   * 构造 ClamAV 扫描器。
   *
   * @param host ClamAV daemon 主机名 / IP
   * @param port ClamAV daemon 端口（默认 3310）
   * @param maxFileSize 文件大小上限（字节），超过此值跳过扫描
   * @param enabled 是否启用病毒扫描
   */
  public ClamAvVirusScanner(String host, int port, long maxFileSize, boolean enabled) {
    this.host = host;
    this.port = port;
    this.maxFileSize = maxFileSize;
    this.enabled = enabled;
  }

  /**
   * 扫描文件内容是否包含病毒（实现 {@link VirusScanner#scan} 接口）。
   *
   * <p>扫描器未启用、文件超过大小上限时返回 {@link ScanResult#ERROR}（标注跳过原因）；
   * 扫描过程抛异常返回 {@link ScanResult#ERROR}，不向上抛出。
   *
   * @param inputStream 文件输入流；本方法会完整读取并关闭流
   * @param fileName 原始文件名（日志定位用）
   * @return 扫描结果：CLEAN（安全）/ INFECTED（检出病毒）/ ERROR（异常跳过）
   */
  @Override
  public ScanResult scan(InputStream inputStream, String fileName) {
    if (!enabled) {
      log.debug("[ClamAvVirusScanner] 病毒扫描未启用，跳过: fileName={}", fileName);
      return ScanResult.ERROR;
    }

    try {
      return doScan(inputStream);
    } catch (Exception e) {
      log.error("[ClamAvVirusScanner] 病毒扫描异常: fileName={}", fileName, e);
      return ScanResult.ERROR;
    }
  }

  /** ClamAV INSTREAM 扫描核心逻辑 */
  private ScanResult doScan(InputStream inputStream) throws IOException {
    try (Socket socket = new Socket(host, port)) {
      var out = socket.getOutputStream();
      var in = socket.getInputStream();

      // 发送 INSTREAM 命令（z 前缀表示压缩传输，这里实际未压缩，仅作协议标识）
      out.write(buildZCommand("zINSTREAM\u0000"));
      out.flush();

      // 流式发送文件内容
      byte[] buffer = new byte[CHUNK_SIZE];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        byte[] chunkSize =
            ByteBuffer.allocate(CHUNK_LENGTH_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(bytesRead)
                .array();
        out.write(chunkSize);
        out.write(buffer, 0, bytesRead);
      }

      // 发送结束标记（0 长度 chunk）
      out.write(
          ByteBuffer.allocate(CHUNK_LENGTH_BYTES)
              .order(ByteOrder.BIG_ENDIAN)
              .putInt(0)
              .array());
      out.flush();

      // 读取响应
      byte[] response = in.readAllBytes();
      String result = new String(response).trim();

      if (result.contains("OK")) {
        log.info("[ClamAvVirusScanner] 扫描通过: {}", result);
        return ScanResult.CLEAN;
      } else if (result.contains("FOUND")) {
        String virusName = result.replaceAll(".*FOUND: ", "").trim();
        log.warn("[ClamAvVirusScanner] 检测到病毒: {}", virusName);
        return ScanResult.INFECTED;
      } else {
        log.error("[ClamAvVirusScanner] 扫描错误: {}", result);
        return ScanResult.ERROR;
      }
    }
  }

  /** 构建 ClamAV z 命令字节 */
  private static byte[] buildZCommand(String command) {
    byte[] cmdBytes = command.getBytes();
    byte[] result = new byte[cmdBytes.length];
    System.arraycopy(cmdBytes, 0, result, 0, cmdBytes.length);
    return result;
  }

  /**
   * 判断当前扫描器是否可用。
   *
   * <p>本方法仅检查配置开关，{@code true} 表示扫描功能已启用。
   *
   * @return true 表示已启用，false 表示未启用（此时 scan 返回 ERROR）
   */
  @Override
  public boolean isAvailable() {
    return enabled;
  }

  /** 获取最大文件大小限制（字节） */
  public long getMaxFileSize() {
    return maxFileSize;
  }
}
