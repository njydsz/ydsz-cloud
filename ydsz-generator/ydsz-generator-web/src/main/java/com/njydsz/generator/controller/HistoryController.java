package com.njydsz.generator.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.service.GenHistoryService;

/**
 * 代码生成历史 REST 控制器（含回滚）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@ApiVersion("26.09.01")
@Secured("ROLE_GENERATOR_USER")
@RestController
@RequestMapping("/generator/history")
@RequiredArgsConstructor
public class HistoryController {

  private final GenHistoryService historyService;

  /**
   * 查询最近的代码生成任务记录（按时间倒序）。
   *
   * <p>返回的历史记录用于展示生成历史列表，每条记录包含任务基本信息
   * （数据源、模板分组、表名、操作人、生成文件数、时间等）。
   *
   * @param limit 返回数量上限，默认 20，最大值 100
   * @return 历史任务列表，按生成时间倒序排列
   */
  @GetMapping
  public YdszResponse<List<GenHistory>> listRecent(
      @RequestParam(defaultValue = "20") int limit) {
    return YdszResponse.success(historyService.listRecent(limit));
  }

  /**
   * 查询单个生成任务的详细信息。
   *
   * <p>包含数据源配置、模板分组、目标表、输出目录、冲突策略及生成结果统计。
   *
   * @param id 任务 ID
   * @return 任务实体，包含 datasourceId、templateGroupId、tableName、
   *         fileCount、successCount、datasource 等详细信息
   */
  @GetMapping("/{id}")
  public YdszResponse<GenHistory> getById(@PathVariable Long id) {
    return YdszResponse.success(historyService.getById(id));
  }

  /**
   * 查询生成任务的文件粒度明细。
   *
   * <p>列出本次任务涉及的所有文件路径及其生成状态（成功/跳过/失败）。
   *
   * @param id 任务 ID
   * @return 文件明细列表，每个元素包含 filePath（文件绝对路径）、
   *         fileType（文件类型：ENTITY/MAPPER/SERVICE/CONTROLLER 等）、
   *         generateStatus（生成状态：SUCCESS/SKIPPED/FAILED）
   */
  @GetMapping("/{id}/files")
  public YdszResponse<List<GenHistoryFile>> listFiles(@PathVariable Long id) {
    return YdszResponse.success(historyService.listFiles(id));
  }

  /**
   * 回滚指定的代码生成任务。
   *
   * <p>根据历史记录中保存的文件信息，将本次生成写入的文件恢复为生成前的状态：
   * 新生成的文件将被删除，被覆盖的文件将从备份中恢复（仅 OVERRIDE 策略有备份）。
   * SKIP 和 APPEND 策略生成的文件不会被此操作删除。
   *
   * @param id 任务 ID
   * @return 操作结果，成功时 data 为 null
   */
  @PostMapping("/{id}/rollback")
  @Audit(module = "生成历史", action = AuditAction.OTHER, content = "'回滚生成历史:' + #id")
  public YdszResponse<Void> rollback(@PathVariable Long id) {
    historyService.rollback(id);
    return YdszResponse.success(null);
  }

  /**
   * 删除指定的生成历史记录及其关联的文件明细。
   *
   * <p>仅删除历史记录和元数据，不会删除已生成到磁盘上的代码文件。
   * 如需恢复已生成文件，请先调用回滚接口。
   *
   * @param id 任务 ID
   * @return 操作结果，成功时 data 为 null
   */
  @DeleteMapping("/{id}")
  @Audit(module = "生成历史", action = AuditAction.DELETE, content = "'删除生成历史:' + #id")
  public YdszResponse<Void> delete(@PathVariable Long id) {
    historyService.deleteHistory(id);
    return YdszResponse.success(null);
  }
}
