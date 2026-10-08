package com.njydsz.cronjob.web.controller.export;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.cronjob.domain.entity.export.ExportTask;
import com.njydsz.cronjob.server.service.export.ExportTaskService;

/**
 * 异步导出任务 REST 控制器。
 *
 * <p>提供任务提交、进度查询、下载、取消等端到端异步导出能力。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
@Slf4j
@Tag(name = "异步导出任务")
@ApiVersion("26.10.13")
@RestController
@RequestMapping("/cronjob/export-tasks")
@RequiredArgsConstructor
public class ExportTaskController {

  private static final String MODULE_CRONJOB = "cronjob";

  private final ExportTaskService exportTaskService;

  /**
   * 提交导出任务（异步）。
   *
   * @param request 提交参数（taskType/taskName/params）
   * @return 任务 ID
   */
  @Operation(summary = "提交异步导出任务")
  @PostMapping
  public YdszResponse<String> submit(@RequestBody Map<String, Object> request) {
    String taskType = (String) request.getOrDefault("taskType", "DEFAULT_EXPORT");
    String taskName = (String) request.getOrDefault("taskName", "导出任务");
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", Map.of());

    String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
    String userId = AuthContextUtils.getUserId();

    String taskId = exportTaskService.submitTask(
        MODULE_CRONJOB, taskType, taskName, params, userId, tenantId);

    return YdszResponse.success(taskId);
  }

  /**
   * 查询任务状态和进度。
   *
   * @param id 任务 ID
   * @return 任务详情（含进度、下载链接）
   */
  @Operation(summary = "查询导出任务状态")
  @GetMapping("/{id}")
  public YdszResponse<ExportTask> getTask(@PathVariable String id) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
    return YdszResponse.success(exportTaskService.getTask(id, tenantId));
  }

  /**
   * 查询当前用户的导出任务列表。
   *
   * @param limit 返回数量上限（默认 20）
   * @return 任务列表
   */
  @Operation(summary = "查询用户导出任务列表")
  @GetMapping("/my")
  public YdszResponse<List<ExportTask>> listMyTasks(
      @RequestParam(defaultValue = "20") int limit) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(exportTaskService.listUserTasks(tenantId, userId, limit));
  }

  /**
   * 取消导出任务。
   *
   * @param id 任务 ID
   * @return 操作结果
   */
  @Operation(summary = "取消导出任务")
  @DeleteMapping("/{id}")
  public YdszResponse<Boolean> cancel(@PathVariable String id) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
    String userId = AuthContextUtils.getUserId();
    return YdszResponse.success(exportTaskService.cancelTask(id, userId, tenantId));
  }

  /**
   * 下载已完成任务的导出文件。
   *
   * <p>返回临时下载链接，前端跳转下载。
   *
   * @param id 任务 ID
   * @return 下载 URL
   */
  @Operation(summary = "获取导出文件下载链接")
  @GetMapping("/{id}/download")
  public YdszResponse<String> getDownloadUrl(@PathVariable String id) {
    String tenantId = AuthContextUtils.getTenantIdOrDefault("1");
    ExportTask task = exportTaskService.getTask(id, tenantId);
    if (task == null) {
      return YdszResponse.error("任务不存在");
    }
    if (!"SUCCEEDED".equals(task.getStatus())) {
      return YdszResponse.error("任务未完成，当前状态: " + task.getStatus());
    }
    return YdszResponse.success(task.getDownloadUrl());
  }
}
