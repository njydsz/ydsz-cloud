package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.service.EvmMeasureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "EVM 挣值管理")
@RestController
@RequestMapping("/api/v1/execution/evm")
@RequiredArgsConstructor
public class EvmController {

    private final EvmMeasureService service;

    @Operation(summary = "录入/更新 EVM 测量（按 initiation+wbs+period 幂等）")
    @PrePermission("execution:evm:save")
    @PostMapping
    public R<Long> save(@Valid @RequestBody EvmMeasureCreateDTO dto) {
        return R.ok(service.save(dto));
    }

    @Operation(summary = "详情")
    @PrePermission("execution:evm:list")
    @GetMapping("/{id}")
    public R<EvmMeasureDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "按项目查询")
    @PrePermission("execution:evm:list")
    @GetMapping("/by-initiation")
    public R<List<EvmMeasureDO>> listByInitiation(@RequestParam Long initiationId) {
        return R.ok(service.listByInitiation(initiationId));
    }

    @Operation(summary = "按 WBS 查询")
    @PrePermission("execution:evm:list")
    @GetMapping("/by-wbs")
    public R<List<EvmMeasureDO>> listByWbs(@RequestParam Long wbsTaskId) {
        return R.ok(service.listByWbs(wbsTaskId));
    }

    @Operation(summary = "项目偏差趋势（按周期）")
    @PrePermission("execution:evm:list")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam Long initiationId) {
        return R.ok(service.trend(initiationId));
    }

    @Operation(summary = "项目 EVM 健康仪表盘")
    @PrePermission("execution:evm:dashboard")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(@RequestParam Long initiationId) {
        return R.ok(service.dashboard(initiationId));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:evm:list")
    @GetMapping("/page")
    public R<Page<EvmMeasureDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String alertLevel) {
        return R.ok(service.page(page, size, initiationId, alertLevel));
    }

    @Operation(summary = "删除")
    @PrePermission("execution:evm:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
