package com.njydsz.cronjob.web.controller.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.domain.vo.JobTaskVO;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;

/**
 * MapReduce 子任务查询 Controller（P0-4）。
 *
 * <p>提供按 logId 查询子任务列表、分页查询子任务、查询子任务执行进度等 HTTP 接口， 供前端展示 MapReduce 任务的子任务执行明细。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #list} - 查询指定执行日志的全部子任务（无分页）
 *   <li>{@link #page} - 分页查询子任务（按 created_at 升序）
 *   <li>{@link #progress} - 查询子任务执行进度（各状态数量汇总 + 进度百分比）
 * </ul>
 *
 * <p>注意：子任务分片策略由 {@code MapTaskExecutor} 决定，本 Controller 仅负责查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "MapReduce 子任务查询", description = "子任务列表/分页/进度汇总")
@RestController
@RequestMapping("/api/v1/cronjob/task")
@RequiredArgsConstructor
@Validated
public class JobTaskController {

  /** MapReduce 子任务 Mapper */
  private final JobTaskMapper jobTaskMapper;

  /**
   * 查询指定执行日志的子任务列表。
   *
   * <p>无分页，适用于子任务数量较少的场景（建议 &lt;1000 条）； 大量子任务请使用 {@link #page} 分页查询。
   *
   * @param logId 执行日志 ID
   * @return 子任务列表（按 created_at 升序）
   */
  @Operation(summary = "查询子任务列表")
  @GetMapping("/list")
  public BaseResponse<List<JobTaskVO>> list(@RequestParam String logId) {
    return BaseResponse.success(
        CronjobConverter.INSTANT.jobTaskListToVO(jobTaskMapper.selectByLogId(logId)));
  }

  /**
   * 分页查询子任务。
   *
   * <p>按 created_at 升序排列，最先生成的子任务排在前面（便于复现执行顺序）。 软删除的子任务（{@code deleted=1}）会被过滤。
   *
   * @param logId 执行日志 ID
   * @param page 页码（默认 1，最小 1）
   * @param size 每页条数（默认 20，最大 100）
   * @return 子任务分页数据
   */
  @Operation(summary = "分页查询子任务")
  @GetMapping("/page")
  public PageResponse<List<JobTaskVO>> page(
      @RequestParam String logId,
      @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}")
          int page,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "{validation.cronjob.msg_15154512}")
          @Max(100)
          int size) {
    Page<JobTask> pageObj = new Page<>(page, size);
    LambdaQueryWrapper<JobTask> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(JobTask::getLogId, logId)
        .eq(JobTask::getDeleted, 0)
        .orderByAsc(JobTask::getCreatedAt);
    Page<JobTask> result = jobTaskMapper.selectPage(pageObj, wrapper);
    return PageResponses.success(result, CronjobConverter.INSTANT::entityToVO);
  }

  /**
   * P0-A3: 查询子任务执行进度。
   *
   * <p>对标 XXL-Job 子任务进度页和 PowerJob InstanceDetail.taskList，
   * 返回各状态子任务数量汇总（total/pending/running/success/failed）以及完成百分比， 便于前端渲染进度条。
   *
   * @param logId 执行日志 ID
   * @return 进度汇总（total/pending/running/success/failed/progressPercent）
   */
  @Operation(summary = "查询子任务执行进度")
  @GetMapping("/progress")
  public BaseResponse<Map<String, Object>> progress(@RequestParam String logId) {
    List<JobTask> all = jobTaskMapper.selectByLogId(logId);
    Map<String, Object> result = new HashMap<>();
    int total = all.size();
    int pending = 0, running = 0, success = 0, failed = 0;
    for (JobTask task : all) {
      String status = task.getStatus();
      if ("PENDING".equals(status)) {
        pending++;
      } else if ("RUNNING".equals(status)) {
        running++;
      } else if ("SUCCESS".equals(status)) {
        success++;
      } else if ("FAILED".equals(status)) {
        failed++;
      }
    }
    result.put("total", total);
    result.put("pending", pending);
    result.put("running", running);
    result.put("success", success);
    result.put("failed", failed);
    result.put("progressPercent", total > 0 ? (int) ((success + failed) * 100.0 / total) : 0);
    return BaseResponse.success(result);
  }
}
