package com.njydsz.generator.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.service.CodeGenService;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;

/**
 * 代码生成 REST 控制器。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/api/generator/code")
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
  @Audit(module = "代码生成", action = AuditAction.QUERY, recordRequest = false)
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
   * 预览并打包为 zip 下载（对齐 RuoYi-Vue 一键下载模式）。
   *
   * @param datasourceId    数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName       表名
   * @return zip 二进制流
   */
  @Audit(module = "代码生成", action = AuditAction.DOWNLOAD, content = "'下载代码ZIP:' + #tableName", recordRequest = false)
  @GetMapping("/preview/zip")
  public ResponseEntity<byte[]> downloadPreviewZip(
      @RequestParam Long datasourceId,
      @RequestParam Long templateGroupId,
      @RequestParam String tableName) throws IOException {
    log.info("下载代码 ZIP ds={} group={} table={}", datasourceId, templateGroupId, tableName);
    List<CodePreviewVO> previews = codeGenService.preview(datasourceId, templateGroupId, tableName);

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
      for (CodePreviewVO preview : previews) {
        if (preview.getContent() == null || preview.getContent().isEmpty()) {
          continue;
        }
        String entryName = preview.getFileName() != null ? preview.getFileName() : "unknown";
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(preview.getContent().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }

    HttpHeaders headers = new HttpHeaders();
    String zipName = tableName + "_" + System.currentTimeMillis() + ".zip";
    headers.setContentDispositionFormData("attachment", zipName);
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    return new ResponseEntity<>(bos.toByteArray(), headers, HttpStatus.OK);
  }

  /**
   * 正式生成代码到指定目录。
   *
   * @param query 生成参数（数据源、模板分组、表名、输出目录、冲突策略、触发人）
   * @return 生成结果
   */
  @Audit(module = "代码生成", action = AuditAction.CREATE, content = "'生成代码:' + #query.tableName", recordRequest = false)
  @PostMapping("/generate")
  public YdszResponse<GenResultVO> generate(@RequestBody GenCodeGenerateQuery query) {
    log.info("生成代码 ds={} group={} table={} dir={} strategy={}",
        query.getDatasourceId(), query.getTemplateGroupId(), query.getTableName(),
        query.getOutputDir(), query.getConflictStrategy());
    return YdszResponse.success(codeGenService.generate(query));
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
  @Audit(module = "代码生成", action = AuditAction.CREATE, content = "'全量生成代码:' + #datasourceId", recordRequest = false)
  @PostMapping("/generate/all")
  public YdszResponse<GenResultVO> generateAll(
      @RequestParam Long datasourceId,
      @RequestParam Long templateGroupId,
      @RequestParam String outputDir,
      @RequestParam(defaultValue = "SKIP") ConflictStrategyEnum conflictStrategy,
      @RequestParam(defaultValue = "system") String triggeredBy) {
    return YdszResponse.success(
        codeGenService.generateAll(datasourceId, templateGroupId, outputDir,
            conflictStrategy, triggeredBy));
  }

  /**
   * 控制器级别兜底异常处理。
   *
   * <p>任何未捕获异常都返回 500 + 错误信息，保持响应结构一致。
   *
   * @param ex 未捕获异常
   * @return 失败响应
   */
  @ExceptionHandler(Exception.class)
  public YdszResponse<Void> handleException(Exception ex) {
    log.error("Generator 接口未捕获异常: {}", ex.getMessage(), ex);
    return YdszResponse.error("500", "生成失败: " + ex.getMessage());
  }
}
