package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.service.EntityReverseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实体类反向生成 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator/reverse")
@RequiredArgsConstructor
public class ReverseController {

  private final EntityReverseService reverseService;

  /**
   * 反向分析单个 Java 源文件。
   *
   * @param sourceFilePath 源文件路径
   * @param templateGroupId 模板分组 ID
   * @param outputDir 输出目录
   * @return 分析报告
   */
  @PostMapping("/analyze")
  public YdszResponse<String> analyze(
      @RequestParam String sourceFilePath,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir) {
    String result = reverseService.reverseGenerate(sourceFilePath, templateGroupId, outputDir);
    return YdszResponse.success(result);
  }

  /**
   * 批量反向分析目录。
   *
   * @param sourceDirPath 源文件目录
   * @param templateGroupId 模板分组 ID
   * @param outputDir 输出目录
   * @return 分析报告列表
   */
  @PostMapping("/analyze-batch")
  public YdszResponse<List<String>> analyzeBatch(
      @RequestParam String sourceDirPath,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir) {
    List<String> results = reverseService.reverseBatch(sourceDirPath, templateGroupId, outputDir);
    return YdszResponse.success(results);
  }
}
