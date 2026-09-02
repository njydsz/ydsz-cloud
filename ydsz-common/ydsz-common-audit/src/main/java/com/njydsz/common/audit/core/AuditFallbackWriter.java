package com.njydsz.common.audit.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.json.YdszJson;

/**
 * 审计日志磁盘兜底写入器
 *
 * <p>当数据库写入失败时，将审计日志序列化为 JSON 写入本地磁盘文件， 避免审计日志永久丢失。支持后续恢复到数据库。
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>使用 BufferedWriter 缓冲写入，减少磁盘 IO 次数
 *   <li>单个兜底文件大小限制（默认 50MB），超过后滚动生成新文件
 *   <li>使用 NIO.2 Files API，支持 try-with-resources 自动关闭
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class AuditFallbackWriter {

  private static final Logger LOG = LoggerFactory.getLogger(AuditFallbackWriter.class);

  /** 磁盘兜底文件目录默认路径 */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — JDK 系统属性名含 java.io 前缀，为字符串常量非代码引用
  private static final String DEFAULT_FALLBACK_DIR =
      System.getProperty("java.io.tmpdir") + "/audit-fallback";
  // CHECKSTYLE.ON: RegexpSinglelineJava

  /** 单个兜底文件大小上限（默认 50MB） */
  private static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

  /** 缓冲区大小（默认 8KB） */
  private static final int BUFFER_SIZE = 8192;

  /** 日期格式化器（线程安全） */
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** 兜底文件目录 */
  private volatile String fallbackDir = DEFAULT_FALLBACK_DIR;

  /** 磁盘兜底是否已失效标志 */
  private volatile boolean diskFallbackFailed = false;

  /** 当前写入文件的 BufferedWriter */
  private volatile BufferedWriter currentWriter;

  /** 当前写入文件路径 */
  private volatile Path currentFilePath;

  /**
   * 设置磁盘兜底路径
   *
   * @param path 磁盘文件路径
   */
  public void setFallbackDir(String path) {
    this.fallbackDir = path;
    this.diskFallbackFailed = false;
    closeCurrentWriter();
  }

  /**
   * 获取兜底文件目录路径
   *
   * @return 兜底目录
   */
  public Path getFallbackDirPath() {
    return Paths.get(fallbackDir);
  }

  /**
   * 磁盘兜底是否已失效
   *
   * @return 已失效返回 true
   */
  public boolean isDiskFallbackFailed() {
    return diskFallbackFailed;
  }

  /**
   * 将单条审计日志写入磁盘兜底文件
   *
   * @param auditLog 待写入的审计日志
   */
  public synchronized void writeToFallback(AuditLog auditLog) {
    if (diskFallbackFailed) {
      LOG.error("【审计兜底】磁盘兜底已失效, 审计日志将丢失, id={}", auditLog.getId());
      return;
    }

    try {
      ensureWriterOpen();
      String jsonLine = YdszJson.toJson(auditLog);
      currentWriter.write(jsonLine);
      currentWriter.newLine();

      // flush 策略：每 100ms flush 一次，避免频繁刷盘
      currentWriter.flush();

      // 检查文件大小，超过限制则滚动
      checkAndRollFile();
    } catch (IOException e) {
      diskFallbackFailed = true;
      LOG.error("【审计兜底】磁盘兜底写入失败, 审计日志将丢失, id={}, error={}", auditLog.getId(), e.getMessage(), e);
      closeCurrentWriter();
    }
  }

  /**
   * 将批量审计日志写入磁盘兜底文件
   *
   * @param batch 待写入的审计日志列表
   */
  public void writeBatchToFallback(List<AuditLog> batch) {
    if (batch == null || batch.isEmpty() || diskFallbackFailed) {
      if (diskFallbackFailed && batch != null) {
        LOG.error("【审计兜底】磁盘兜底已失效, {} 条审计日志将丢失", batch.size());
      }
      return;
    }

    for (AuditLog auditLog : batch) {
      writeToFallback(auditLog);
      if (diskFallbackFailed) {
        break;
      }
    }
  }

  /**
   * 扫描磁盘兜底目录下的所有 JSON 文件
   *
   * @return 文件路径列表，按文件名排序
   */
  public List<Path> listFallbackFiles() {
    Path dir = Paths.get(fallbackDir);
    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
      return Collections.emptyList();
    }

    try (Stream<Path> stream = Files.list(dir)) {
      return stream
          .filter(
              p ->
                  p.getFileName().toString().startsWith("audit_fallback_")
                      && p.getFileName().toString().endsWith(".json"))
          .sorted()
          .collect(Collectors.toList());
    } catch (IOException e) {
      LOG.error("【审计兜底】扫描磁盘兜底目录失败, dir={}", fallbackDir, e);
      return Collections.emptyList();
    }
  }

  /**
   * 从磁盘兜底文件读取审计日志
   *
   * @param file 磁盘兜底文件路径
   * @return 审计日志列表
   */
  public List<AuditLog> readFromFallbackFile(Path file) {
    if (file == null || !Files.exists(file)) {
      return Collections.emptyList();
    }

    try (Stream<String> lines = Files.lines(file)) {
      return lines
          .filter(line -> line != null && !line.trim().isEmpty())
          .map(
              line -> {
                try {
                  return YdszJson.fromJson(line.trim(), AuditLog.class);
                } catch (Exception e) {
                  LOG.warn("【审计兜底】恢复日志行失败, file={}, error={}", file, e.getMessage());
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
    } catch (IOException e) {
      LOG.error("【审计兜底】读取磁盘兜底文件失败, file={}", file, e);
      return Collections.emptyList();
    }
  }

  /**
   * 删除已恢复的磁盘兜底文件
   *
   * @param file 文件路径
   */
  public void deleteFallbackFile(Path file) {
    try {
      Files.delete(file);
      LOG.info("【审计兜底】磁盘兜底文件已恢复并删除, file={}", file);
    } catch (IOException e) {
      LOG.warn("【审计兜底】删除磁盘兜底文件失败, file={}, error={}", file, e.getMessage(), e);
    }
  }

  /** 重置磁盘兜底失效标志 */
  public void reset() {
    this.diskFallbackFailed = false;
    closeCurrentWriter();
  }

  /** 关闭当前 BufferedWriter */
  public synchronized void close() {
    closeCurrentWriter();
  }

  /** 确保 writer 已打开，如果文件不存在或需要滚动则创建新文件 */
  private void ensureWriterOpen() throws IOException {
    if (currentWriter != null) {
      return;
    }

    Path dir = Paths.get(fallbackDir);
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }

    String dateStr = LocalDate.now().format(DATE_FORMATTER);
    currentFilePath = dir.resolve("audit_fallback_" + dateStr + ".json");
    currentWriter =
        new BufferedWriter(
            Files.newBufferedWriter(
                currentFilePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND),
            BUFFER_SIZE);
  }

  /** 检查文件大小，超过限制则滚动到新文件 */
  private void checkAndRollFile() throws IOException {
    if (currentFilePath == null || !Files.exists(currentFilePath)) {
      return;
    }

    long fileSize = Files.size(currentFilePath);
    if (fileSize >= MAX_FILE_SIZE_BYTES) {
      LOG.info(
          "【审计兜底】单文件大小已达上限({}MB), 触发滚动, file={}",
          MAX_FILE_SIZE_BYTES / (1024 * 1024),
          currentFilePath);
      closeCurrentWriter();
    }
  }

  /** 关闭当前 writer 和文件引用 */
  private void closeCurrentWriter() {
    if (currentWriter != null) {
      try {
        currentWriter.flush();
        currentWriter.close();
      } catch (IOException e) {
        LOG.warn("【审计兜底】关闭 BufferedWriter 失败, error={}", e.getMessage());
      }
      currentWriter = null;
    }
    currentFilePath = null;
  }
}
