package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 代码生成 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator/code")
@RequiredArgsConstructor
public class CodeGenController {

  private final CodeGenService codeGenService;

  /**
   * 预览生成结果。
   *
   * @param datasourceId    数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName       表名
   * @return 预览列表
   */
  @GetMapping("/preview")
  public YdszResponse<List<CodePreviewVO>> preview(
      @RequestParam Long datasourceId,
      @RequestParam Long templateGroupId,
      @RequestParam String tableName) {
    log.info("预览代码 ds={} group={} table={}", datasourceId, templateGroupId, tableName);
    return YdszResponse.success(
        codeGenService.preview(datasourceId, templateGroupId, tableName));
  }

  /**
   * 正式生成代码到指定目录。
   *
   * @param datasourceId      数据源 ID
   * @param templateGroupId   模板分组 ID
   * @param tableName         表名
   * @param outputDir         输出目录
   * @param conflictStrategy  冲突策略（SKIP/OVERRIDE/MERGE）
   * @param triggeredBy       触发人
   * @return 生成结果
   */
  @PostMapping("/generate")
  public YdszResponse<GenResultVO> generate(
      @RequestParam Long datasourceId,
      @RequestParam Long templateGroupId,
      @RequestParam String tableName,
      @RequestParam String outputDir,
      @RequestParam(defaultValue = "SKIP") ConflictStrategyEnum conflictStrategy,
      @RequestParam(defaultValue = "system") String triggeredBy) {
    log.info("生成代码 ds={} group={} table={} dir={} strategy={}",
        datasourceId, templateGroupId, tableName, outputDir, conflictStrategy);
    return YdszResponse.success(
        codeGenService.generate(datasourceId, templateGroupId, tableName,
            outputDir, conflictStrategy, triggeredBy));
  }

  /**
   * 批量生成（全库）。
   *
   * @param datasourceId      数据源 ID
   * @param templateGroupId   模板分组 ID
   * @param outputDir         输出目录
   * @param conflictStrategy  冲突策略
   * @param triggeredBy       触发人
   * @return 生成结果汇总
   */
  @PostMapping("/generate/all")
  public YdszResponse<GenResultVO> generateAll(
      @RequestParam Long datasourceId,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir,
      @RequestParam(defaultValue = "SKIP") ConflictStrategyEnum conflictStrategy,
      @RequestParam(defaultValue = "system") String triggeredBy) {
    // TODO: 获取表列表后批量调用 generate
    throw new UnsupportedOperationException("待实现：全量生成");
  }
}
