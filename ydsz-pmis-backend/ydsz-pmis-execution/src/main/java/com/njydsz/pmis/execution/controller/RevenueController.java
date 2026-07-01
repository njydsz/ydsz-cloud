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

@Tag(name = "收入确认")
@RestController
@RequestMapping("/api/v1/execution/revenue")
@RequiredArgsConstructor
public class RevenueController {

    private final RevenueService service;

    @Operation(summary = "录入收入")
    @PrePermission("execution:revenue:create")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RevenueCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "确认收入")
    @PrePermission("execution:revenue:update")
    @PutMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam Long confirmedBy) {
        service.confirm(id, confirmedBy);
        return R.ok();
    }

    @Operation(summary = "冲销收入")
    @PrePermission("execution:revenue:update")
    @PutMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        service.reverse(id);
        return R.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:revenue:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:revenue:list")
    @GetMapping("/{id}")
    public R<RevenueDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:revenue:list")
    @GetMapping("/page")
    public R<Page<RevenueDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String period) {
        return R.ok(service.page(page, size, keyword, status, contractId, initiationId, period));
    }

    @Operation(summary = "按合同汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-contract")
    public R<List<Map<String, Object>>> sumByContract(@RequestParam Long contractId) {
        return R.ok(service.sumByContract(contractId));
    }

    @Operation(summary = "按期间汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-period")
    public R<List<Map<String, Object>>> sumByPeriod(@RequestParam Long initiationId) {
        return R.ok(service.sumByPeriod(initiationId));
    }
}
