package com.njydsz.generator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 代码生成历史记录服务。
 *
 * <p>将每次代码生成的元数据（时间、表名、模块、生成的文件列表）记录到本地 JSON 文件，
 * 便于回溯历史操作、查看差异和恢复。
 *
 * <p>历史文件存储在 {@code ~/.ydsz-generator/history/} 目录下，按日期归档。
 *
 * @author ydsz-team
 * @since 26.09.04
 */
@Slf4j
@Service
public class CodeGenHistoryService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final String HISTORY_DIR = System.getProperty("user.home") + "/.ydsz-generator/history";

  /**
   * 记录一次代码生成操作。
   *
   * @param moduleName - 模块名
   * @param tableName - 表名
   * @param generatedFiles - 生成的文件路径列表
   */
  public void recordGeneration(String moduleName, String tableName, List<String> generatedFiles) {
    try {
      Path historyDir = Paths.get(HISTORY_DIR);
      Files.createDirectories(historyDir);

      String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
      String fileName = String.format("gen_%s_%s_%s.md", moduleName, tableName, timestamp);
      Path historyFile = historyDir.resolve(fileName);

      String build = buildHistoryEntry(moduleName, tableName, generatedFiles, timestamp);
      Files.writeString(historyFile, build, StandardCharsets.UTF_8);
      log.debug("代码生成历史已记录: {}", historyFile);
    } catch (IOException e) {
      log.warn("记录代码生成历史失败: {}", e.getMessage());
    }
  }

  /**
   * 查询最近的生成历史。
   *
   * @param limit - 最多返回条数
   * @return 历史记录列表
   */
  public List<HistoryEntry> listRecentHistory(int limit) {
    List<HistoryEntry> entries = new ArrayList<>(limit);
    Path historyDir = Paths.get(HISTORY_DIR);

    if (!Files.exists(historyDir)) {
      return entries;
    }

    try {
      Files.list(historyDir)
          .filter(p -> p.toString().endsWith(".md"))
          .sorted((a, b) -> {
            try {
              return -Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
            } catch (IOException e) {
              return 0;
            }
          })
          .limit(limit)
          .forEach(p -> entries.add(parseHistoryFile(p)));
    } catch (IOException e) {
      log.warn("读取历史记录目录失败: {}", e.getMessage());
    }

    return entries;
  }

  /**
   * 生成 Git diff 风格的差异报告。
   *
   * @param filePath - 文件路径
   * @param oldContent - 旧内容
   * @param newContent - 新内容
   * @return diff 格式字符串
   */
  public String generateDiff(String filePath, String oldContent, String newContent) {
    if (oldContent == null || oldContent.equals(newContent)) {
      return "[无变更] " + filePath;
    }

    String[] oldLines = oldContent.split("\n");
    String[] newLines = newContent.split("\n");

    StringBuilder diff = new StringBuilder();
    diff.append("--- a/").append(filePath).append("\n");
    diff.append("+++ b/").append(filePath).append("\n");

    int oldIdx = 0;
    int newIdx = 0;

    while (oldIdx < oldLines.length || newIdx < newLines.length) {
      if (oldIdx < oldLines.length && newIdx < newLines.length
          && oldLines[oldIdx].equals(newLines[newIdx])) {
        diff.append(" ").append(oldLines[oldIdx]).append("\n");
        oldIdx++;
        newIdx++;
      } else if (oldIdx < oldLines.length && (newIdx >= newLines.length
          || !contains(newLines, oldLines[oldIdx], newIdx))) {
        diff.append("-").append(oldLines[oldIdx]).append("\n");
        oldIdx++;
      } else if (newIdx < newLines.length) {
        diff.append("+").append(newLines[newIdx]).append("\n");
        newIdx++;
      }
    }

    return diff.toString();
  }

  private String buildHistoryEntry(String moduleName, String tableName,
                                   List<String> generatedFiles, String timestamp) {
    StringBuilder sb = new StringBuilder();
    sb.append("# 代码生成记录\n\n");
    sb.append("- **时间**: ").append(timestamp).append("\n");
    sb.append("- **模块**: ").append(moduleName).append("\n");
    sb.append("- **表名**: ").append(tableName).append("\n");
    sb.append("- **生成文件数**: ").append(generatedFiles.size()).append("\n\n");
    sb.append("## 生成文件列表\n\n");
    for (String file : generatedFiles) {
      sb.append("- `").append(file).append("`\n");
    }
    return sb.toString();
  }

  private HistoryEntry parseHistoryFile(Path p) {
    try {
      String content = Files.readString(p, StandardCharsets.UTF_8);
      String fileName = p.getFileName().toString();
      long size = Files.size(p);
      return new HistoryEntry(fileName, size, content.substring(0, Math.min(200, content.length())));
    } catch (IOException e) {
      return new HistoryEntry(p.getFileName().toString(), 0, "");
    }
  }

  private static boolean contains(String[] lines, String target, int fromIndex) {
    for (int i = fromIndex; i < lines.length; i++) {
      if (lines[i].equals(target)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 历史记录条目。
   *
   * @param fileName - 文件名
   * @param sizeBytes - 文件大小（字节）
   * @param preview - 内容预览
   */
  public record HistoryEntry(String fileName, long sizeBytes, String preview) {
  }
}
