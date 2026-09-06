package com.njydsz.generator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.generator.engine.CodeGenEngine;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.enums.GenStatusEnum;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.repository.GenHistoryFileRepository;
import com.njydsz.generator.repository.GenHistoryRepository;
import com.njydsz.generator.vo.CodePreviewVO;
import com.njydsz.generator.vo.GenResultVO;

/**
 * 代码生成编排服务（Domain Service）。
 *
 * <p>串联 TableMeta → Template → Engine → History 全链路，核心职责：
 * <ul>
 *   <li>{@link #preview} — 预览不写入</li>
 *   <li>{@link #generate(GenCodeGenerateQuery)} — 正式生成（带历史记录 + 冲突策略）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenService {

  /** 历史文件列表初始容量。 */
  private static final int HISTORY_FILE_LIST_CAPACITY = 8;
  /** 批量生成单表任务最长等待时间（分钟）。 */
  private static final long PER_TABLE_TIMEOUT_MINUTES = 5L;
  /** 批量生成线程池优雅关闭等待时间（秒）。 */
  private static final long POOL_SHUTDOWN_SECONDS = 30L;

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
   * @param query 生成请求（数据源、模板分组、表名、输出目录、冲突策略、触发人）
   * @return 生成结果
   */
  @Transactional(rollbackFor = Exception.class)
  public GenResultVO generate(GenCodeGenerateQuery query) {
    String triggeredBy = query.getTriggeredBy() == null
        ? "system" : query.getTriggeredBy();
    ConflictStrategyEnum strategy = resolveStrategy(query.getConflictStrategy());
    GenHistory history = createHistory(
        query.getDatasourceId(), query.getTemplateGroupId(),
        query.getTableName(), triggeredBy);

    int successCount = 0;
    int skipCount = 0;
    int failCount = 0;
    List<GenHistoryFile> historyFiles =
        new ArrayList<>(HISTORY_FILE_LIST_CAPACITY);

    try {
      WriteStats stats = renderAndWrite(history, query, strategy, historyFiles);
      successCount = stats.successCount();
      skipCount = stats.skipCount();
      history.setFileCount(successCount + skipCount);
      history.setStatus((failCount > 0
          ? (successCount > 0 ? GenStatusEnum.PARTIAL : GenStatusEnum.FAILED)
          : GenStatusEnum.SUCCESS).getCode());
      history.setFinishedAt(LocalDateTime.now());
    } catch (Exception e) {
      log.error("代码生成失败 table={} err={}", query.getTableName(), e.getMessage(), e);
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
   * 批量生成数据源下全表。
   *
   * <p>读取数据源缓存的全部表元数据，然后并行调用
   * {@link #generateBatch(GenCodeGenerateQuery, List)}。
   *
   * @param datasourceId      数据源 ID
   * @param templateGroupId   模板分组 ID
   * @param outputDir         输出目录
   * @param conflictStrategy  冲突策略
   * @param triggeredBy       触发人
   * @return 生成结果汇总
   */
  public GenResultVO generateAll(
      Long datasourceId, Long templateGroupId, String outputDir,
      ConflictStrategyEnum conflictStrategy, String triggeredBy) {
    GenDatasource ds = datasourceService.getById(datasourceId);
    List<GenTableMeta> tables = tableMetadataService.listCachedTables(datasourceId);
    if (tables == null || tables.isEmpty()) {
      tables = tableMetadataService.refreshTables(ds);
    }
    List<String> tableNames = tables.stream()
        .map(GenTableMeta::getTableName)
        .collect(Collectors.toList());
    GenCodeGenerateQuery query = GenCodeGenerateQuery.builder()
        .datasourceId(datasourceId)
        .templateGroupId(templateGroupId)
        .outputDir(outputDir)
        .conflictStrategy(conflictStrategy == null ? null : conflictStrategy.name())
        .triggeredBy(triggeredBy)
        .build();
    return generateBatch(query, tableNames);
  }

  /**
   * 批量生成多个表（并行执行）。
   *
   * <p>使用独立短生命周期线程池并发处理多张表，线程数 = min(表数, CPU 核数)。
   * 池在方法退出前等待所有任务完成。任一表的生成失败不影响其他表，
   * 失败计数会被汇总到返回的 {@link GenResultVO} 中。
   *
   * @param query      基础生成参数（数据源、模板分组、输出目录、冲突策略、触发人）
   * @param tableNames 表名列表
   * @return 生成结果汇总
   */
  public GenResultVO generateBatch(GenCodeGenerateQuery query, List<String> tableNames) {

    int poolSize = Math.max(1, Math.min(tableNames.size(),
        Runtime.getRuntime().availableProcessors()));
    log.info("批量生成开始 count={} poolSize={}", tableNames.size(), poolSize);

    ExecutorService pool = ExecutorUtils.newFixedThreadPool(poolSize);
    AtomicInteger totalSuccess = new AtomicInteger();
    AtomicInteger totalSkip = new AtomicInteger();
    AtomicInteger totalFail = new AtomicInteger();
    Long[] firstHistoryId = new Long[1];

    try {
      List<Future<?>> futures = new ArrayList<>(tableNames.size());
      for (String tableName : tableNames) {
        futures.add(pool.submit(() -> {
          try {
            GenCodeGenerateQuery tableQuery = GenCodeGenerateQuery.builder()
                .datasourceId(query.getDatasourceId())
                .templateGroupId(query.getTemplateGroupId())
                .tableName(tableName)
                .outputDir(query.getOutputDir())
                .conflictStrategy(query.getConflictStrategy())
                .triggeredBy(query.getTriggeredBy())
                .build();
            GenResultVO r = generate(tableQuery);
            synchronized (firstHistoryId) {
              if (firstHistoryId[0] == null) {
                firstHistoryId[0] = r.getHistoryId();
              }
            }
            if (r.getSuccessCount() != null) {
              totalSuccess.addAndGet(r.getSuccessCount());
            }
            if (r.getSkipCount() != null) {
              totalSkip.addAndGet(r.getSkipCount());
            }
            if (r.getFailCount() != null) {
              totalFail.addAndGet(r.getFailCount());
            }
          } catch (Exception e) {
            log.error("批量生成单表失败 table={} err={}", tableName, e.getMessage());
            totalFail.incrementAndGet();
          }
        }));
      }

      for (Future<?> f : futures) {
        f.get(PER_TABLE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
      }
    } catch (Exception e) {
      log.error("批量生成失败: {}", e.getMessage(), e);
      totalFail.incrementAndGet();
    } finally {
      ExecutorUtils.shutdownGracefully(pool, POOL_SHUTDOWN_SECONDS, TimeUnit.SECONDS);
    }

    return GenResultVO.builder()
        .historyId(firstHistoryId[0])
        .fileCount(totalSuccess.get() + totalSkip.get() + totalFail.get())
        .successCount(totalSuccess.get())
        .skipCount(totalSkip.get())
        .failCount(totalFail.get())
        .build();
  }

  /**
   * 创建并保存生成历史记录（运行中状态）。
   *
   * @param datasourceId    数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName       表名
   * @param triggeredBy     触发人
   * @return 已持久化的历史记录
   */
  private GenHistory createHistory(
      Long datasourceId, Long templateGroupId, String tableName, String triggeredBy) {
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
    return historyRepository.save(history);
  }

  /**
   * 渲染并写入单表全部模板文件（不更新历史状态）。
   *
   * @param history      本次生成历史
   * @param query        生成请求
   * @param strategy     冲突策略
   * @param historyFiles 文件明细收集列表
   * @return 成功/跳过统计
   * @throws IOException 文件写入失败
   */
  private WriteStats renderAndWrite(
      GenHistory history, GenCodeGenerateQuery query,
      ConflictStrategyEnum strategy, List<GenHistoryFile> historyFiles) throws IOException {
    GenDatasource ds = datasourceService.getById(query.getDatasourceId());
    GenTableMeta tableMeta = tableMetadataService.getOrRefresh(ds, query.getTableName());
    List<GenColumnMeta> columns = tableMetadataService.refreshColumns(ds, tableMeta);
    List<GenTemplate> templates = templateService.listByGroup(query.getTemplateGroupId());

    Map<String, Object> tableCtx = codeGenEngine.buildTableContext(columns);
    Map<String, Object> context = codeGenEngine.buildContext(
        tableMeta.getModuleName(), defaultBasePackage, defaultAuthor,
        tableCtx, new HashMap<>());

    int successCount = 0;
    int skipCount = 0;
    for (GenTemplate tpl : templates) {
      String content = codeGenEngine.renderTemplate(tpl, context);
      String filePath = query.getOutputDir() + "/" + resolveOutputPath(tpl, tableMeta);
      String hash = codeGenEngine.computeHash(content);
      String action = writeFile(filePath, content, strategy);

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
    return new WriteStats(successCount, skipCount);
  }

  /**
   * 解析冲突策略字符串（空值默认 SKIP）。
   *
   * @param strategy 策略字符串
   * @return 冲突策略枚举
   */
  private ConflictStrategyEnum resolveStrategy(String strategy) {
    return strategy == null ? ConflictStrategyEnum.SKIP : ConflictStrategyEnum.valueOf(strategy);
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
      throws IOException {
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

  /** 单表文件写入统计（成功数/跳过数）。 */
  private record WriteStats(int successCount, int skipCount) {
  }
}
