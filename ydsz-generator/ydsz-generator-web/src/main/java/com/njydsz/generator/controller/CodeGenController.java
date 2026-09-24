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
import org.springframework.security.access.annotation.Secured;
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
@ApiVersion("26.09.01")
@Slf4j
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator/code")
@RequiredArgsConstructor
public class CodeGenController {

  private final CodeGenService codeGenService;

  /**
   * 预览代码生成结果（渲染模板后展示，不写入文件系统）。
   *
   * <p>支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库作为数据源，
   * 目标技术栈由所选模板分组决定（如 Spring Boot + MyBatis-Plus、Vue3 等）。
   * 生成产物包含 Entity/VO/DTO、Mapper、Service、Controller、XML 及前端页面等文件。
   *
   * @param datasourceId    数据源 ID，需为已配置且连接正常的数据源
   * @param templateGroupId 模板分组 ID，决定目标技术栈与代码风格
   * @param tableName       物理表名，需存在于指定数据源中
   * @return 预览结果列表，每项包含 fileName（文件名）、filePath（相对路径）、
   *         content（渲染后的代码文本）、isConflict（目标文件是否已存在）
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
   * <p><b>API-RESP 豁免说明（P1-9）：</b>本端点返回二进制文件流（zip 下载），
   * 非平台统一 JSON 响应体适用场景，豁免 {@code YdszResponse} 包装。
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
   * 根据表结构正式生成代码到指定输出目录。
   *
   * <p>支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库作为数据源，
   * 目标技术栈由所选模板分组决定。
   * 生成产物包含 Entity/VO/DTO、Mapper 接口与 XML、Service 接口与实现、
   * Controller、前端 API 及页面等全栈文件。
   *
   * <p>冲突策略：
   * <ul>
   *   <li>SKIP：跳过已存在文件（默认）</li>
   *   <li>OVERRIDE：覆盖已存在文件并备份原文件</li>
   *   <li>APPEND：追加到已存在文件末尾</li>
   * </ul>
   *
   * @param query 生成请求参数，包含：
   *              datasourceId（数据源 ID，非空）、
   *              templateGroupId（模板分组 ID，非空）、
   *              tableName（物理表名，非空）、
   *              outputDir（输出目录绝对路径，非空）、
   *              conflictStrategy（冲突策略：SKIP / OVERRIDE / APPEND，可空，默认 SKIP）、
   *              triggeredBy（触发人标识，可空，默认 system）
   * @return 生成结果，包含 historyId（任务 ID）、fileCount（总文件数）、
   *         successCount（成功数）、skipCount（跳过数）、failCount（失败数）
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
   * 对数据源下全部表批量生成代码。
   *
   * <p>遍历数据源中所有表，逐表应用模板分组进行代码生成。
   * 支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库，
   * 目标技术栈由模板分组决定。生成产物与单表生成一致。
   *
   * <p>冲突策略与 {@link #generate(GenCodeGenerateQuery)} 相同。
   * 建议在非高峰时段执行，大库可能耗时较长。
   *
   * @param datasourceId     数据源 ID，需为已配置且连接正常的数据源
   * @param templateGroupId  模板分组 ID，决定目标技术栈与代码风格
   * @param outputDir        输出目录绝对路径
   * @param conflictStrategy 冲突策略（SKIP / OVERRIDE / APPEND），默认 SKIP
   * @param triggeredBy      触发人标识，默认 system
   * @return 汇总结果，包含 historyId（批次任务 ID）、fileCount（总文件数）、
   *         successCount（成功数）、skipCount（跳过数）、failCount（失败数）
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
}
