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
    List<String> lines = new ArrayList<>(16);