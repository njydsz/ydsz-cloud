package com.njydsz.common.file.storage;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件内容多次复用缓冲器
 *
 * <p>将上传文件内容缓冲为可多次打开 {@link InputStream} 的内容源：
 *
 * <ul>
 *   <li>文件大小不超过阈值时走内存缓冲（字节数组），多次复用高效
 *   <li>超过阈值时落盘到临时文件，避免大文件全量读入内存导致 OOM
 * </ul>
 *
 * <p><b>使用场景：</b>同一上传文件需经历秒传校验、病毒扫描、对象存储上传等多个处理阶段， 复用同一内容源避免重复读取 IO。
 *
 * <p><b>资源释放：</b>本接口继承 {@link AutoCloseable}，必须在 {@code try-with-resources} 中使用，确保临时文件被清理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FileContentBuffer extends AutoCloseable {

  /**
   * 打开内容输入流
   *
   * <p>每次调用返回新的 {@link InputStream}，调用方负责关闭返回的流。 可多次调用以支持多阶段处理（秒传校验、病毒扫描、上传等）。
   *
   * @return 内容输入流
   * @throws IOException 打开流失败时抛出
   */
  InputStream openStream() throws IOException;

  /**
   * 释放底层资源
   *
   * <p>内存实现无需操作；临时文件实现会删除底层文件。可安全多次调用。
   */
  @Override
  void close();

  /**
   * 创建适合当前文件大小的 {@link FileContentBuffer} 实例
   *
   * <p>小于等于 {@code memoryBufferThreshold} 时使用内存缓冲；否则使用临时文件缓冲。
   *
   * @param file 待缓冲的上传文件
   * @param memoryBufferThreshold 内存缓冲阈值（字节）
   * @return 文件内容缓冲器
   * @throws IOException 缓冲失败时抛出
   */
  static FileContentBuffer create(MultipartFile file, long memoryBufferThreshold) throws IOException {
    if (file.getSize() <= memoryBufferThreshold) {
      return new InMemoryFileContentBuffer(file.getBytes());
    }
    return new TempFileContentBuffer(file);
  }

  /** 内存缓冲实现（小文件） */
  final class InMemoryFileContentBuffer implements FileContentBuffer {

    private final byte[] bytes;

    InMemoryFileContentBuffer(byte[] bytes) {
      this.bytes = bytes != null ? bytes : new byte[0];
    }

    @Override
    public InputStream openStream() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public void close() {
      // 无需释放底层资源
    }
  }

  /** 临时文件缓冲实现（大文件，避免 OOM） */
  @Slf4j
  final class TempFileContentBuffer implements FileContentBuffer {

    private final Path tempFile;

    TempFileContentBuffer(MultipartFile file) throws IOException {
      this.tempFile = Files.createTempFile("ydsz-upload-", ".tmp");
      file.transferTo(this.tempFile);
    }

    @Override
    public InputStream openStream() throws IOException {
      return new BufferedInputStream(Files.newInputStream(tempFile, StandardOpenOption.READ));
    }

    @Override
    public void close() {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        // 临时文件删除失败不影响业务，交由系统临时目录回收
        log.warn("[FileContentBuffer] failed to delete temp file: {}", tempFile, e);
      }
    }
  }
}
