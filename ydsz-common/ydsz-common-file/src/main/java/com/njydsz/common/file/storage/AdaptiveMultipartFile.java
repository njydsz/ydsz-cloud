package com.njydsz.common.file.storage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.web.multipart.MultipartFile;

/**
 * 字节数组形式的 {@link MultipartFile} 适配器。
 *
 * <p>将已读入内存的字节内容包装为 {@code MultipartFile}，使服务层得以复用 {@link IFileStorage} 的既有上传能力，
 * 避免为非 multipart 来源（如 HTTP 原始字节流、字节数组拼接等）另建一套上传通道。
 *
 * <p><b>内存约束：</b>调用方应在读取报文前已按需校验长度上限，本类不做二次限制；
 * 构造时复制入参数组，避免调用方后续复用同一缓冲区导致内容被改写。
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>application/octet-stream 原始字节流上传（如 sourcemap 上报）</li>
 *   <li>服务间调用时的字节数组传递</li>
 *   <li>测试/Mock 场景快速构造 MultipartFile</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.22
 * @see IFileStorage#upload
 */
public class AdaptiveMultipartFile implements MultipartFile {

  /** 无表单场景下默认的表单字段名 */
  private static final String DEFAULT_PART_NAME = "file";

  /** 默认内容类型（二进制流） */
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  /** 表单字段名（无真实表单时固定占位） */
  private final String name;

  /** 原始文件名 */
  private final String originalFilename;

  /** 内容类型 */
  private final String contentType;

  /** 文件字节内容（构造时已复制） */
  private final byte[] content;

  /**
   * 构造字节数组形式的 MultipartFile（使用默认内容类型和默认字段名）。
   *
   * @param originalFilename 原始文件名，为 {@code null} 或空时回退为 {@code "file"}
   * @param content 文件字节内容，为 {@code null} 时按空文件处理
   */
  public AdaptiveMultipartFile(String originalFilename, byte[] content) {
    this(DEFAULT_PART_NAME, originalFilename, DEFAULT_CONTENT_TYPE, content);
  }

  /**
   * 构造字节数组形式的 MultipartFile（可指定内容类型）。
   *
   * @param originalFilename 原始文件名
   * @param contentType 内容类型（如 {@code "application/json"}、{@code "image/png"}）
   * @param content 文件字节内容
   */
  public AdaptiveMultipartFile(String originalFilename, String contentType, byte[] content) {
    this(DEFAULT_PART_NAME, originalFilename, contentType, content);
  }

  /**
   * 完整构造字节数组形式的 MultipartFile。
   *
   * @param name 表单字段名
   * @param originalFilename 原始文件名
   * @param contentType 内容类型
   * @param content 文件字节内容
   */
  public AdaptiveMultipartFile(
      String name, String originalFilename, String contentType, byte[] content) {
    this.name = name != null ? name : DEFAULT_PART_NAME;
    this.originalFilename =
        (originalFilename != null && !originalFilename.isEmpty()) ? originalFilename : DEFAULT_PART_NAME;
    this.contentType = contentType != null ? contentType : DEFAULT_CONTENT_TYPE;
    this.content = content != null ? content.clone() : new byte[0];
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getOriginalFilename() {
    return originalFilename;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isEmpty() {
    return content.length == 0;
  }

  @Override
  public long getSize() {
    return content.length;
  }

  @Override
  public byte[] getBytes() {
    return content.clone();
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public void transferTo(File dest) throws IOException, IllegalStateException {
    Files.write(dest.toPath(), content);
  }
}
