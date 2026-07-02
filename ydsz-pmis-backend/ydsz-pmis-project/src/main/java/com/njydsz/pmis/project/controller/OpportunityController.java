package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.service.OpportunityService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商机 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "商机管理")
@RestController
@RequestMapping("/api/v1/project/opportunity")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunityService service;

    @Operation(summary = "创建商机")
    @PrePermission("project:opportunity:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody OpportunityCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "更新商机")
    @PrePermission("project:opportunity:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody OpportunityUpdateDTO dto) {
        service.update(dto);
        return Result.ok();
    }

    @Operation(summary = "变更状态")
    @PrePermission("project:opportunity:update")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody OpportunityStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "删除商机")
    @PrePermission("project:opportunity:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "商机详情")
    @PrePermission("project:opportunity:list")
    @GetMapping("/{id}")
    public Result<OpportunityDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @PrePermission("project:opportunity:list")
    @GetMapping("/page")
    public Result<Page<OpportunityDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Long ownerId) {
        return Result.ok(service.page(page, size, keyword, status, level, ownerId));
    }

    @Operation(summary = "评估并更新赢率")
    @PrePermission("project:opportunity:evaluate")
    @PostMapping("/{id}/evaluate-winrate")
    public Result<BigDecimal> evaluateWinRate(@PathVariable Long id,
                                         @RequestParam(required = false) String customerCredit,
                                         @RequestParam(defaultValue = "false") boolean hasHistory) {
        return Result.ok(service.evaluateWinRate(id, customerCredit, hasHistory));
    }

    @Operation(summary = "按状态聚合")
    @PrePermission("project:opportunity:list")
    @GetMapping("/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByStatus(tenantId));
    }

    @Operation(summary = "按分级聚合")
    @PrePermission("project:opportunity:list")
    @GetMapping("/aggregate/level")
    public Result<List<Map<String, Object>>> aggregateByLevel(@RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByLevel(tenantId));
    }

    @Operation(summary = "商机转立项自动化(WON -> CONVERTED + 创建预立项草稿)")
    @PrePermission("project:opportunity:convert")
    @PostMapping("/{id}/convert-to-initiation")
    public Result<Long> convertToInitiation(@PathVariable Long id,
                                        @RequestParam(required = false) Long sponsorId,
                                        @RequestParam(required = false) Long pmId) {
        return Result.ok(service.convertToInitiation(id, sponsorId, pmId));
    }
}
