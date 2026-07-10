package com.njydsz.pmis.cronjob.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.cronjob.entity.job.JobTaskDO;
import com.njydsz.pmis.cronjob.mapper.job.JobTaskMapper;
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

import java.util.List;

/**
 * MapReduce 子任务查询 Controller（P0-4）。
 *
 * <p>提供按 logId 查询子任务列表、分页查询子任务等 HTTP 接口，
 * 供前端展示 MapReduce 任务的子任务执行明细。
 *
 * @author ydsz-pmis-team
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
    public Result<List<JobTaskDO>> list(@RequestParam String logId) {
        return Result.ok(jobTaskMapper.selectByLogId(logId));
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
    public Result<Page<JobTaskDO>> page(
            @RequestParam String logId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.cronjob.msg_e648fb78}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.cronjob.msg_15154512}") @Max(100) int size) {
        Page<JobTaskDO> pageObj = new Page<>(page, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobTaskDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(JobTaskDO::getLogId, logId)
                .eq(JobTaskDO::getDeleted, 0)
                .orderByAsc(JobTaskDO::getCreatedAt);
        return Result.ok(jobTaskMapper.selectPage(pageObj, wrapper));
    }
}
