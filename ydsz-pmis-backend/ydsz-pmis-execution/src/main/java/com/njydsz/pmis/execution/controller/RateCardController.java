package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.dto.RateCardCreateDTO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.service.RateCardService;
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

@Tag(name = "对外报价费率 Rate Card")
@RestController
@RequestMapping("/api/v1/execution/rate-card")
@RequiredArgsConstructor
public class RateCardController {

    private final RateCardService service;

    @Operation(summary = "创建对外报价费率")
    @PrePermission("execution:rate-card:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RateCardCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "更新")
    @PrePermission("execution:rate-card:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RateCardCreateDTO dto) {
        service.update(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("execution:rate-card:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("execution:rate:list")
    @GetMapping("/{id}")
    public Result<RateCardDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "命中有效费率（职级+项目类型+客户等级+日期）")
    @PrePermission("execution:rate:list")
    @GetMapping("/match")
    public Result<RateCardDO> match(
            @RequestParam String levelCode,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(service.matchEffective(levelCode, projectType, customerLevel, date));
    }

    @Operation(summary = "按职级查询")
    @PrePermission("execution:rate:list")
    @GetMapping("/by-level")
    public Result<List<RateCardDO>> listByLevel(@RequestParam String levelCode) {
        return Result.ok(service.listByLevel(levelCode));
    }

    @Operation(summary = "分页")
    @PrePermission("execution:rate:list")
    @GetMapping("/page")
    public Result<Page<RateCardDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String levelCode,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, levelCode, status));
    }
}
