package com.njydsz.nextwiki.server.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件版本对比服务。
 *
 * <p>支持文本类文件的版本差异对比（基于 Myers diff 算法的简化实现）。
 *
 * <p><b>支持的对比类型：</b>
 *
 * <ul>
 *   <li>纯文本文件（txt、md、json、xml、csv 等）
 *   <li>不支持二进制文件（图片、视频、Office 文档等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class VersionDiffService {

  /** 单次对比最大文件大小（字节）：1MB */
  private static final long MAX_DIFF_SIZE = 1024 * 1024;

  /**
   * 对比两个文本版本的差异。
   *
   * @param oldContent 旧版本文本内容
   * @param newContent 新版本文本内容
   * @return 差异结果（按行粒度的变更列表）
   */
  public DiffResult diff(String oldContent, String newContent) {
    if (oldContent == null) {
      oldContent = "";
    }
    if (newContent == null) {
      newContent = "";
    }

    List<String> oldLines = splitLines(oldContent);
    List<String> newLines = splitLines(newContent);

    // 使用 LCS（最长公共子序列）算法计算差异
    List<DiffEntry> entries = computeLcsDiff(oldLines, newLines);

    return DiffResult.builder()
        .entries(entries)
        .oldLineCount(oldLines.size())
        .newLineCount(newLines.size())
        .additions((int) entries.stream().filter(e -> e.getType() == DiffType.ADD).count())
        .deletions((int) entries.stream().filter(e -> e.getType() == DiffType.DELETE).count())
        .build();
  }

  /**
   * 从输入流读取文本内容（用于从存储下载文件内容）。
   *
   * @param inputStream 输入流
   * @return 文本内容
   */
  public String readTextContent(InputStream inputStream) {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString();
    } catch (Exception e) {
      log.warn("[VersionDiffService] 读取文本内容失败: {}", e.getMessage());
      return "";
    }
  }

  /**
   * 检查文件是否支持 diff 对比。
   *
   * @param mimeType MIME 类型
   * @param size 文件大小（字节）
   * @return {@code true} 表示支持对比
   */
  public boolean isDiffSupported(String mimeType, long size) {
    if (size > MAX_DIFF_SIZE) {
      return false;
    }
    if (mimeType == null) {
      return true; // 默认允许
    }
    return mimeType.startsWith("text/")
        || mimeType.equals("application/json")
        || mimeType.equals("application/xml")
        || mimeType.equals("application/javascript")
        || mimeType.equals("application/x-yaml")
        || mimeType.equals("application/markdown");
  }

  // ==================== 私有方法 ====================

  /** 按行分割文本 */
  private List<String> splitLines(String content) {
    List<String> lines = new ArrayList<>();
    if (content.isEmpty()) {
      return lines;
    }
    String[] parts = content.split("\n", -1);
    for (String part : parts) {
      // 移除尾部 \r（Windows 换行符）
      String trimmed = part.endsWith("\r") ? part.substring(0, part.length() - 1) : part;
      lines.add(trimmed);
    }
    // 移除最后一个空行（由末尾 \n 导致）
    if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    return lines;
  }

  /**
   * 基于 LCS 算法计算行级差异。
   *
   * <p>简化实现：使用动态规划计算最长公共子序列，然后回溯生成 diff 条目。
   */
  private List<DiffEntry> computeLcsDiff(List<String> oldLines, List<String> newLines) {
    int m = oldLines.size();
    int n = newLines.size();

    // 边界情况
    if (m == 0 && n == 0) {
      return new ArrayList<>();
    }
    if (m == 0) {
      List<DiffEntry> entries = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        entries.add(DiffEntry.builder().type(DiffType.ADD).newLine(j + 1).content(newLines.get(j)).build());
      }
      return entries;
    }
    if (n == 0) {
      List<DiffEntry> entries = new ArrayList<>();
      for (int i = 0; i < m; i++) {
        entries.add(DiffEntry.builder().type(DiffType.DELETE).oldLine(i + 1).content(oldLines.get(i)).build());
      }
      return entries;
    }

    // 动态规划计算 LCS 长度
    int[][] dp = new int[m + 1][n + 1];
    for (int i = 1; i <= m; i++) {
      for (int j = 1; j <= n; j++) {
        if (Objects.equals(oldLines.get(i - 1), newLines.get(j - 1))) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }

    // 回溯生成 diff 条目
    List<DiffEntry> entries = new ArrayList<>();
    int i = m;
    int j = n;
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && Objects.equals(oldLines.get(i - 1), newLines.get(j - 1))) {
        // 相同行
        entries.add(0, DiffEntry.builder()
            .type(DiffType.EQUAL)
            .oldLine(i)
            .newLine(j)
            .content(oldLines.get(i - 1))
            .build());
        i--;
        j--;
      } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
        // 新增行
        entries.add(0, DiffEntry.builder()
            .type(DiffType.ADD)
            .newLine(j)
            .content(newLines.get(j - 1))
            .build());
        j--;
      } else if (i > 0) {
        // 删除行
        entries.add(0, DiffEntry.builder()
            .type(DiffType.DELETE)
            .oldLine(i)
            .content(oldLines.get(i - 1))
            .build());
        i--;
      }
    }

    return entries;
  }

  // ==================== 数据模型 ====================

  /** 差异类型 */
  public enum DiffType {
    /** 新增行 */
    ADD,
    /** 删除行 */
    DELETE,
    /** 未变更行 */
    EQUAL
  }

  /** 单条差异条目 */
  @Data
  @Builder
  public static class DiffEntry {
    /** 差异类型 */
    private DiffType type;
    /** 旧版本行号（从 1 开始，EQUAL/DELETE 有效） */
    private Integer oldLine;
    /** 新版本行号（从 1 开始，EQUAL/ADD 有效） */
    private Integer newLine;
    /** 行内容 */
    private String content;
  }

  /** diff 结果 */
  @Data
  @Builder
  public static class DiffResult {
    /** 差异条目列表 */
    private List<DiffEntry> entries;
    /** 旧版本总行数 */
    private int oldLineCount;
    /** 新版本总行数 */
    private int newLineCount;
    /** 新增行数 */
    private int additions;
    /** 删除行数 */
    private int deletions;
  }
}
