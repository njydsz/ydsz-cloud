package com.njydsz.cronjob.web.controller.job;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.cronjob.domain.entity.job.JobTaskDO;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * MapReduce 子任务查询 Controller（P0-4）。
 *
 * <p>提供按 logId 查询子任务列表、分页查询子任务等 HTTP 接口，
 * 供前端展示 MapReduce 任务的子任务执行明细。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "MapReduce 子任务查询")
@RestController
@RequestMapping("/cronjob/task")
@RequiredArgsConstructor
@Validated
public class JobTaskController {

    /** MapReduce 子任务 Mapper */
    private final JobTaskMapper jobTaskMapper;

    /**
     * 查询指定执行日志的子任务列表。
     *
     * @param logId 执行日志 ID
     * @return 统一响应结果，包含子任务列表
     */
    @Operation(summary = "查询子任务列表")
    @GetMapping("/list")
    public BaseResponse<List<JobTaskDO>> list(@RequestParam String logId) {
        return BaseResponse.success(jobTaskMapper.selectByLogId(logId));
    }

    /**
     * 分页查询子任务。
     *
     * @param logId 执行日志 ID
     * @param page  页码（默认 1）
     * @param size  每页条数（默认 20）
     * @return 统一响应结果，包含子任务分页数据
     */
    @Operation(summary = "分页查询子任务")
    @GetMapping("/page")
    public BaseResponse<Page<JobTaskDO>> page(
            @RequestParam String logId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size) {
        Page<JobTaskDO> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<JobTaskDO> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(JobTaskDO::getLogId, logId)
                .eq(JobTaskDO::getDeleted, 0)
                .orderByAsc(JobTaskDO::getCreatedAt);
        return BaseResponse.success(jobTaskMapper.selectPage(pageObj, wrapper));
    }

    /**
     * P0-A3: 查询子任务执行进度。
     *
     * <p>对标 XXL-Job 子任务进度页和 PowerJob InstanceDetail.taskList，
     * 返回各状态子任务数量汇总，便于前端渲染进度条。
     *
     * @param logId 执行日志 ID
     * @return 进度汇总（total/pending/running/success/failed）
     */
    @Operation(summary = "查询子任务执行进度")
    @GetMapping("/progress")
    public BaseResponse<java.util.Map<String, Object>> progress(@RequestParam String logId) {
        java.util.List<JobTaskDO> all = jobTaskMapper.selectByLogId(logId);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        int total = all.size();
        int pending = 0, running = 0, success = 0, failed = 0;
        for (JobTaskDO task : all) {
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
