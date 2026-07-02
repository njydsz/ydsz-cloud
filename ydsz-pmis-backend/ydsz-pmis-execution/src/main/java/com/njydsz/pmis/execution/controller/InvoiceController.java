package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.execution.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.execution.dto.InvoiceCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.service.InvoiceService;
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
 * 发票管理 Controller
 *
 * <p>负责发票的创建、审批、开具、红冲、取消及台账查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "发票管理")
@RestController
@RequestMapping("/api/v1/execution/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @Operation(summary = "创建发票申请")
    @PrePermission("finance:invoice:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InvoiceCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "提交审批")
    @PrePermission("finance:invoice:approve")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @RequestParam Long operatorId) {
        service.submit(id, operatorId);
        return Result.ok();
    }

    @Operation(summary = "审批通过")
    @PrePermission("finance:invoice:approve")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.approve(id, dto);
        return Result.ok();
    }

    @Operation(summary = "审批驳回")
    @PrePermission("finance:invoice:approve")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.reject(id, dto);
        return Result.ok();
    }

    @Operation(summary = "财务开具")
    @PrePermission("finance:invoice:issue")
    @PutMapping("/{id}/issue")
    public Result<Void> issue(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.issue(id, dto);
        return Result.ok();
    }

    @Operation(summary = "红冲")
    @PrePermission("finance:invoice:reverse")
    @PutMapping("/{id}/reverse")
    public Result<Void> redReverse(@PathVariable Long id,
                              @RequestParam Long operatorId,
                              @RequestParam(required = false) String comment) {
        service.redReverse(id, operatorId, comment);
        return Result.ok();
    }

    @Operation(summary = "取消")
    @PrePermission("finance:invoice:status")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                          @RequestParam Long operatorId,
                          @RequestParam(required = false) String comment) {
        service.cancel(id, operatorId, comment);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PrePermission("finance:invoice:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "详情")
    @PrePermission("finance:invoice:list")
    @GetMapping("/{id}")
    public Result<InvoiceDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "分页")
    @PrePermission("finance:invoice:list")
    @GetMapping("/page")
    public Result<Page<InvoiceDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String invoiceType) {
        return Result.ok(service.page(page, size, keyword, status, contractId, initiationId, customerId, invoiceType));
    }

    @Operation(summary = "按合同汇总开票金额")
    @PrePermission("finance:invoice:list")
    @GetMapping("/sum/by-contract")
    public Result<BigDecimal> sumByContract(@RequestParam Long contractId) {
        return Result.ok(service.sumInvoicedByContract(contractId));
    }

    @Operation(summary = "按状态分组台账")
    @PrePermission("finance:invoice:list")
    @GetMapping("/aggregate/by-status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam Long contractId) {
        return Result.ok(service.aggregateByStatus(contractId));
    }
}
