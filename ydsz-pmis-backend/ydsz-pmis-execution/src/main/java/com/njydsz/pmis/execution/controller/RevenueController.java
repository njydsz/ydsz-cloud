package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.RevenueCreateDTO;
import com.njydsz.pmis.execution.entity.RevenueDO;
import com.njydsz.pmis.execution.service.RevenueService;
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

import java.util.List;
import java.util.Map;

/**
 * 收入确认 Controller
 *
 * <p>负责收入录入、确认、状态迁移及按项目/合同/周期的聚合查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "收入确认")
@RestController
@RequestMapping("/api/v1/execution/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService service;

    @Operation(summary = "录入收入")
    @PrePermission("execution:revenue:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RevenueCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "确认收入")
    @PrePermission("execution:revenue:update")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @RequestParam Long confirmedBy) {
        service.confirm(id, confirmedBy);
        return Result.ok();
    }

    @Operation(summary = "冲销收入")
    @PrePermission("execution:revenue:update")
    @PutMapping("/{id}/reverse")
    public Result<Void> reverse(@PathVariable Long id) {
        service.reverse(id);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:revenue:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:revenue:list")
    @GetMapping("/{id}")
    public Result<RevenueDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:revenue:list")
    @GetMapping("/page")
    public Result<Page<RevenueDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String period) {
        return Result.ok(service.page(page, size, keyword, status, contractId, initiationId, period));
    }

    @Operation(summary = "按合同汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-contract")
    public Result<List<Map<String, Object>>> sumByContract(@RequestParam Long contractId) {
        return Result.ok(service.sumByContract(contractId));
    }

    @Operation(summary = "按期间汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-period")
    public Result<List<Map<String, Object>>> sumByPeriod(@RequestParam Long initiationId) {
        return Result.ok(service.sumByPeriod(initiationId));
    }
}
