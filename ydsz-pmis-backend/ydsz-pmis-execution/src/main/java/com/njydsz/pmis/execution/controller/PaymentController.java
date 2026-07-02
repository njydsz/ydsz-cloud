package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.PaymentAllocationDTO;
import com.njydsz.pmis.execution.dto.PaymentCreateDTO;
import com.njydsz.pmis.execution.entity.PaymentDO;
import com.njydsz.pmis.execution.service.PaymentService;
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
 * 回款管理 Controller
 *
 * <p>负责回款录入、确认到账、核销发票、自动核销及现金流预测。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "回款管理")
@RestController
@RequestMapping("/api/v1/execution/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @Operation(summary = "录入回款")
    @PrePermission("finance:payment:create")
    @PostMapping
    public Result<Long> record(@Valid @RequestBody PaymentCreateDTO dto) {
        return Result.ok(service.record(dto));
    }

    @Operation(summary = "确认到账")
    @PrePermission("finance:payment:status")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @RequestParam Long operatorId) {
        service.confirm(id, operatorId);
        return Result.ok();
    }

    @Operation(summary = "取消")
    @PrePermission("finance:payment:status")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                          @RequestParam Long operatorId,
                          @RequestParam(required = false) String reason) {
        service.cancel(id, operatorId, reason);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("finance:payment:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "核销到发票")
    @PrePermission("finance:payment:allocate")
    @PostMapping("/allocate")
    public Result<Void> allocate(@Valid @RequestBody PaymentAllocationDTO dto) {
        service.allocate(dto);
        return Result.ok();
    }

    @Operation(summary = "自动核销（按客户）")
    @PrePermission("finance:payment:allocate")
    @PostMapping("/auto-allocate")
    public Result<Integer> autoAllocate(@RequestParam Long customerId,
                                   @RequestParam Long operatorId) {
        return Result.ok(service.autoAllocate(customerId, operatorId));
    }

    @Operation(summary = "现金流预测")
    @PrePermission("finance:payment:list")
    @GetMapping("/forecast")
    public Result<List<Map<String, Object>>> forecast(@RequestParam Long initiationId,
                                                 @RequestParam(defaultValue = "3") int months) {
        return Result.ok(service.forecastCashFlow(initiationId, months));
    }

    @Operation(summary = "详情")
    @PrePermission("finance:payment:list")
    @GetMapping("/{id}")
    public Result<PaymentDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("finance:payment:list")
    @GetMapping("/page")
    public Result<Page<PaymentDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long initiationId) {
        return Result.ok(service.page(page, size, keyword, status, contractId, customerId, initiationId));
    }

    @Operation(summary = "按合同汇总回款")
    @PrePermission("finance:payment:list")
    @GetMapping("/sum/by-contract")
    public Result<BigDecimal> sumByContract(@RequestParam Long contractId) {
        return Result.ok(service.sumReceivedByContract(contractId));
    }

    @Operation(summary = "按月汇总")
    @PrePermission("finance:payment:list")
    @GetMapping("/aggregate/by-month")
    public Result<List<Map<String, Object>>> aggregateByMonth(@RequestParam Long initiationId) {
        return Result.ok(service.aggregateByMonth(initiationId));
    }
}
