package com.njydsz.nextwiki.server.service;

import java.io.InputStream;

import com.njydsz.common.file.virus.VirusScanner;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.server.config.NextwikiProperties;

/**
 * 病毒扫描应用服务
 *
 * <p>委托 ydsz-common-file 的 {@link VirusScanner} SPI（{@code ClamAV} 实现）进行病毒扫描，
 * 并在 API 层保留「跳过 / 错误」语义以兼容现有业务逻辑。
 *
 * <p><b>SPI 集成：</b>实际的 ClamAV TCP/INSTREAM 协议逻辑下沉到
 * {@code com.njydsz.common.file.virus.ClamAvVirusScanner}，本类仅做 NextWiki 特定前置校验
 * （文件大小限制 / 开关控制）和结果适配。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirusScanApplicationService {

  private final NextwikiProperties properties;
  private final VirusScanner virusScanner;

  /** 最大文件大小限制（100MB） */
  private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

  /**
   * 扫描输入流（门面方法：前置校验 + 委托 {@link VirusScanner#scan} + 异常兜底）。   *
   * <p>未启用 / 文件超过 {@link #MAX_FILE_SIZE} 时返回 skipped；扫描异常被捕获并返回 error，不向上抛。
   *
   * @param inputStream 文件输入流（方法内读取，调用方负责关闭）
   * @param fileSize 文件大小（字节），用于超限快速跳过
   * @return 扫描结果 {@link ScanResult}（clean/infected/skipped/error）
   * @complexity 正常为一次 ClamAV TCP 往返（O(fileSize) 流式传输）；超限/未启用为 O(1)
   * @note 本方法不抛异常，调用方需按 {@link ScanResult#isInfected()} 决策
   * @concurrency 无共享可变状态，线程安全；每次扫描由 ClamAV 新建独立 Socket
   */
  public ScanResult scan(InputStream inputStream, long fileSize) {
    if (!properties.getVirusScan().isEnabled()) {
      return ScanResult.skipped("病毒扫描未启用");
    }

    if (fileSize > MAX_FILE_SIZE) {
      log.warn("[VirusScanApplicationService] 文件超过大小限制，跳过扫描: size={}", fileSize);
      return ScanResult.skipped("文件超过大小限制");
    }

    try {
      VirusScanner.ScanResult result = virusScanner.scan(inputStream, null);
      return switch (result) {
        case CLEAN -> ScanResult.clean();
        case INFECTED -> ScanResult.infected("unknown");
        case ERROR -> ScanResult.error("扫描过程发生错误");
      };
    } catch (Exception e) {
      log.error("[VirusScanApplicationService] 病毒扫描异常", e);
      return ScanResult.error("扫描异常: " + e.getMessage());
    }
  }

  /** 扫描结果 */
  @Data
  @Builder
  public static class ScanResult {
    /** 是否扫描通过（无病毒） */
    private boolean clean;

    /** 是否检出病毒 */
    private boolean infected;

    /** 是否跳过（未启用或超限） */
    private boolean skipped;

    /** 是否扫描出错（连接/协议异常） */
    private boolean error;

    /** 结果描述（OK / 病毒名 / 跳过原因 / 错误信息） */
    private String message;

    /**
     * 构造「扫描通过」结果，即 ClamAV 明确回复 OK。
     *
     * <p>四个状态位中<b>只有本结果</b>代表文件确已被查杀引擎放行。 {@code skipped} 与 {@code error} 均<b>未</b>完成实际查杀，调用方 若以「非
     * infected 即安全」做判断会放过未扫描文件，务必显式判 {@code clean}。
     *
     * @return 通过结果，{@code clean=true}、{@code message="OK"}
     */
    public static ScanResult clean() {
      return ScanResult.builder().clean(true).message("OK").build();
    }

    /**
     * 构造「检出病毒」结果。
     *
     * <p>命中即应阻断上传并留档告警。message 统一格式化为 {@code "FOUND: {virusName}"}，与 ClamAV 原始应答保持一致，便于日志检索与对账。
     *
     * @param virusName ClamAV 回报的病毒特征名，从应答行中截取
     * @return 检出结果，{@code infected=true}
     */
    public static ScanResult infected(String virusName) {
      return ScanResult.builder().infected(true).message("FOUND: " + virusName).build();
    }

    /**
     * 构造「跳过扫描」结果。
     *
     * <p>用于扫描开关关闭、或文件超过 {@link VirusScanApplicationService#MAX_FILE_SIZE} 这类<b>主动放弃</b>的场景——超大文件走
     * ClamAV INSTREAM 会长时间占用连接， 故以放行换吞吐。此时文件<b>未经查杀</b>，安全等级由上层策略自行决定。
     *
     * @param reason 跳过原因（如 {@code "病毒扫描未启用"}、{@code "文件超过大小限制"}）
     * @return 跳过结果，{@code skipped=true}
     */
    public static ScanResult skipped(String reason) {
      return ScanResult.builder().skipped(true).message(reason).build();
    }

    /**
     * 构造「扫描出错」结果。
     *
     * <p>对应 ClamAV 连接失败、协议应答无法识别等异常；异常已在 {@link VirusScanApplicationService#scan}
     * 内被吞掉转为本结果，<b>不向上抛出</b>， 避免查杀服务抖动直接压垮上传链路。同样属于「未完成查杀」，不可当作安全。
     *
     * @param message 错误描述，含原始应答或异常摘要
     * @return 错误结果，{@code error=true}
     */
    public static ScanResult error(String message) {
      return ScanResult.builder().error(true).message(message).build();
    }
  }
}
