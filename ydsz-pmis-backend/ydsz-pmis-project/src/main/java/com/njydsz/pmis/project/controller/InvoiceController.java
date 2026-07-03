package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.project.dto.InvoiceCreateDTO;
import com.njydsz.pmis.project.entity.InvoiceDO;
import com.njydsz.pmis.project.service.InvoiceService;
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

    /**
     * 创建发票申请
     *
     * @param dto 发票创建参数
     * @return 新建发票 ID
     */
    @Operation(summary = "创建发票申请")
    @PrePermission("finance:invoice:create")
    @OperationLog(module = "发票管理", action = "创建发票申请", bizType = "INVOICE", saveResult = true)
    @Idempotent(key = "invoice:create", ttlSeconds = 10, message = "请勿重复提交发票申请")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InvoiceCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 提交发票审批
     *
     * @param id         发票 ID
     * @param operatorId 操作人 ID
     * @return 空结果
     */
    @Operation(summary = "提交审批")
    @PrePermission("finance:invoice:approve")
    @OperationLog(module = "发票管理", action = "提交发票审批", bizType = "INVOICE")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @RequestParam Long operatorId) {
        service.submit(id, operatorId);
        return Result.ok();
    }

    /**
     * 审批通过
     *
     * @param id  发票 ID
     * @param dto 审批参数
     * @return 空结果
     */
    @Operation(summary = "审批通过")
    @PrePermission("finance:invoice:approve")
    @OperationLog(module = "发票管理", action = "审批通过", bizType = "INVOICE")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.approve(id, dto);
        return Result.ok();
    }

    /**
     * 审批驳回
     *
     * @param id  发票 ID
     * @param dto 审批参数
     * @return 空结果
     */
    @Operation(summary = "审批驳回")
    @PrePermission("finance:invoice:approve")
    @OperationLog(module = "发票管理", action = "审批驳回", bizType = "INVOICE")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.reject(id, dto);
        return Result.ok();
    }

    /**
     * 财务开具发票
     *
     * @param id  发票 ID
     * @param dto 开具参数
     * @return 空结果
     */
    @Operation(summary = "财务开具")
    @PrePermission("finance:invoice:issue")
    @OperationLog(module = "发票管理", action = "财务开具发票", bizType = "INVOICE")
    @PutMapping("/{id}/issue")
    public Result<Void> issue(@PathVariable Long id, @Valid @RequestBody InvoiceApprovalDTO dto) {
        service.issue(id, dto);
        return Result.ok();
    }

    /**
     * 红冲发票
     *
     * @param id         发票 ID
     * @param operatorId 操作人 ID
     * @param comment    红冲备注，可选
     * @return 空结果
     */
    @Operation(summary = "红冲")
    @PrePermission("finance:invoice:reverse")
    @OperationLog(module = "发票管理", action = "红冲发票", bizType = "INVOICE")
    @PutMapping("/{id}/reverse")
    public Result<Void> redReverse(@PathVariable Long id,
                              @RequestParam Long operatorId,
                              @RequestParam(required = false) String comment) {
        service.redReverse(id, operatorId, comment);
        return Result.ok();
    }

    /**
     * 取消发票
     *
     * @param id         发票 ID
     * @param operatorId 操作人 ID
     * @param comment    取消备注，可选
     * @return 空结果
     */
    @Operation(summary = "取消")
    @PrePermission("finance:invoice:status")
    @OperationLog(module = "发票管理", action = "取消发票", bizType = "INVOICE")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                          @RequestParam Long operatorId,
                          @RequestParam(required = false) String comment) {
        service.cancel(id, operatorId, comment);
        return Result.ok();
    }

    /**
     * 删除发票
     *
     * @param id 发票 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("finance:invoice:delete")
    @OperationLog(module = "发票管理", action = "删除发票", bizType = "INVOICE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询发票详情
     *
     * @param id 发票 ID
     * @return 发票实体
     */
    @Operation(summary = "详情")
    @PrePermission("finance:invoice:list")
    @GetMapping("/{id}")
    public Result<InvoiceDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询发票
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同 ID
     * @param initiationId 项目立项 ID
     * @param customerId   客户 ID
     * @param invoiceType  发票类型
     * @return 分页结果
     */
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

    /**
     * 按合同汇总开票金额
     *
     * @param contractId 合同 ID
     * @return 已开票金额
     */
    @Operation(summary = "按合同汇总开票金额")
    @PrePermission("finance:invoice:list")
    @GetMapping("/sum/by-contract")
    public Result<BigDecimal> sumByContract(@RequestParam Long contractId) {
        return Result.ok(service.sumInvoicedByContract(contractId));
    }

    /**
     * 按状态分组查询发票台账
     *
     * @param contractId 合同 ID
     * @return 各状态发票汇总列表
     */
    @Operation(summary = "按状态分组台账")
    @PrePermission("finance:invoice:list")
    @GetMapping("/aggregate/by-status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@RequestParam Long contractId) {
        return Result.ok(service.aggregateByStatus(contractId));
    }
}
