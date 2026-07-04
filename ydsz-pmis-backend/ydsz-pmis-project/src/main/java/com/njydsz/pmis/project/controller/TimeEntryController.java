package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.project.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.service.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 工时管理 Controller
 *
 * <p>负责工时录入、审批、聚合查询及跨项目冲突检测。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "工时管理")
@RestController
@RequestMapping("/api/v1/execution/time-entry")
@RequiredArgsConstructor
@Validated
public class TimeEntryController {

    private final TimeEntryService service;

    /**
     * 录入工时
     *
     * @param dto 工时录入参数
     * @return 新建工时记录 ID
     */
    @Operation(summary = "录入工时")
    @PrePermission("execution:time:create")
    @Idempotent(key = "time-entry:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TimeEntryCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 提交工时审批
     *
     * @param id 工时记录 ID
     * @return 空结果
     */
    @Operation(summary = "提交工时审批")
    @PrePermission("execution:time:approve")
    @Idempotent(key = "time-entry:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable @Min(1) Longid) {
        service.submit(id);
        return Result.ok();
    }

    /**
     * 审批工时
     *
     * @param dto 工时审批参数
     * @return 空结果
     */
    @Operation(summary = "审批工时")
    @PrePermission("execution:time:approve")
    @PutMapping("/approve")
    public Result<Void> approve(@Valid @RequestBody TimeEntryApprovalDTO dto) {
        service.approve(dto);
        return Result.ok();
    }

    /**
     * 删除工时
     *
     * @param id 工时记录 ID
     * @return 空结果
     */
    @Operation(summary = "删除工时")
    @PrePermission("execution:time:delete")
    @Idempotent(key = "time-entry:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Longid) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询工时详情
     *
     * @param id 工时记录 ID
     * @return 工时实体
     */
    @Operation(summary = "工时详情")
    @PrePermission("execution:time:list")
    @GetMapping("/{id}")
    public Result<TimeEntryDO> get(@PathVariable @Min(1) Longid) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询工时
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param employeeId   员工 ID
     * @param initiationId 项目立项 ID
     * @param taskId       任务 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("execution:time:list")
    @GetMapping("/page")
    public Result<Page<TimeEntryDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.page(page, size, keyword, status, employeeId, initiationId, taskId, from, to));
    }

    /**
     * 按人员+职级聚合项目工时
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           截止日期
     * @return 聚合结果列表
     */
    @Operation(summary = "项目工时按人员+职级聚合")
    @PrePermission("execution:time:list")
    @GetMapping("/aggregate/by-employee-level")
    public Result<List<Map<String, Object>>> aggregateByEmployeeLevel(
            @RequestParam Long initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(service.aggregateHoursByEmployeeAndLevel(initiationId, from, to));
    }

    /**
     * 跨项目工时冲突检测
     *
     * @param employeeId 员工 ID
     * @param entryDate  工时日期
     * @return 冲突列表
     */
    @Operation(summary = "跨项目冲突检测")
    @PrePermission("execution:time:list")
    @GetMapping("/conflict")
    public Result<List<Map<String, Object>>> detectCrossProject(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate) {
        return Result.ok(service.detectCrossProject(employeeId, entryDate));
    }
}
