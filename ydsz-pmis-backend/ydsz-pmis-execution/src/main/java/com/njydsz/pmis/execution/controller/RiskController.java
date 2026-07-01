package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.RiskCreateDTO;
import com.njydsz.pmis.execution.dto.RiskStatusDTO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.service.RiskService;
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

@Tag(name = "项目风险")
@RestController
@RequestMapping("/api/v1/execution/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService service;

    @Operation(summary = "登记风险")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RiskCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PutMapping("/status")
    public R<Void> changeStatus(@Valid @RequestBody RiskStatusDTO dto) {
        service.changeStatus(dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<RiskDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:risk:list")
    @GetMapping("/page")
    public R<Page<RiskDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Long initiationId) {
        return R.ok(service.page(page, size, keyword, status, riskLevel, initiationId));
    }

    @Operation(summary = "按等级聚合")
    @PrePermission("execution:risk:list")
    @GetMapping("/aggregate/by-level")
    public R<List<Map<String, Object>>> aggregateByLevel(@RequestParam Long initiationId) {
        return R.ok(service.aggregateByLevel(initiationId));
    }
}
