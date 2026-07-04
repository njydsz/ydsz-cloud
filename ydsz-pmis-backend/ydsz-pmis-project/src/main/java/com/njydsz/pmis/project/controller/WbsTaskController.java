package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.project.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.project.entity.WbsTaskDO;
import com.njydsz.pmis.project.service.WbsTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务管理 Controller
 *
 * <p>负责任务的创建、状态迁移、进度更新、分页查询及项目整体进度计算。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "WBS 任务管理")
@RestController
@RequestMapping("/api/v1/execution/wbs")
@RequiredArgsConstructor
@Validated
public class WbsTaskController {

    private final WbsTaskService service;

    /**
     * 创建 WBS 任务
     *
     * @param dto 任务创建参数
     * @return 新建任务 ID
     */
    @Operation(summary = "创建 WBS 任务")
    @PrePermission("execution:wbs:create")
    @Idempotent(key = "wbs-task:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WbsTaskCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 变更任务状态
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "变更任务状态")
    @PrePermission("execution:wbs:status")
    @Idempotent(key = "wbs-task:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody WbsTaskStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 更新任务进度
     *
     * @param id           任务 ID
     * @param progressPct  进度百分比（0-100）
     * @param actualEffort 实际工时（人天），可选
     * @return 空结果
     */
    @Operation(summary = "更新任务进度")
    @PrePermission("execution:wbs:update")
    @PutMapping("/{id}/progress")
    public Result<Void> updateProgress(@PathVariable @Min(1) Longid,
                                   @RequestParam BigDecimal progressPct,
                                   @RequestParam(required = false) BigDecimal actualEffort) {
        service.updateProgress(id, progressPct, actualEffort);
        return Result.ok();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 空结果
     */
    @Operation(summary = "删除任务")
    @PrePermission("execution:wbs:delete")
    @Idempotent(key = "wbs-task:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Longid) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务实体
     */
    @Operation(summary = "任务详情")
    @PrePermission("execution:wbs:list")
    @GetMapping("/{id}")
    public Result<WbsTaskDO> get(@PathVariable @Min(1) Longid) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询任务
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（任务名称/编号）
     * @param status       状态过滤
     * @param taskType     任务类型
     * @param initiationId 项目立项 ID
     * @param ownerId      责任人 ID
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("execution:wbs:list")
    @GetMapping("/page")
    public Result<Page<WbsTaskDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long ownerId) {
        return Result.ok(service.page(page, size, keyword, status, taskType, initiationId, ownerId));
    }

    /**
     * 查询项目下的任务列表
     *
     * @param initiationId 项目立项 ID
     * @return 任务列表
     */
    @Operation(summary = "项目下的任务列表")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}")
    public Result<List<WbsTaskDO>> listByInitiation(@PathVariable @Min(1) LonginitiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    /**
     * 查询项目下的里程碑任务列表
     *
     * @param initiationId 项目立项 ID
     * @return 里程碑任务列表
     */
    @Operation(summary = "项目里程碑")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/milestones")
    public Result<List<WbsTaskDO>> listMilestones(@PathVariable @Min(1) LonginitiationId) {
        return Result.ok(service.listMilestones(initiationId));
    }

    /**
     * 计算项目整体进度（按工时加权）
     *
     * @param initiationId 项目立项 ID
     * @return 整体进度百分比（0-100）
     */
    @Operation(summary = "项目整体进度（按工时加权）")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/overall-progress")
    public Result<BigDecimal> overallProgress(@PathVariable @Min(1) LonginitiationId) {
        return Result.ok(service.calcOverallProgress(initiationId));
    }

    /**
     * 统计项目任务状态分布
     *
     * @param initiationId 项目立项 ID
     * @return 各状态任务数量列表
     */
    @Operation(summary = "状态分布")
    @PrePermission("execution:wbs:list")
    @GetMapping("/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam Long initiationId) {
        return Result.ok(service.aggregateByStatus(initiationId));
    }
}
