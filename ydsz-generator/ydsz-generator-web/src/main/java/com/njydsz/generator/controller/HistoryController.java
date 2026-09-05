package com.njydsz.generator.controller;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.service.GenHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 代码生成历史 REST 控制器（含回滚）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/generator/history")
@RequiredArgsConstructor
public class HistoryController {

  private final GenHistoryService historyService;

  /**
   * 查询最近 N 条任务记录。
   *
   * @param limit 数量上限（默认 20）
   * @return 历史列表
   */
  @GetMapping
  public YdszResponse<List<GenHistory>> listRecent(
      @RequestParam(defaultValue = "20") int limit) {
    return YdszResponse.success(historyService.listRecent(limit));
  }

  /**
   * 查询任务详情。
   *
   * @param id 任务 ID
   * @return 任务实体
   */
  @GetMapping("/{id}")
  public YdszResponse<GenHistory> getById(@PathVariable Long id) {
    return YdszResponse.success(historyService.getById(id));
  }

  /**
   * 查询任务文件明细。
   *
   * @param id 任务 ID
   * @return 文件列表
   */
  @GetMapping("/{id}/files")
  public YdszResponse<List<GenHistoryFile>> listFiles(@PathVariable Long id) {
    return YdszResponse.success(historyService.listFiles(id));
  }

  /**
   * 回滚任务（恢复/删除文件）。
   *
   * @param id 任务 ID
   * @return 操作结果
   */
  @PostMapping("/{id}/rollback")
  public YdszResponse<Void> rollback(@PathVariable Long id) {
    historyService.rollback(id);
    return YdszResponse.success(null);
  }

  /**
   * 删除历史记录。
   *
   * @param id 任务 ID
   * @return 操作结果
   */
  @DeleteMapping("/{id}")
  public YdszResponse<Void> delete(@PathVariable Long id) {
    historyService.deleteHistory(id);
    return YdszResponse.success(null);
  }
}
