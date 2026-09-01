package com.njydsz.common.file.virus;

import java.io.InputStream;

/**
 * 文件病毒扫描接口
 *
 * <p>定义文件上传时的病毒检测能力，支持多种病毒扫描引擎实现（如 ClamAV、ICAP 等）。 默认注册 {@link NoOpVirusScanner}
 * 作为空操作实现，业务方可替换为实际扫描引擎。
 *
 * <p><b>使用方式：</b>
 *
 * <ul>
 *   <li>实现本接口并注册为 Spring Bean 即可自动替换默认空操作实现
 *   <li>若无自定义实现，系统使用 {@link NoOpVirusScanner}，所有文件视为 CLEAN
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see NoOpVirusScanner
 */
public interface VirusScanner {

  /**
   * 扫描文件内容是否包含病毒
   *
   * @param inputStream 待扫描的文件输入流（由调用方负责关闭）
   * @param fileName 原始文件名（用于日志记录和类型判断）
   * @return 扫描结果
   */
  ScanResult scan(InputStream inputStream, String fileName);

  /**
   * 判断当前病毒扫描引擎是否可用
   *
   * @return true 表示可用，false 表示不可用（此时将跳过扫描）
   */
  boolean isAvailable();

  /** 病毒扫描结果枚举 */
  enum ScanResult {
    /** 文件安全，未检测到病毒 */
    CLEAN,
    /** 检测到病毒/恶意软件 */
    INFECTED,
    /** 扫描过程发生错误，无法确定是否安全 */
    ERROR
  }
}
