package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.execution.dto.SimulationStatusDTO;
import com.njydsz.pmis.execution.entity.ProfitSimulationDO;
import com.njydsz.pmis.execution.service.ProfitSimulationService;
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

@Tag(name = "利润测算")
@RestController
@RequestMapping("/api/v1/execution/profit-simulation")
@RequiredArgsConstructor
public class ProfitSimulationController {

    private final ProfitSimulationService service;

    @Operation(summary = "创建测算版本")
    @PrePermission("execution:simulation:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProfitSimulationCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PrePermission("execution:simulation:approve")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody SimulationStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:simulation:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:simulation:list")
    @GetMapping("/{id}")
    public Result<ProfitSimulationDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "按项目查询所有版本")
    @PrePermission("execution:simulation:list")
    @GetMapping("/by-initiation")
    public Result<List<ProfitSimulationDO>> listByInitiation(@RequestParam Long initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    @Operation(summary = "多版本对比")
    @PrePermission("execution:simulation:list")
    @GetMapping("/compare")
    public Result<List<Map<String, Object>>> compare(@RequestParam Long initiationId) {
        return Result.ok(service.compare(initiationId));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:simulation:list")
    @GetMapping("/page")
    public Result<Page<ProfitSimulationDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String scenarioType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, initiationId, scenarioType, status));
    }
}
