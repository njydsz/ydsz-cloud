package com.njydsz.common.excel.core.reader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.support.cache.LRUCache;

/**
 * 分块加载的 SST（Shared Strings Table）缓存表。
 *
 * <p>Excel .xlsx 文件中的 SharedStrings.xml 存储了所有共享字符串， 单元格类型为 {@code t="s"} 时通过索引引用此表。当 Excel
 * 文件包含大量字符串时， 全量加载 SST 会消耗大量内存，本类据此实现了两种加载策略：
 *
 * <h3>加载策略</h3>
 *
 * <ul>
 *   <li><b>简单模式</b>（totalSize &lt; 5MB）：全量读入 byte[]，预扫描 {@code <si>} 标签 建立偏移量索引，查询时直接在内存中切片
 *   <li><b>分块模式</b>（totalSize &ge; 5MB）：将数据落到临时文件并用 {@link RandomAccessFile} 持有句柄，仅在内存中保留偏移量与长度索引
 * </ul>
 *
 * <h3>实例管理</h3>
 *
 * <p>通过 {@link ConcurrentHashMap} 按文件路径缓存实例，避免重复解析同一文件的 SST。 实例常驻直到显式调用 {@link #clearCache(String)}
 * 或 {@link #clearAllCache()}， 长期运行的服务若持续处理不同文件需主动清理，否则实例表会无界增长。
 *
 * <h3>注意事项</h3>
 *
 * <ul>
 *   <li><b>线程安全性</b>：实例表本身线程安全，但单个实例的 {@code parse} / {@code close} 与内部 LRU
 *       缓存均无同步保护，同一文件不应被多线程并发解析。
 *   <li>分块模式会在系统临时目录生成 {@code sst_chunk_*.tmp}，已注册 {@code deleteOnExit}，但仅在 JVM 正常退出时才会清理。
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SharedStringsReader
 * @see LRUCache
 */
public class ChunkedSSTTable {

  private static final Logger LOG = LoggerFactory.getLogger(ChunkedSSTTable.class);

  /** 分块模式阈值：SST 数据超过 5MB 时启用分块加载 */
  private static final int CHUNK_THRESHOLD = 5 * 1024 * 1024;

  /** LRU 缓存容量：缓存最近访问的 2000 个字符串 */
  private static final int CACHE_SIZE = 2000;

  /** LRU 缓存：字符串索引 → 字符串值 */
  private final LRUCache<Integer, String> cache;

  /** 每个字符串在文件中的偏移量（分块模式使用） */
  private long[] offsets;

  /** 每个字符串的字节长度（分块模式使用） */
  private int[] lengths;

  /** 随机访问文件句柄（分块模式使用） */
  private RandomAccessFile raf;

  /** SST 中的字符串总数 */
  private int totalStrings;

  /** 是否为简单模式（全量内存） */
  private boolean isSimpleMode;

  /** 简单模式下的完整 SST 字节数据 */
  private byte[] simpleStrings;

  /** 按文件路径缓存的实例表 */
  private static final ConcurrentHashMap<String, ChunkedSSTTable> instances =
      new ConcurrentHashMap<>();

  /**
   * 获取指定文件路径的 SST 实例（单例模式）。
   *
   * @param filePath SST 文件路径
   * @return 缓存或新建的 ChunkedSSTTable 实例
   */
  public static ChunkedSSTTable getInstance(String filePath) {
    return instances.computeIfAbsent(filePath, k -> new ChunkedSSTTable());
  }

  /**
   * 清除指定文件路径的 SST 实例缓存。
   *
   * @param filePath SST 文件路径
   */
  public static void clearCache(String filePath) {
    instances.remove(filePath);
  }

  /** 清除所有 SST 实例缓存。 */
  public static void clearAllCache() {
    instances.clear();
  }

  private ChunkedSSTTable() {
    this.cache = new LRUCache<>(CACHE_SIZE);
  }

  /**
   * 解析 SST 输入流。
   *
   * <p>根据数据大小自动选择加载策略：
   *
   * <ul>
   *   <li>totalSize < {@value #CHUNK_THRESHOLD} → {@link #parseSimpleMode}（全量内存）
   *   <li>totalSize ≥ {@value #CHUNK_THRESHOLD} → {@link #parseChunkedMode}（分块随机访问）
   * </ul>
   *
   * @param sstStream SST XML 输入流
   * @param totalSize SST 数据总大小（字节）
   * @throws IOException 读取异常
   */
  public void parse(InputStream sstStream, long totalSize) throws IOException {
    if (totalSize < CHUNK_THRESHOLD) {
      parseSimpleMode(sstStream);
    } else {
      parseChunkedMode(sstStream);
    }
  }

  /**
   * 简单模式：全量读入内存。
   *
   * <p>将 SST XML 全量读入 byte[]，预扫描所有 {@code <si>} 标签建立偏移量索引。 后续查询直接在内存中按索引定位。
   *
   * @param sstStream SST XML 输入流
   * @throws IOException 读取异常
   */
  private void parseSimpleMode(InputStream sstStream) throws IOException {
    isSimpleMode = true;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = sstStream.read(buffer)) != -1) {
      baos.write(buffer, 0, read);
    }
    simpleStrings = baos.toByteArray();

    int tempCount = 0;
    int tempPos = 0;
    while (tempPos < simpleStrings.length) {
      tempPos = indexOf(simpleStrings, "<si>", tempPos);
      if (tempPos == -1) {
        break;
      }
      tempPos += 4;
      tempCount++;
    }
    offsets = new long[tempCount];
    lengths = new int[tempCount];

    int count = 0;
    int pos = 0;
    while (pos < simpleStrings.length) {
      int tagStart = indexOf(simpleStrings, "<si>", pos);
      if (tagStart == -1) {
        break;
      }
      int tagEnd = indexOf(simpleStrings, "</si>", tagStart);
      if (tagEnd == -1) {
        break;
      }

      int contentStart = tagStart + 5;
      int contentEnd = findContentEnd(simpleStrings, contentStart, tagEnd);

      int stringIndex = count;

      int idx = indexOf(simpleStrings, "\" t=\"", tagStart);
      if (idx != -1 && idx < tagStart + 50) {
        int eqIdx = indexOf(simpleStrings, "\"", idx + 5);
        if (eqIdx != -1) {
          String numStr =
              new String(Arrays.copyOfRange(simpleStrings, idx + 5, eqIdx), StandardCharsets.UTF_8);
          try {
            stringIndex = Integer.parseInt(numStr);
          } catch (NumberFormatException e) {
            stringIndex = count;
          }
        }
      }

      if (stringIndex >= offsets.length) {
        stringIndex = count;
      }

      offsets[stringIndex] = contentStart;
      lengths[stringIndex] = contentEnd - contentStart;

      pos = tagEnd + 6;
      count++;
    }
    totalStrings = count;
    LOG.debug("SST解析完成: {} 个字符串, 简单模式", totalStrings);
  }

  private int findContentEnd(byte[] data, int start, int maxEnd) {
    int pos = start;
    while (pos < maxEnd) {
      if (data[pos] == '<') {
        if (pos + 3 < maxEnd
            && data[pos + 1] == 't'
            && data[pos + 2] == '/'
            && data[pos + 3] == '>') {
          return pos;
        }
        int closeTag = indexOf(data, "</t>", pos);
        if (closeTag != -1 && closeTag < maxEnd) {
          return pos;
        }
      }
      pos++;
    }
    return maxEnd;
  }

  private void parseChunkedMode(InputStream sstStream) throws IOException {
    isSimpleMode = false;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = sstStream.read(buffer)) != -1) {
      baos.write(buffer, 0, read);
    }
    byte[] data = baos.toByteArray();

    int countTag = indexOf(data, "count=\"");
    if (countTag == -1) {
      countTag = indexOf(data, "uniqueCount=\"");
    }
    if (countTag != -1) {
      int start = countTag + 7;
      int end = indexOf(data, "\"", start);
      if (end != -1) {
        try {
          totalStrings =
              Integer.parseInt(
                  new String(Arrays.copyOfRange(data, start, end), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
          totalStrings = 10000;
        }
      }
    }

    offsets = new long[totalStrings];
    lengths = new int[totalStrings];

    File tempFile = File.createTempFile("sst_chunk_", ".tmp");
    tempFile.deleteOnExit();
    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
      fos.write(data);
    }
    raf = new RandomAccessFile(tempFile, "r");

    int pos = 0;
    int stringIndex = 0;
    while (pos < data.length && stringIndex < totalStrings) {
      int siTag = indexOf(data, "<si>", pos);
      if (siTag == -1) {
        break;
      }

      int contentStart = siTag + 5;
      int contentEnd = indexOf(data, "</si>", contentStart);
      if (contentEnd == -1) {
        break;
      }

      int tAttr = indexOf(data, " t=\"", siTag);
      if (tAttr != -1 && tAttr < contentEnd) {
        int quote1 = tAttr + 4;
        int quote2 = indexOf(data, "\"", quote1);
        if (quote2 != -1 && quote2 < contentEnd) {
          String t = new String(Arrays.copyOfRange(data, quote1, quote2), StandardCharsets.UTF_8);
          if ("s".equals(t)) {
            int vTag = indexOf(data, "<v>", contentStart);
            if (vTag != -1 && vTag < contentEnd) {
              int vStart = vTag + 3;
              int vEnd = indexOf(data, "</v>", vStart);
              if (vEnd != -1 && vEnd < contentEnd) {
                try {
                  stringIndex =
                      Integer.parseInt(
                          new String(
                              Arrays.copyOfRange(data, vStart, vEnd), StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                  LOG.debug("Caught exception (ignored): {}", e.getMessage());
                }
              }
            }
          }
        }
      }

      offsets[stringIndex] = contentStart;
      lengths[stringIndex] = contentEnd - contentStart;

      pos = contentEnd + 6;
      stringIndex++;
    }

    LOG.debug("SST解析完成: {} 个字符串, 分块模式", totalStrings);
  }

  /**
   * 按索引取共享字符串。
   *
   * <p>先查 LRU 缓存，未命中再按预建的偏移量/长度从简单模式的字节数组中切片， 并将结果回填缓存。
   *
   * <p><b>降级为空串的情形</b>（均不抛异常，以免单个坏单元格中断整表解析）： 索引为负、越界、尚未 {@code parse} 或已 {@code close}、切片区间非法。
   * 此外<b>分块模式下本方法恒返回空字符串</b>——该分支尚未实现基于 {@link RandomAccessFile} 的按需读取。
   *
   * @param index SST 中的字符串索引，从 0 开始
   * @return 对应字符串；任何异常或越界情形均返回空字符串 {@code ""}，永不为 {@code null}
   */
  public String getString(int index) {
    if (index < 0 || offsets == null || index >= offsets.length) {
      return "";
    }

    String cached = cache.get(index);
    if (cached != null) {
      return cached;
    }

    String str;
    if (isSimpleMode && simpleStrings != null) {
      int start = (int) offsets[index];
      int len = lengths[index];
      if (start >= 0 && len > 0 && start + len <= simpleStrings.length) {
        str =
            new String(
                Arrays.copyOfRange(simpleStrings, start, start + len), StandardCharsets.UTF_8);
      } else {
        str = "";
      }
    } else {
      str = "";
    }

    cache.put(index, str);
    return str;
  }

  private int indexOf(byte[] data, String target) {
    return indexOf(data, target, 0);
  }

  private int indexOf(byte[] data, String target, int start) {
    byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
    outer:
    for (int i = start; i <= data.length - targetBytes.length; i++) {
      for (int j = 0; j < targetBytes.length; j++) {
        if (data[i + j] != targetBytes[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  /**
   * 释放本实例占用的内存与文件句柄。
   *
   * <p>清空 LRU 缓存、置空偏移量/长度索引与简单模式的字节数组，并关闭分块模式的 {@link RandomAccessFile}。关闭句柄时的 {@link IOException}
   * 被有意吞掉（仅 debug 日志）， 因为释放阶段的失败不应影响主流程。
   *
   * <p><b>调用后状态</b>：实例不可再使用，{@link #getString(int)} 将恒定返回空字符串； 但实例仍留在静态实例表中，需另行调用 {@link
   * #clearCache(String)} 摘除， 否则后续 {@link #getInstance(String)} 会取到这个已关闭的对象。
   */
  public void close() {
    cache.clear();
    offsets = null;
    lengths = null;
    simpleStrings = null;
    if (raf != null) {
      try {
        raf.close();
      } catch (IOException e) {
        LOG.debug("Caught exception (ignored): {}", e.getMessage());
      }
      raf = null;
    }
  }
}
