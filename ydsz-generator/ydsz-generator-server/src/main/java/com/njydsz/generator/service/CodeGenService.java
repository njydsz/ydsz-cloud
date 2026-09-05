package com.njydsz.generator.service;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.engine.CodeGenEngine;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.enums.GenStatusEnum;
import com.njydsz.generator.repository.GenHistoryFileRepository;
import com.njydsz.generator.repository.GenHistoryRepository;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成编排服务（Domain Service）。
 *
 * <p>串联 TableMeta → Template → Engine → History 全链路，核心职责：
 * <ul>
 *   <li>{@link #preview} — 预览不写入</li>
 *   <li>{@link #generate} — 正式生成（带历史记录 + 冲突策略）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenService {

  private final DatasourceService datasourceService;
  private final TemplateGroupService templateGroupService;
  private final TemplateService templateService;
  private final TableMetadataService tableMetadataService;
  private final CodeGenEngine codeGenEngine;
  private final GenHistoryRepository historyRepository;
  private final GenHistoryFileRepository historyFileRepository;

  /** 默认作者（来自配置）。 */
  @Value("${generator.default-author:ydsz-generator}")
  private String defaultAuthor;

  /** 默认基础包（来自配置）。 */
  @Value("${generator.default-package:com.njydsz}")
  private String defaultBasePackage;

  /**
   * 预览指定表的生成结果（不写文件）。
   *
   * @param datasourceId   数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName      表名
   * @return 预览结果列表
   */
  public List<CodePreviewVO> preview(Long datasourceId, Long templateGroupId, String tableName) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    GenTableMeta tableMeta = tableMetadataService.getOrRefresh(ds, tableName);
    List<GenColumnMeta> columns = tableMetadataService.listColumns(tableMeta.getId());
    // 如果缓存为空则刷新
    if (columns == null || columns.isEmpty()) {
      columns = tableMetadataService.refreshColumns(ds, tableMeta);
    }
    List<GenTemplate> templates = templateService.listByGroup(templateGroupId);

    List<CodePreviewVO> previews = new ArrayList<>(templates.size());
    Map<String, Object> tableCtx = codeGenEngine.buildTableContext(columns);
    Map<String, Object> context = codeGenEngine.buildContext(
        tableMeta.getModuleName(), defaultBasePackage, defaultAuthor,
        tableCtx, new HashMap<>());

    for (GenTemplate tpl : templates) {
      previews.add(codeGenEngine.preview(tpl, context));
    }
    return previews;
  }

  /**
   * 正式生成代码到指定目录。
   *
   * @param datasourceId   数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName      表名
   * @param outputDir      输出目录
   * @param conflictStrategy 冲突策略
   * @param triggeredBy    触发人
   * @return 生成结果
   */
  @Transactional(rollbackFor = Exception.class)
  public GenResultVO generate(
      Long datasourceId, Long templateGroupId, String tableName,
      String outputDir, ConflictStrategyEnum conflictStrategy, String triggeredBy) {

    if (conflictStrategy == null) {
      conflictStrategy = ConflictStrategyEnum.SKIP;
    }

    // 创建历史记录
    GenHistory history = GenHistory.builder()
        .moduleName(tableName)
        .datasourceId(datasourceId)
        .templateGroupId(templateGroupId)
        .tableCount(1)
        .fileCount(0)
        .status(GenStatusEnum.RUNNING.getCode())
        .triggeredBy(triggeredBy)
        .startedAt(LocalDateTime.now())
        .build();
    history = historyRepository.save(history);

    int successCount = 0;
    int skipCount = 0;
    int failCount = 0;
    List<GenHistoryFile> historyFiles = new ArrayList<>(8);

    try {
      GenDatasource ds = datasourceService.getById(datasourceId);
      GenTableMeta tableMeta = tableMetadataService.getOrRefresh(ds, tableName);
      List<GenColumnMeta> columns = tableMetadataService.refreshColumns(ds, tableMeta);
      List<GenTemplate> templates = templateService.listByGroup(templateGroupId);

      Map<String, Object> tableCtx = codeGenEngine.buildTableContext(columns);
      Map<String, Object> context = codeGenEngine.buildContext(
          tableMeta.getModuleName(), defaultBasePackage, defaultAuthor,
          tableCtx, new HashMap<>());

      for (GenTemplate tpl : templates) {
        String content = codeGenEngine.renderTemplate(tpl, context);
        String filePath = outputDir + "/" + resolveOutputPath(tpl, tableMeta);
        String hash = codeGenEngine.computeHash(content);
        String action = writeFile(filePath, content, conflictStrategy);

        GenHistoryFile hf = GenHistoryFile.builder()
            .historyId(history.getId())
            .filePath(filePath)
            .originalBackupPath(null)
            .fileHash(hash)
            .action(action)
            .build();
        historyFiles.add(hf);

        if ("CREATED".equals(action) || "UPDATED".equals(action)) {
          successCount++;
        } else {
          skipCount++;
        }
      }

      history.setFileCount(successCount + skipCount);
      history.setStatus((failCount > 0
          ? (successCount > 0 ? GenStatusEnum.PARTIAL : GenStatusEnum.FAILED)
          : GenStatusEnum.SUCCESS).getCode());
      history.setFinishedAt(LocalDateTime.now());
    } catch (Exception e) {
      log.error("代码生成失败 table={} err={}", tableName, e.getMessage(), e);
      history.setStatus(GenStatusEnum.FAILED.getCode());
      history.setErrorMessage(e.getMessage());
      history.setFinishedAt(LocalDateTime.now());
      failCount++;
    }

    historyRepository.save(history);
    historyFileRepository.batchSave(historyFiles);

    return GenResultVO.builder()
        .historyId(history.getId())
        .fileCount(successCount + skipCount + failCount)
        .successCount(successCount)
        .skipCount(skipCount)
        .failCount(failCount)
        .build();
  }

  /**
   * 批量生成多个表。
   *
   * @param datasourceId   数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableNames     表名列表
   * @param outputDir      输出目录
   * @param conflictStrategy 冲突策略
   * @param triggeredBy    触发人
   * @return 生成结果汇总
   */
  public GenResultVO generateBatch(
      Long datasourceId, Long templateGroupId, List<String> tableNames,
      String outputDir, ConflictStrategyEnum conflictStrategy, String triggeredBy) {

    int totalSuccess = 0;
    int totalSkip = 0;
    int totalFail = 0;
    Long firstHistoryId = null;

    for (String tableName : tableNames) {
      GenResultVO r = generate(datasourceId, templateGroupId, tableName,
          outputDir, conflictStrategy, triggeredBy);
      if (firstHistoryId == null) {
        firstHistoryId = r.getHistoryId();
      }
      totalSuccess += (r.getSuccessCount() != null ? r.getSuccessCount() : 0);
      totalSkip += (r.getSkipCount() != null ? r.getSkipCount() : 0);
      totalFail += (r.getFailCount() != null ? r.getFailCount() : 0);
    }

    return GenResultVO.builder()
        .historyId(firstHistoryId)
        .fileCount(totalSuccess + totalSkip + totalFail)
        .successCount(totalSuccess)
        .skipCount(totalSkip)
        .failCount(totalFail)
        .build();
  }

  private String resolveOutputPath(GenTemplate tpl, GenTableMeta tableMeta) {
    // 根据模板文件名映射到目标路径（如 entity.vm → domain/entity/TableName.java）
    String fileName = tpl.getFileName().replace(".vm", ".java");
    String parent = tpl.getParentPath();
    if (parent == null || parent.isEmpty()) {
      return fileName;
    }
    return parent + fileName;
  }

  private String writeFile(String filePath, String content, ConflictStrategyEnum strategy)
      throws Exception {
    Path path = Paths.get(filePath);
    Files.createDirectories(path.getParent());

    if (Files.exists(path)) {
      switch (strategy) {
        case SKIP:
          return "UNCHANGED";
        case OVERRIDE:
          // 备份原文件
          Path backup = Paths.get(filePath + ".bak." + System.currentTimeMillis());
          Files.copy(path, backup);
          Files.writeString(path, content, StandardCharsets.UTF_8);
          return "UPDATED";
        case MERGE:
          // 简单追加以 // AUTO-GEN 开头
          String existing = Files.readString(path, StandardCharsets.UTF_8);
          Files.writeString(path, existing + "\n// AUTO-GEN\n" + content, StandardCharsets.UTF_8);
          return "UPDATED";
        default:
          return "UNCHANGED";
      }
    } else {
      Files.writeString(path, content, StandardCharsets.UTF_8);
      return "CREATED";
    }
  }
}
