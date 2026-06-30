package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.BudgetItemDTO;
import com.njydsz.pmis.project.dto.GateReviewDTO;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.BudgetItemDO;
import com.njydsz.pmis.project.entity.GateReviewDO;
import com.njydsz.pmis.project.entity.InitiationDO;
import com.njydsz.pmis.project.service.InitiationService;
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
 * 立项管理 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "立项管理")
@RestController
@RequestMapping("/api/v1/project/initiation")
@RequiredArgsConstructor
public class InitiationController {

    private final InitiationService service;

    @Operation(summary = "提交立项")
    @PostMapping
    public R<Long> create(@Valid @RequestBody InitiationCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "阶段迁移")
    @PutMapping("/stage")
    public R<Void> changeStage(@Valid @RequestBody InitiationStageDTO dto) {
        service.changeStage(dto);
        return R.ok();
    }

    @Operation(summary = "删除立项")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "立项详情")
    @GetMapping("/{id}")
    public R<InitiationDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<InitiationDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String projectLevel,
            @RequestParam(required = false) Long pmId) {
        return R.ok(service.page(page, size, keyword, stage, projectLevel, pmId));
    }

    // ============= 预算 =============

    @Operation(summary = "新增预算明细")
    @PostMapping("/budget")
    public R<Long> addBudget(@Valid @RequestBody BudgetItemDTO dto) {
        return R.ok(service.addBudgetItem(dto));
    }

    @Operation(summary = "删除预算明细")
    @DeleteMapping("/budget/{id}")
    public R<Void> delBudget(@PathVariable Long id) {
        service.deleteBudgetItem(id);
        return R.ok();
    }

    @Operation(summary = "预算明细列表")
    @GetMapping("/{id}/budget")
    public R<List<BudgetItemDO>> listBudget(@PathVariable Long id) {
        return R.ok(service.listBudget(id));
    }

    @Operation(summary = "预算按分类汇总")
    @GetMapping("/{id}/budget/summary")
    public R<List<Map<String, Object>>> sumBudget(@PathVariable Long id) {
        return R.ok(service.sumBudgetByCategory(id));
    }

    @Operation(summary = "重新汇总预算总额")
    @PostMapping("/{id}/budget/recompute")
    public R<BigDecimal> recomputeBudget(@PathVariable Long id) {
        return R.ok(service.recomputeBudget(id));
    }

    // ============= 门径 =============

    @Operation(summary = "门径评审")
    @PostMapping("/gate/review")
    public R<Long> reviewGate(@Valid @RequestBody GateReviewDTO dto) {
        return R.ok(service.reviewGate(dto));
    }

    @Operation(summary = "门径评审记录")
    @GetMapping("/{id}/gate/reviews")
    public R<List<GateReviewDO>> listGateReviews(@PathVariable Long id) {
        return R.ok(service.listGateReviews(id));
    }

    // ============= 统计 =============

    @Operation(summary = "按阶段聚合")
    @GetMapping("/aggregate/stage")
    public R<List<Map<String, Object>>> aggregateByStage(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByStage(tenantId));
    }

    @Operation(summary = "查询立项预算（供执行模块调用）")
    @GetMapping("/{id}/budget/snapshot")
    public R<java.math.Map<String, Object>> budgetSnapshot(@PathVariable Long id) {
        return R.ok(service.budgetSnapshot(id));
    }
}
