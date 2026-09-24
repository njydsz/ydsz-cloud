package com.njydsz.common.excel.core.reader.sax;

import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;

/**
 * 将 {@link MappedByteBuffer} 桥接为 {@link InputStream} 的适配器。
 *
 * <p>用于 SuperFastExcelReader 的 MAPPED_FILE 策略路径：将临时文件通过内存映射（mmap）后，以标准的
 * {@link InputStream} 接口供 {@code SheetXmlReader} 顺序读取。
 *
 * <p>内存映射的优势：
 *
 * <ul>
 *   <li>不占用 JVM 堆空间（映射区在堆外 / 直接内存）</li>
 *   <li>物理内存占用由 OS 页面调度控制，仅加载实际访问的页面（通常 4KB/页）</li>
 *   <li>读取大文件时避免传统流式的"堆内存字节数组 + 内核缓冲区"双重拷贝</li>
 *   <li>OS 对映射文件有 readahead 预读优化，顺序读取性能接近堆内存</li>
 * </ul>
 *
 * <p><b>注意：</b>本 InputStream 不维护独立的 position，直接委托给 MappedByteBuffer 的位置指针。 不可重复调用 {@link #read()} 与其他方法混合使用。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
class MappedByteBufferInputStream extends InputStream {

  private final MappedByteBuffer mappedBuffer;

  /**
   * 构造 MappedByteBufferInputStream。
   *
   * @param mappedBuffer 已映射的只读 MappedByteBuffer（外部负责释放，本实例不关闭 buffer）
   */
  MappedByteBufferInputStream(MappedByteBuffer mappedBuffer) {
    this.mappedBuffer = mappedBuffer;
  }

  @Override
  public int read() throws IOException {
    if (!mappedBuffer.hasRemaining()) {
      return -1;
    }
    return mappedBuffer.get() & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (!mappedBuffer.hasRemaining()) {
      return -1;
    }
    int remaining = mappedBuffer.remaining();
    int toRead = Math.min(len, remaining);
    mappedBuffer.get(b, off, toRead);
    return toRead;
  }

  @Override
  public int available() throws IOException {
    return mappedBuffer.remaining();
  }

  /** MappedByteBuffer 不支持 mark/reset。 */
  @Override
  public boolean markSupported() {
    return false;
  }
}
