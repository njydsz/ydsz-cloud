package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.RateInternalCreateDTO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.service.RateInternalService;
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

@Tag(name = "对内成本费率")
@RestController
@RequestMapping("/api/v1/execution/rate-internal")
@RequiredArgsConstructor
public class RateInternalController {

    private final RateInternalService service;

    @Operation(summary = "创建对内成本费率")
    @PrePermission("execution:rate-internal:create")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RateInternalCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "更新")
    @PrePermission("execution:rate-internal:update")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RateInternalCreateDTO dto) {
        service.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:rate-internal:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:rate:list")
    @GetMapping("/{id}")
    public R<RateInternalDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "命中有效成本费率（职级+部门+日期）")
    @PrePermission("execution:rate:list")
    @GetMapping("/match")
    public R<RateInternalDO> match(
            @RequestParam String levelCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(service.matchEffective(levelCode, departmentId, date));
    }

    @Operation(summary = "按职级+部门查询")
    @PrePermission("execution:rate:list")
    @GetMapping("/by-level-dept")
    public R<List<RateInternalDO>> listByLevelAndDept(
            @RequestParam String levelCode,
            @RequestParam(required = false) Long departmentId) {
        return R.ok(service.listByLevelAndDept(levelCode, departmentId));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:rate:list")
    @GetMapping("/page")
    public R<Page<RateInternalDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status) {
        return R.ok(service.page(page, size, levelCode, departmentId, status));
    }
}
