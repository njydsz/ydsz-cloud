package com.njydsz.system.server.support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.web.multipart.MultipartFile;

/**
 * 内存字节数组形式的 {@link MultipartFile} 适配器。
 *
 * <p>{@code IFileStorage} 的上传入口以 {@link MultipartFile} 为参数，而前端 sourcemap
 * 上报走的是 {@code application/octet-stream} 原始字节流（由 {@code bash/upload-sourcemaps.mjs}
 * 以 fetch body 直发），不构成 multipart 表单，Spring 无法绑定为 {@code MultipartFile}。
 * 本适配器把已读入内存的报文字节包装为 {@code MultipartFile}，使服务层得以复用既有
 * 对象存储能力，避免为监控场景另建一套上传通道。
 *
 * <p><b>内存约束：</b>sourcemap 单价通常为数百 KB 至数 MB，调用方（Controller）在读取
 * 报文前已校验长度上限，本类不再做二次限制；构造时复制入参数组，避免调用方后续复用
 * 同一缓冲区导致内容被改写。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public class ByteArrayMultipartFile implements MultipartFile {

  /** 无表单场景下占位的表单字段名 */
  private static final String DEFAULT_PART_NAME = "file";

  /** 默认内容类型（二进制流） */
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

  /** sourcemap 内容类型（sourcemap 本身是 JSON 文本） */
  private static final String SOURCEMAP_CONTENT_TYPE = "application/json";

  /** sourcemap 文件扩展名 */
  private static final String SOURCEMAP_EXTENSION = ".map";

  /** 表单字段名（本场景无真实表单，固定占位） */
  private final String name;

  /** 原始文件名 */
  private final String originalFilename;

  /** 内容类型 */
  private final String contentType;

  /** 文件字节内容（构造时已复制） */
  private final byte[] content;

  /**
   * 构造字节数组形式的 MultipartFile。
   *
   * @param originalFilename 原始文件名，为 {@code null} 时回退为 {@code file}
   * @param content 文件字节内容，为 {@code null} 时按空文件处理
   */
  public ByteArrayMultipartFile(String originalFilename, byte[] content) {
    this.name = DEFAULT_PART_NAME;
    this.originalFilename =
        originalFilename == null || originalFilename.isEmpty() ? DEFAULT_PART_NAME : originalFilename;
    this.content = content == null ? new byte[0] : content.clone();
    this.contentType =
        this.originalFilename.endsWith(SOURCEMAP_EXTENSION)
            ? SOURCEMAP_CONTENT_TYPE
            : DEFAULT_CONTENT_TYPE;
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
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(content);
  }

  @Override
  public void transferTo(File dest) throws IOException, IllegalStateException {
    Files.write(dest.toPath(), content);
  }
}
