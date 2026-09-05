package com.njydsz.generator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体类反向生成服务。
 *
 * <p>从已有的 Java Entity 源文件解析字段结构，生成 Service/Controller 骨架代码。
 * 解析流程：读取源文件 → 提取类名/包/字段 → 反推 Service/Controller。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
public class EntityReverseService {

  /** 默认作者（来自配置）。 */
  @Value("${generator.default-author:ydsz-generator}")
  private String defaultAuthor;
  /** 类名正则。 */
  private static final Pattern CLASS_NAME_PATTERN =
      Pattern.compile("public\\s+(?:class|record)\\s+(\\w+)");
  /** 包名正则。 */
  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("package\\s+([\\w.]+)\\s*;");
  /** 字段正则。 */
  private static final Pattern FIELD_PATTERN =
      Pattern.compile("private\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*;");

  /**
   * 从指定 .java 源文件反向生成分析报告。
   *
   * @param sourceFilePath Java 源文件路径
   * @param templateGroupId 模板分组 ID
   * @param outputDir 输出目录
   * @return 反向生成分析结果
   */
  public String reverseGenerate(String sourceFilePath, Long templateGroupId, String outputDir) {
    File sourceFile = new File(sourceFilePath);
    if (!sourceFile.exists() || !sourceFile.isFile()) {
      throw new IllegalArgumentException("源文件不存在: " + sourceFilePath);
    }
    try {
      String content = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
      String className = extractClassName(content);
      String packageName = extractPackageName(content);
      List<FieldInfo> fields = extractFields(content);

      return buildReport(className, packageName, fields);
    } catch (IOException e) {
      log.error("读取源文件失败 path={} err={}", sourceFilePath, e.getMessage(), e);
      throw new RuntimeException("反向解析失败: " + e.getMessage(), e);
    }
  }

  /**
   * 批量反向解析目录下全部 .java 文件。
   *
   * @param sourceDirPath 源文件目录
   * @param templateGroupId 模板分组 ID
   * @param outputDir 输出目录
   * @return 每个文件的分析报告
   */
  public List<String> reverseBatch(String sourceDirPath, Long templateGroupId, String outputDir) {
    File dir = new File(sourceDirPath);
    if (!dir.exists() || !dir.isDirectory()) {
      throw new IllegalArgumentException("无效目录: " + sourceDirPath);
    }
    File[] javaFiles = dir.listFiles((d, name) -> name.endsWith(".java"));
    if (javaFiles == null) {
      return new ArrayList<>();
    }
    List<String> results = new ArrayList<>(javaFiles.length);
    for (File f : javaFiles) {
      try {
        results.add(reverseGenerate(f.getAbsolutePath(), templateGroupId, outputDir));
      } catch (Exception e) {
        results.add(String.format("文件 %s 解析失败: %s", f.getName(), e.getMessage()));
      }
    }
    return results;
  }

  // ════════════════════════════════════════════════════════════
  // 私有辅助方法
  // ════════════════════════════════════════════════════════════

  private String extractClassName(String source) {
    Matcher m = CLASS_NAME_PATTERN.matcher(source);
    return m.find() ? m.group(1) : "";
  }

  private String extractPackageName(String source) {
    Matcher m = PACKAGE_PATTERN.matcher(source);
    return m.find() ? m.group(1) : "";
  }

  private List<FieldInfo> extractFields(String source) {
    List<FieldInfo> fields = new ArrayList<>(16);
    Matcher m = FIELD_PATTERN.matcher(source);
    while (m.find()) {
      String type = m.group(1);
      String name = m.group(2);
      if (!type.equals("static") && !type.equals("final")) {
        fields.add(new FieldInfo(type, name, ""));
      }
    }
    return fields;
  }

  private String buildReport(String className, String packageName, List<FieldInfo> fields) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("反向生成分析报告\n");
    sb.append("========================================\n");
    sb.append(String.format("  类名: %s%n", className));
    sb.append(String.format("  包名: %s%n", packageName));
    sb.append(String.format("  字段数: %d%n", fields.size()));
    sb.append("  字段列表:\n");
    for (FieldInfo f : fields) {
      sb.append(String.format("    - %s %s%n", f.type, f.name));
    }
    sb.append("========================================\n");
    return sb.toString();
  }

  /** 字段信息记录。 */
  private record FieldInfo(String type, String name, String comment) {
  }
}
