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

@Tag(name = "回款管理")
@RestController
@RequestMapping("/api/v1/execution/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @Operation(summary = "录入回款")
    @Pblic R<Long> record(@Valid @RequestBody PaymentCreateDTO dto) {
        return R.ok(service.record(dto));
    }

    @Operation(summary = "确认到账")
    @PutMapping("/{id}/confirm")
    pu  service.confirm(id, operatorId);
        return R.ok();
    }

    @
blic R<Void> cancel(@PathVariable Long id,
                          @RequestParam Long operatorId,
                          @RequestParam(required = false) String reason) {
        service.cancel(id, operatorId, reason);
        return R.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("finance:payment:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "核销到发票")
    @PostMapping("/allocate")
    public R<Void> allocate(@Va")
    @PrePermission("finance:payment:allocatelid @RequestBody PaymentAllocationDTO dto) {
        service.allocate(dto);
        return R.ok();
    }

    @Operation(summary = "自动核销（按客户）")
    @PostMapping("/auto-allocate")
    public R<Integer> autoAllocate(@RequestParam Long customerId,
                                   @RequestParam Long operatorId) {
        return R.ok(service.autoAllocate(customerId, operatorId));
    }

    @Operation(summary = "现金流预测")
    @GetMapping("/forecast")
    public R<List<Map<String, Object>>> forecast(@RequestParam Long initiationId,
                                                 @RequestParam(defaultValue = "3") int months) {
        return R.ok(service.forecastCashFlow(initiationId, months));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<PaymentDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("finance:payment:list")
    @GetMapping("/page")
    public R<Page<PaymentDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long initiationId) {
        return R.ok(service.page(page, size, keyword, status, contractId, customerId, initiationId));
    }

    @Operation(summary = "按合同汇总回款")
    @GetMapping("/sum/by-contract")
    public R<BigDecimal> sumByContract(@RequestParam Long contractId) {
        return R.ok(service.sumReceivedByContract(contractId));
    }

    @Operation(summary = "按月汇总")
    @PrePermission("finance:payment:list")
    @GetMapping("/aggregate/by-month")
    public R<List<Map<String, Object>>> aggregateByMonth(@RequestParam Long initiationId) {
        return R.ok(service.aggregateByMonth(initiationId));
    }
}
