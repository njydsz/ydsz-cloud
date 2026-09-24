package com.njydsz.common.excel.core.reader.sax;

/**
 * Sheet XML 读取策略枚举 — 控制大文件的临时存储与解析方式。
 *
 * <p>{@link SuperFastExcelReader} 解析 sheet XML 时需要将 zip 中的条目数据落地后再解析。 本枚举决定落地策略：
 *
 * <ul>
 *   <li>{@link #AUTO}（默认）：根据文件大小自适应（≤ 2MB 走内存缓冲，&gt; 2MB 走内存映射文件）</li>
 *   <li>{@link #MEMORY}：始终使用堆内存字节数组（适合小文件或堆空间充裕场景）</li>
 *   <li>{@link #MAPPED_FILE}：始终使用 {@link java.nio.MappedByteBuffer} 内存映射（适合超大文件，
 *       物理内存占用由 OS 页面调度决定，不占用 JVM 堆）</li>
 *   <li>{@link #DISK}：始终使用磁盘临时文件（旧行为，兼容性最好，I/O 延迟最高）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public enum TempFileStrategy {

  /**
   * 自适应策略（默认）。
   *
   * <p>小文件（≤ 2MB sheet XML）：直接解压输入流到堆内存字节数组，零磁盘 I/O。
   * 大文件（&gt; 2MB）：使用内存映射文件（MappedByteBuffer），物理内存占用由 OS 页面调度控制，
   * 不占用 JVM 堆空间。
   */
  AUTO,

  /**
   * 纯内存策略。
   *
   * <p>始终将 sheet XML 完整读入堆内存字节数组后解析。 速度最快，但大文件内存占用为文件大小的 2-3 倍
   * （字节数组 + 字符串解码），适合堆空间充裕且文件大小可控的场景。
   */
  MEMORY,

  /**
   * 内存映射文件策略。
   *
   * <p>始终使用 {@link java.nio.MappedByteBuffer} 将 sheet XML 临时文件映射到虚拟内存。 解析器按顺序
   * 读取，OS 按需换页加载数据，物理内存占用稳定在几十 MB 级别，不随文件大小线性增长。 适合超大文件
   * （&gt; 100MB sheet XML）或堆空间受限的生产环境。
   */
  MAPPED_FILE,

  /**
   * 磁盘临时文件策略。
   *
   * <p>旧版本行为。始终复制到磁盘临时文件后再以流式方式读取。 速度最慢（双重磁盘 I/O：复制 + 读取），
   * 但兼容性最好，不受 JVM 内存模型与 mmap 限制。
   */
  DISK
}
