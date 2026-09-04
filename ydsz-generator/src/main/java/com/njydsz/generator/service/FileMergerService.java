package com.njydsz.generator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件合并/对比服务。
 *
 * <p>当生成的代码文件已存在时，提供三种策略：
 * <ul>
 *   <li>{@link FileConflictStrategy#SKIP} — 跳过已存在文件</li>
 *   <li>{@link FileConflictStrategy#OVERRIDE} — 全量覆盖</li>
 *   <li>{@link FileConflictStrategy#MERGE} — 智能合并（保留自定义方法/字段，更新生成部分）</li>
 * </ul>
 *
 * <p><b>智能合并策略说明：</b>
 * <pre>
 * // --- 自动生成区域 START ---
 * // 此区域内容由代码生成器维护，请勿手动修改
 * private String generatedField;
 * // --- 自动生成区域 END ---
 *
 * // 用户自定义代码（合并时保留）
 * private String customMethod() { ... }
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.04
 */
@Slf4j
@Service
public class FileMergerService {

  /** 自动生成的区域标记 */
  private static final String GENERATED_START = "// --- GENERATED CODE START ---";
  private static final String GENERATED_END = "// --- GENERATED CODE END ---";

  /**
   * 处理文件冲突。
   *
   * @param templateName - 模板名
   * @param newContent - 新生成的内容
   * @param existingPath - 已存在文件的路径
   * @param strategy - 冲突处理策略
   * @return 最终写入的内容；若跳过则返回 null
   */
  public String resolveConflict(String templateName, String newContent,
                                String existingPath, FileConflictStrategy strategy) {
    Path path = Paths.get(existingPath);
    if (!Files.exists(path)) {
      return newContent;
    }

    switch (strategy) {
      case SKIP -> {
        log.debug("文件已存在，跳过: {}", existingPath);
        return null;
      }
      case OVERRIDE -> {
        log.debug("文件已存在，覆盖: {}", existingPath);
        return newContent;
      }
      case MERGE -> {
        try {
          String existingContent = Files.readString(path, StandardCharsets.UTF_8);
          return mergeContents(existingContent, newContent);
        } catch (IOException e) {
          log.warn("读取已存在文件失败，降级为覆盖: {}", existingPath);
          return newContent;
        }
      }
      default -> throw new IllegalArgumentException("未知冲突策略: " + strategy);
    }
  }

  /**
   * 生成统一 diff 格式的对比结果。
   *
   * @param oldContent - 旧文件内容
   * @param newContent - 新文件内容
   * @return diff 格式字符串
   */
  public DiffResult computeDiff(String oldContent, String newContent) {
    if (oldContent == null) {
      return new DiffResult(0, 0, 0, List.of());
    }

    String[] oldLines = oldContent.split("\n", -1);
    String[] newLines = newContent.split("\n", -1);

    List<DiffLine> diffLines = new ArrayList<>();
    int added = 0;
    int deleted = 0;
    int unchanged = 0;

    int oldIdx = 0;
    int newIdx = 0;

    while (oldIdx < oldLines.length || newIdx < newLines.length) {
      if (oldIdx < oldLines.length && newIdx < newLines.length
          && oldLines[oldIdx].equals(newLines[newIdx])) {
        diffLines.add(new DiffLine(DiffLineType.UNCHANGED, oldIdx + 1, newIdx + 1, oldLines[oldIdx]));
        unchanged++;
        oldIdx++;
        newIdx++;
      } else if (shouldDelete(oldLines, oldIdx, newLines, newIdx)) {
        diffLines.add(new DiffLine(DiffLineType.DELETED, oldIdx + 1, -1, oldLines[oldIdx]));
        deleted++;
        oldIdx++;
      } else if (newIdx < newLines.length) {
        diffLines.add(new DiffLine(DiffLineType.ADDED, -1, newIdx + 1, newLines[newIdx]));
        added++;
        newIdx++;
      } else {
        diffLines.add(new DiffLine(DiffLineType.DELETED, oldIdx + 1, -1, oldLines[oldIdx]));
        deleted++;
        oldIdx++;
      }
    }

    return new DiffResult(added, deleted, unchanged, diffLines);
  }

  // -----------------------------------------------------------------------

  /**
   * 智能合并：保留 GENERATED CODE 区域外的用户自定义内容。
   *
   * @param existing - 已存在文件内容
   * @param generated - 新生成内容
   * @return 合并后的内容
   */
  private String mergeContents(String existing, String generated) {
    // 如果没有 GENERATED 区域标记，降级为覆盖
    if (!generated.contains(GENERATED_START) || !existing.contains(GENERATED_START)) {
      log.debug("未检测到 GENERATED 区域标记，使用覆盖策略");
      return generated;
    }

    // 提取旧文件中的用户自定义部分
    List<String> userCustomSections = extractUserCustomSections(existing);
    if (userCustomSections.isEmpty()) {
      return generated;
    }

    // 将用户自定义部分追加到生成代码末尾（或插入到指定位置）
    StringBuilder merged = new StringBuilder(generated);
    merged.append("\n");
    merged.append("// --- USER CUSTOM CODE START (PRESERVED) ---\n");
    for (String section : userCustomSections) {
      merged.append(section).append("\n");
    }
    merged.append("// --- USER CUSTOM CODE END ---\n");

    return merged.toString();
  }

  private List<String> extractUserCustomSections(String content) {
    List<String> customSections = new ArrayList<>(4);
    String[] lines = content.split("\n");
    boolean inGenerated = false;
    StringBuilder section = new StringBuilder();

    for (String line : lines) {
      if (line.contains(GENERATED_START)) {
        inGenerated = true;
        if (section.length() > 0) {
          customSections.add(section.toString());
          section = new StringBuilder();
        }
      } else if (line.contains(GENERATED_END)) {
        inGenerated = false;
      } else if (!inGenerated) {
        section.append(line).append("\n");
      }
    }

    if (section.length() > 0) {
      customSections.add(section.toString());
    }

    return customSections;
  }

  private boolean shouldDelete(String[] oldLines, int oldIdx, String[] newLines, int newIdx) {
    if (oldIdx >= oldLines.length) {
      return false;
    }
    if (newIdx >= newLines.length) {
      return true;
    }
    // 简单启发式：如果在后续的新行中不存在当前旧行，则删除
    for (int i = newIdx; i < newLines.length; i++) {
      if (oldLines[oldIdx].equals(newLines[i])) {
        return false;
      }
    }
    return true;
  }

  // -----------------------------------------------------------------------
  // 数据类
  // -----------------------------------------------------------------------

  /**
   * 文件冲突策略枚举。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  public enum FileConflictStrategy {
    /** 跳过已存在文件 */
    SKIP,
    /** 全量覆盖 */
    OVERRIDE,
    /** 智能合并 */
    MERGE
  }

  /**
   * diff 行类型。
   *
   * @author ydsz-team
   * @since 26.09.04
   */
  public enum DiffLineType {
    UNCHANGED,
    ADDED,
    DELETED
  }

  /**
   * diff 结果。
   *
   * @param added - 新增行数
   * @param deleted - 删除行数
   * @param unchanged - 未变行数
   * @param lines - diff 行详情
   */
  public record DiffResult(int added, int deleted, int unchanged, List<DiffLine> lines) {
  }

  /**
   * 单行 diff 信息。
   *
   * @param type - 行类型
   * @param oldLineNumber - 旧文件行号（新增行为 -1）
   * @param newLineNumber - 新文件行号（删除行为 -1）
   * @param content - 行内容
   */
  public record DiffLine(DiffLineType type, int oldLineNumber, int newLineNumber, String content) {
  }
}
