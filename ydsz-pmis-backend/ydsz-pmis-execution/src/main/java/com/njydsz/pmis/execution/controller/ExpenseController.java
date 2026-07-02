package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.ExpenseCreateDTO;
import com.njydsz.pmis.execution.entity.ExpenseDO;
import com.njydsz.pmis.execution.service.ExpenseService;
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

/**
 * 费用报销 Controller
 *
 * <p>负责费用创建、审批、状态迁移及分页查询；受预算强管控约束。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "费用报销")
@RestController
@RequestMapping("/api/v1/execution/expense")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    @Operation(summary = "创建费用")
    @PrePermission("execution:expense:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ExpenseCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PrePermission("execution:expense:status")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody ApprovalDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:expense:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:expense:list")
    @GetMapping("/{id}")
    public Result<ExpenseDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:expense:list")
    @GetMapping("/page")
    public Result<Page<ExpenseDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId) {
        return Result.ok(service.page(page, size, keyword, status, expenseType, employeeId, initiationId));
    }
}
