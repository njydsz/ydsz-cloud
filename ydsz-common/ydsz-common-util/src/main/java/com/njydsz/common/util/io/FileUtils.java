package com.njydsz.common.util.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import com.njydsz.common.util.api.Experimental;

/**
 * 文件操作工具类
 *
 * <p>封装 Apache Commons IO 提供便捷的文件读写、目录操作、扩展名解析等能力。 所有 IO 异常统一转换为 {@link UncheckedIOException}
 * 向上抛出并附带路径上下文， 便于调用方感知失败原因（遵循云顶编码规范 11 章：禁止吞异常）。
 *
 * <p>统一使用 UTF-8 字符编码进行文本读写。
 *
 * <p>注意：本类与 {@code org.apache.commons.io.FileUtils} 同名，类内对 Commons IO FileUtils 的调用一律使用全限定名（并在行末标注
 * FQN-OK），避免同名类遮蔽导致编译冲突。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("零采用；常规文件操作建议优先使用 JDK NIO Files API")
@Slf4j
public final class FileUtils {

  /** 文件扩展名分隔符 */
  private static final String EXTENSION_SEPARATOR = ".";

  /** 最后一个点号的索引基准 */
  private static final int LAST_DOT_NOT_FOUND = -1;

  /** 文件大小不存在时的返回值 */
  private static final long SIZE_NOT_EXIST = -1L;

  private FileUtils() {
    throw new UnsupportedOperationException(
        "FileUtils is a utility class and cannot be instantiated");
  }

  // ==================== 文件读写 ====================

  /**
   * 读取文件内容为 UTF-8 字符串。
   *
   * @param path 文件路径，不能为 null
   * @return 文件内容字符串
   * @throws NullPointerException 如果 path 为 null
   * @throws UncheckedIOException 读取失败时抛出（附带文件路径上下文）
   */
  public static String readFileToString(String path) {
    Objects.requireNonNull(path, "path must not be null");
    try {
      // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
      return org.apache.commons.io.FileUtils.readFileToString(
          new File(path), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取文件失败: " + path, e);
    }
  }

  /**
   * 静默读取文件内容为 UTF-8 字符串，读取失败返回 null。
   *
   * <p>显式命名 Quietly，调用方应知悉 IO 异常会被吞掉并返回 null。 业务场景请优先使用 {@link #readFileToString(String)}。
   *
   * @param path 文件路径，不能为 null
   * @return 文件内容字符串，读取失败返回 null
   * @throws NullPointerException 如果 path 为 null
   */
  public static String readFileToStringQuietly(String path) {
    Objects.requireNonNull(path, "path must not be null");
    try {
      return readFileToString(path);
    } catch (UncheckedIOException e) {
      log.warn("Failed to read file to string: {}", path, e);
      return null;
    }
  }

  /**
   * 将字符串写入文件（UTF-8），覆盖已有内容。目标文件的父目录不存在时自动创建。
   *
   * @param path 目标文件路径，不能为 null
   * @param content 要写入的内容，不能为 null
   * @throws NullPointerException 如果任一参数为 null
   * @throws UncheckedIOException 写入失败时抛出（附带文件路径上下文）
   */
  public static void writeStringToFile(String path, String content) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(content, "content must not be null");
    try {
      // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
      org.apache.commons.io.FileUtils.writeStringToFile(
          new File(path), content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("写入文件失败: " + path, e);
    }
  }

  /**
   * 将输入流内容复制到目标文件，输入流在使用后关闭。
   *
   * @param is 输入流，不能为 null
   * @param targetPath 目标文件路径，不能为 null
   * @throws NullPointerException 如果任一参数为 null
   * @throws UncheckedIOException 复制失败时抛出（附带目标路径上下文）
   */
  public static void copy(InputStream is, String targetPath) {
    Objects.requireNonNull(is, "input stream must not be null");
    Objects.requireNonNull(targetPath, "targetPath must not be null");
    try {
      // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
      org.apache.commons.io.FileUtils.copyInputStreamToFile(is, new File(targetPath));
    } catch (IOException e) {
      throw new UncheckedIOException("复制输入流到文件失败: " + targetPath, e);
    } finally {
      IOUtils.closeQuietly(is);
    }
  }

  // ==================== 目录操作 ====================

  /**
   * 创建目录（含不存在的父目录），目录已存在时不报错。
   *
   * @param dirPath 目录路径，不能为 null
   * @throws NullPointerException 如果 dirPath 为 null
   * @throws UncheckedIOException 创建失败时抛出（附带目录路径上下文）
   */
  public static void mkdirs(String dirPath) {
    Objects.requireNonNull(dirPath, "dirPath must not be null");
    try {
      // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
      org.apache.commons.io.FileUtils.forceMkdir(new File(dirPath));
    } catch (IOException e) {
      throw new UncheckedIOException("创建目录失败: " + dirPath, e);
    }
  }

  /**
   * 静默删除文件或目录，失败不抛异常。
   *
   * @param path 文件或目录路径，不能为 null
   * @return 是否删除成功（不存在也返回 true）
   * @throws NullPointerException 如果 path 为 null
   */
  public static boolean deleteQuietly(String path) {
    Objects.requireNonNull(path, "path must not be null");
    // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
    return org.apache.commons.io.FileUtils.deleteQuietly(new File(path));
  }

  /**
   * 类似 Unix touch：创建文件（如果不存在），或更新最后修改时间（如果已存在）。
   *
   * @param path 文件路径，不能为 null
   * @throws NullPointerException 如果 path 为 null
   * @throws UncheckedIOException touch 失败时抛出（附带文件路径上下文）
   */
  public static void touch(String path) {
    Objects.requireNonNull(path, "path must not be null");
    try {
      // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
      org.apache.commons.io.FileUtils.touch(new File(path));
    } catch (IOException e) {
      throw new UncheckedIOException("touch 文件失败: " + path, e);
    }
  }

  // ==================== 扩展名解析 ====================

  /**
   * 获取文件扩展名（不含点号）。
   *
   * <p>例如："test.txt" 返回 "txt"，"archive.tar.gz" 返回 "gz"。
   *
   * @param filename 文件名，可为 null
   * @return 文件扩展名（不含点）；无扩展名返回空字符串；输入为 null 返回 null
   */
  public static String getExtension(String filename) {
    if (filename == null) {
      return null;
    }
    int lastDotIndex = filename.lastIndexOf(EXTENSION_SEPARATOR.charAt(0));
    if (lastDotIndex == LAST_DOT_NOT_FOUND) {
      return "";
    }
    return filename.substring(lastDotIndex + 1);
  }

  /**
   * 获取不含扩展名的文件名。
   *
   * <p>例如："test.txt" 返回 "test"，"archive.tar.gz" 返回 "archive.tar"。
   *
   * @param filename 文件名，可为 null
   * @return 不含扩展名的文件名；无扩展名返回原文件名；输入为 null 返回 null
   */
  public static String getFilenameWithoutExtension(String filename) {
    if (filename == null) {
      return null;
    }
    int lastDotIndex = filename.lastIndexOf(EXTENSION_SEPARATOR.charAt(0));
    if (lastDotIndex == LAST_DOT_NOT_FOUND) {
      return filename;
    }
    return filename.substring(0, lastDotIndex);
  }

  // ==================== 目录内容判断 ====================

  /**
   * 判断目录是否为空（不包含任何文件或子目录）。路径不存在或不是目录时返回 true。
   *
   * @param dirPath 目录路径，不能为 null
   * @return 目录是否为空或不存在
   * @throws NullPointerException 如果 dirPath 为 null
   */
  public static boolean isEmptyDirectory(String dirPath) {
    Objects.requireNonNull(dirPath, "dirPath must not be null");
    File dir = new File(dirPath);
    if (!dir.isDirectory()) {
      return true;
    }
    String[] children = dir.list();
    return children == null || children.length == 0;
  }

  // ==================== 文件大小 ====================

  /**
   * 获取文件大小（字节数）。
   *
   * @param path 文件路径，不能为 null
   * @return 文件大小（字节），文件不存在或获取失败返回 -1
   * @throws NullPointerException 如果 path 为 null
   */
  public static long sizeOf(String path) {
    Objects.requireNonNull(path, "path must not be null");
    Path filePath = Paths.get(path);
    if (!Files.exists(filePath)) {
      return SIZE_NOT_EXIST;
    }
    // FQN-OK: name conflict with com.njydsz.common.util.io.FileUtils
    return org.apache.commons.io.FileUtils.sizeOf(new File(path));
  }
}
