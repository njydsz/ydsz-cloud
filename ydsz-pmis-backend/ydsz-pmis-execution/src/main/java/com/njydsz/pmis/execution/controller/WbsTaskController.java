package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.execution.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.execution.entity.WbsTaskDO;
import com.njydsz.pmis.execution.service.WbsTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class WbsTaskController {

    private final WbsTaskService service;

    @Operation(summary = "创建 WBS 任务")
    @PrePermission("execution:wbs:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WbsTaskCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "变更任务状态")
    @PrePermission("execution:wbs:status")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody WbsTaskStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "更新任务进度")
    @PrePermission("execution:wbs:update")
    @PutMapping("/{id}/progress")
    public Result<Void> updateProgress(@PathVariable Long id,
                                   @RequestParam BigDecimal progressPct,
                                   @RequestParam(required = false) BigDecimal actualEffort) {
        service.updateProgress(id, progressPct, actualEffort);
        return Result.ok();
    }

    @Operation(summary = "删除任务")
    @PrePermission("execution:wbs:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "任务详情")
    @PrePermission("execution:wbs:list")
    @GetMapping("/{id}")
    public Result<WbsTaskDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @PrePermission("execution:wbs:list")
    @GetMapping("/page")
    public Result<Page<WbsTaskDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long ownerId) {
        return Result.ok(service.page(page, size, keyword, status, taskType, initiationId, ownerId));
    }

    @Operation(summary = "项目下的任务列表")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}")
    public Result<List<WbsTaskDO>> listByInitiation(@PathVariable Long initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    @Operation(summary = "项目里程碑")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/milestones")
    public Result<List<WbsTaskDO>> listMilestones(@PathVariable Long initiationId) {
        return Result.ok(service.listMilestones(initiationId));
    }

    @Operation(summary = "项目整体进度（按工时加权）")
    @PrePermission("execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/overall-progress")
    public Result<BigDecimal> overallProgress(@PathVariable Long initiationId) {
        return Result.ok(service.calcOverallProgress(initiationId));
    }

    @Operation(summary = "状态分布")
    @PrePermission("execution:wbs:list")
    @GetMapping("/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam Long initiationId) {
        return Result.ok(service.aggregateByStatus(initiationId));
    }
}
