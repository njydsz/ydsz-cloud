package com.njydsz.common.netty.transfer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.stream.ChunkedFile;
import lombok.extern.slf4j.Slf4j;

/**
 * 零拷贝文件传输工具 — 封装 Netty 的 {@link DefaultFileRegion} 和 {@link ChunkedFile}，提供高效的大文件传输能力。
 *
 * <p>零拷贝（Zero-Copy）通过底层 sendfile 系统调用来避免用户态-内核态数据拷贝， 比传统 {@code byte[]} 循环读写提升 3-10 倍吞吐量。
 *
 * <h3>使用模式</h3>
 *
 * <ul>
 *   <li><b>完整文件传输</b>：使用 {@link #sendFile(Channel, File)} 直接发送整个文件</li>
 *   <li><b>断点续传</b>：使用 {@link #sendFile(Channel, File, long, long)} 从指定偏移发送</li>
 *   <li><b>Chunked 流式传输</b>：使用 {@link #sendChunked(Channel, File)} 流式分块发送（适用于限速场景）</li>
 * </ul>
 *
 * <h3>注意事项</h3>
 *
 * <ul>
 *   <li>Epoll 传输模式下 {@link DefaultFileRegion} 不触发 {@code writeComplete} 事件，监听 ChannelFuture 即可</li>
 *   <li>Windows 上 {@link DefaultFileRegion} 可能退化为传统写入模式（Netty 自动适配）</li>
 *   <li>传输完成后需确保目标 Channel 正确关闭或返回池中</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public final class ZeroCopyFileTransfer {

  /** ChunkedFile 默认分块大小（64KB） */
  private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

  private ZeroCopyFileTransfer() {
    throw new UnsupportedOperationException("工具类不可实例化");
  }

  /**
   * 完整发送文件（零拷贝）。
   *
   * <p>等价于 {@code sendFile(channel, file, 0, file.length())}。
   *
   * @param channel 目标 Channel
   * @param file 待发送的文件
   * @return ChannelFuture
   * @throws IOException 文件打开失败时抛出
   */
  public static ChannelFuture sendFile(Channel channel, File file) throws IOException {
    return sendFile(channel, file, 0, file.length());
  }

  /**
   * 从文件指定偏移量开始发送（支持断点续传）。
   *
   * @param channel 目标 Channel
   * @param file 待发送的文件
   * @param offset 起始偏移量（字节）
   * @param length 发送长度（字节）
   * @return ChannelFuture
   * @throws IOException 文件打开失败时抛出
   */
  public static ChannelFuture sendFile(Channel channel, File file, long offset, long length)
      throws IOException {
    if (!file.exists() || !file.isFile()) {
      throw new IOException("文件不存在或非普通文件: " + file.getAbsolutePath());
    }
    if (offset < 0 || length < 0 || offset + length > file.length()) {
      throw new IllegalArgumentException(
          String.format("无效范围: offset=%d, length=%d, fileSize=%d", offset, length, file.length()));
    }

    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      FileChannel fileChannel = raf.getChannel();
      DefaultFileRegion region = new DefaultFileRegion(fileChannel, offset, length);

      ChannelFuture future = channel.writeAndFlush(region);
      future.addListener(
          (ChannelFutureListener)
              f -> {
                try {
                  raf.close();
                } catch (IOException e) {
                  log.warn("[ZeroCopy] 关闭文件异常: {}", e.getMessage());
                }
                if (f.isSuccess()) {
                  log.info("[ZeroCopy] 文件发送成功: {}, bytes={}", file.getName(), length);
                } else {
                  log.error("[ZeroCopy] 文件发送失败: {}, reason={}", file.getName(), f.cause().getMessage());
                }
              });
      return future;
    } catch (Exception e) {
      raf.close();
      throw new IOException("零拷贝文件传输异常: " + file.getAbsolutePath(), e);
    }
  }

  /**
   * 流式分块发送文件（适用于限速或进度回调场景）。
   *
   * <p>使用 {@link ChunkedFile} 将文件分块写入 Pipeline，每个块大小 64KB。 配合 {@link
   * io.netty.handler.traffic.ChannelTrafficShapingHandler} 可实现带宽限速。
   *
   * @param channel 目标 Channel
   * @param file 待发送的文件
   * @return ChannelFuture（最后一个 chunk 的 future）
   * @throws IOException 文件打开失败时抛出
   */
  public static ChannelFuture sendChunked(Channel channel, File file) throws IOException {
    return sendChunked(channel, file, 0, file.length(), DEFAULT_CHUNK_SIZE);
  }

  /**
   * 流式分块发送文件（自定义分块大小）。
   *
   * @param channel 目标 Channel
   * @param file 待发送的文件
   * @param offset 起始偏移量
   * @param length 发送长度
   * @param chunkSize 每个 chunk 的字节数
   * @return ChannelFuture
   * @throws IOException 文件打开失败时抛出
   */
  public static ChannelFuture sendChunked(
      Channel channel, File file, long offset, long length, int chunkSize) throws IOException {
    if (!file.exists() || !file.isFile()) {
      throw new IOException("文件不存在或非普通文件: " + file.getAbsolutePath());
    }
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("分块大小必须 > 0: " + chunkSize);
    }

    RandomAccessFile raf = new RandomAccessFile(file, "r");
    try {
      ChunkedFile chunkedFile = new ChunkedFile(raf, offset, length, chunkSize);

      ChannelFuture future = channel.writeAndFlush(chunkedFile);
      future.addListener(
          (ChannelFutureListener)
              f -> {
                try {
                  raf.close();
                } catch (IOException e) {
                  log.warn("[ZeroCopy] 关闭文件异常: {}", e.getMessage());
                }
                if (!f.isSuccess()) {
                  log.error("[ZeroCopy] 分块发送失败: {}, reason={}", file.getName(), f.cause().getMessage());
                }
              });
      return future;
    } catch (Exception e) {
      raf.close();
      throw new IOException("分块文件传输异常: " + file.getAbsolutePath(), e);
    }
  }

  /**
   * 计算文件传输的断点续传偏移量（简易校验：通过已传输字节数组比较）。
   *
   * <p>此方法为辅助工具，服务端可用于确认客户端请求的断点是否与已存储文件大小一致。
   *
   * @param file 文件
   * @param requestedOffset 请求的偏移量
   * @return 有效的起始偏移量（如果 requestedOffset > fileLength，返回 fileLength）
   */
  public static long validateResumeOffset(File file, long requestedOffset) {
    if (requestedOffset < 0) {
      return 0;
    }
    long fileLength = file.exists() ? file.length() : 0;
    return Math.min(requestedOffset, fileLength);
  }
}
