package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ApprovalDTO;
import com.njydsz.pmis.project.dto.ExpenseCreateDTO;
import com.njydsz.pmis.project.entity.ExpenseDO;
import com.njydsz.pmis.project.service.ExpenseService;
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
@Validated
public class ExpenseController {

    private final ExpenseService service;

    /**
     * 创建费用
     *
     * @param dto 费用创建参数
     * @return 新建费用 ID
     */
    @Operation(summary = "创建费用")
    @PrePermission("execution:expense:create")
    @Idempotent(key = "expense:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ExpenseCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 费用状态迁移
     *
     * @param dto 审批/状态变更参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @PrePermission("execution:expense:status")
    @Idempotent(key = "expense:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody ApprovalDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 删除费用
     *
     * @param id 费用 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:expense:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Longid) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询费用详情
     *
     * @param id 费用 ID
     * @return 费用实体
     */
    @Operation(summary = "详情")
    @PrePermission("execution:expense:list")
    @GetMapping("/{id}")
    public Result<ExpenseDO> get(@PathVariable @Min(1) Longid) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询费用
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param expenseType  费用类型
     * @param employeeId   员工 ID
     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:expense:list")
    @GetMapping("/page")
    public Result<Page<ExpenseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String expenseType,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long initiationId) {
        return Result.ok(service.page(page, size, keyword, status, expenseType, employeeId, initiationId));
    }
}
