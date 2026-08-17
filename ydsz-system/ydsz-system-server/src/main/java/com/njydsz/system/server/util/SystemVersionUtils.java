package com.njydsz.system.server.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 版本号生成工具（P2：版本号可读化）。
 *
 * <p>替代原先 {@code "v" + System.currentTimeMillis()} 的时间戳版本号——原实现并发下可能重复、且不可读。
 * 新版本号格式 {@code vyyyyMMdd-HHmmss-SSS}：按创建时间排序即版本先后，便于审计与回滚定位。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public final class SystemVersionUtils {

  /** 版本号时间格式（精确到毫秒，保证单实例内单调可读） */
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  private SystemVersionUtils() {
    // 工具类禁止实例化
  }

  /**
   * 生成下一个可读版本号。
   *
   * @return 形如 {@code v20260817-103045-123} 的版本号
   */
  public static String nextVersion() {
    return "v" + LocalDateTime.now().format(FORMATTER);
  }
}
