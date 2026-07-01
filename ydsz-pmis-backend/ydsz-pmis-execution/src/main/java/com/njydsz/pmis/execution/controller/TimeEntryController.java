package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.TimeEntryApprovalDTO;
import com.njydsz.pmis.execution.dto.TimeEntryCreateDTO;
import com.njydsz.pmis.execution.entity.TimeEntryDO;
import com.njydsz.pmis.execution.service.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "工时管理")
@RestController
@RequestMapping("/api/v1/execution/time-entry")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService service;

    @Operation(summary = "录入工时")
    @PostMapping
    public R<Long> create(@Valid @RequestBody TimeEntryCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "提交工时审批")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return R.ok();
    }

    @Operation(summary = "审批工时")
    @PutMapping("/approve")
    public R<Void> approve(@Valid @RequestBody TimeEntryApprovalDTO dto) {
        service.approve(dto);
        return R.ok();
    }

    @Operation(summary = "删除工时")
    @PrePermission("execution:time:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "工时详情")
    @GetMapping("/{id}")
    public R<TimeEntryDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<TimeEntryDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.page(page, size, keyword, status, employeeId, initiationId, taskId, from, to));
    }

    @Operation(summary = "项目工时按人员+职级聚合")
    @GetMapping("/aggregate/by-employee-level")
    public R<List<Map<String, Object>>> aggregateByEmployeeLevel(
            @RequestParam Long initiationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.aggregateHoursByEmployeeAndLevel(initiationId, from, to));
    }

    @Operation(summary = "跨项目冲突检测")
    @PrePermission("execution:time:list")
    @GetMapping("/conflict")
    public R<List<Map<String, Object>>> detectCrossProject(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate) {
        return R.ok(service.detectCrossProject(employeeId, entryDate));
    }
}
