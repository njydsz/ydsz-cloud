package com.njydsz.system.server.util;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 版本号生成工具（P2：版本号可读化 + 唯一化）。
 *
 * <p>版本号格式 {@code vyyyyMMdd-HHmmss-SSS-NNNNNN}：前半段为创建时间（毫秒精度），保证按创建时间排序即可得版本先后；
 * 后半段为进程内原子递增序号，消除同毫秒并发写入导致的版本号重复。排序时按字符串字典序即可得到时间先后。
 *
 * <p><b>唯一性边界：</b>本工具保证<b>单实例内</b>版本号唯一且单调。跨实例并发写同一资源时，时间戳+序号仍可能碰撞
 * （概率极低），如需全局强唯一，可接入分布式 ID（雪花算法）作为版本号来源。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SystemVersionUtils {

  /** 版本号时间格式（精确到毫秒） */
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  /** 同毫秒去重序号（进程内原子递增，取模保证固定宽度） */
  private static final AtomicLong SEQUENCE = new AtomicLong(0);

  /** 序号取模基数（保证 6 位定宽，碰撞需同毫秒内写入超过 100 万次） */
  private static final long SEQUENCE_MODULO = 1_000_000L;

  private SystemVersionUtils() {
    // 工具类禁止实例化
  }

  /**
   * 生成下一个可读且唯一的版本号。
   *
   * @return 形如 {@code v20260817-103045-123-000042} 的版本号
   */
  public static String nextVersion() {
    long seq = SEQUENCE.incrementAndGet() % SEQUENCE_MODULO;
    return String.format("v%s-%06d", LocalDateTime.now().format(FORMATTER), seq);
  }
}
