package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.PaymentAllocationDTO;
import com.njydsz.pmis.project.dto.PaymentCreateDTO;
import com.njydsz.pmis.project.entity.PaymentDO;
import com.njydsz.pmis.project.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/finance/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    /** 回款服务 */
    private final PaymentService service;

    /**
     * 录入回款
     *
     * @param dto 回款创建参数
     * @return 新建回款 ID
     */
    @Operation(summary = "录入回款")
    @PrePermission("finance:payment:create")
    @OperationLog(module = "回款管理", action = "录入回款", bizType = "PAYMENT", saveResult = true)
    @Idempotent(key = "payment:record", ttlSeconds = 10, message = "请勿重复录入回款")
    @PostMapping
    public Result<String> record(@Valid @RequestBody PaymentCreateDTO dto) {
        return Result.ok(service.record(dto));
    }

    /**
     * 确认回款到账
     *
     * @param id         回款 ID
     * @param operatorId 操作人 ID
     * @return 空结果
     */
    @Operation(summary = "确认到账")
    @PrePermission("finance:payment:status")
    @OperationLog(module = "回款管理", action = "确认到账", bizType = "PAYMENT")
    @Idempotent(key = "payment:confirm", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable String id, @RequestParam String operatorId) {
        service.confirm(id, operatorId);
        return Result.ok();
    }

    /**
     * 取消回款
     *
     * @param id         回款 ID
     * @param operatorId 操作人 ID
     * @param reason     取消原因，可选
     * @return 空结果
     */
    @Operation(summary = "取消")
    @PrePermission("finance:payment:status")
    @OperationLog(module = "回款管理", action = "取消回款", bizType = "PAYMENT")
    @Idempotent(key = "payment:cancel", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable String id,
                          @RequestParam String operatorId,
                          @RequestParam(required = false) String reason) {
        service.cancel(id, operatorId, reason);
        return Result.ok();
    }

    /**
     * 删除回款
     *
     * @param id 回款 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("finance:payment:delete")
    @OperationLog(module = "回款管理", action = "删除回款", bizType = "PAYMENT")
    @Idempotent(key = "payment:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 核销到发票
     *
     * @param dto 核销分配参数
     * @return 空结果
     */
    @Operation(summary = "核销到发票")
    @PrePermission("finance:payment:allocate")
    @OperationLog(module = "回款管理", action = "核销到发票", bizType = "PAYMENT")
    @Idempotent(key = "payment:allocate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/allocate")
    public Result<Void> allocate(@Valid @RequestBody PaymentAllocationDTO dto) {
        service.allocate(dto);
        return Result.ok();
    }

    /**
     * 按客户自动核销
     *
     * @param customerId 客户 ID
     * @param operatorId 操作人 ID
     * @return 已核销的回款数量
     */
    @Operation(summary = "自动核销（按客户）")
    @PrePermission("finance:payment:allocate")
    @OperationLog(module = "回款管理", action = "自动核销（按客户）", bizType = "PAYMENT", saveResult = true)
    @Idempotent(key = "payment:auto-allocate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/auto-allocate")
    public Result<Integer> autoAllocate(@RequestParam String customerId,
                                   @RequestParam String operatorId) {
        return Result.ok(service.autoAllocate(customerId, operatorId));
    }

    /**
     * 现金流预测
     *
     * @param initiationId 项目立项 ID
     * @param months       预测月份数
     * @return 预测结果列表
     */
    @Operation(summary = "现金流预测")
    @PrePermission("finance:payment:list")
    @GetMapping("/forecast")
    public Result<List<Map<String, Object>>> forecast(@RequestParam String initiationId,
                                                 @RequestParam(defaultValue = "3") int months) {
        return Result.ok(service.forecastCashFlow(initiationId, months));
    }

    /**
     * 查询回款详情
     *
     * @param id 回款 ID
     * @return 回款实体
     */
    @Operation(summary = "详情")
    @PrePermission("finance:payment:list")
    @GetMapping("/{id}")
    public Result<PaymentDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询回款
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同 ID
     * @param customerId   客户 ID
     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("finance:payment:list")
    @GetMapping("/page")
    public Result<Page<PaymentDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String initiationId) {
        return Result.ok(service.page(page, size, keyword, status, contractId, customerId, initiationId));
    }

    /**
     * 按合同汇总回款
     *
     * @param contractId 合同 ID
     * @return 已回款金额
     */
    @Operation(summary = "按合同汇总回款")
    @PrePermission("finance:payment:list")
    @GetMapping("/sum/by-contract")
    public Result<BigDecimal> sumByContract(@RequestParam String contractId) {
        return Result.ok(service.sumReceivedByContract(contractId));
    }

    /**
     * 按月汇总回款
     *
     * @param initiationId 项目立项 ID
     * @return 各月汇总列表
     */
    @Operation(summary = "按月汇总")
    @PrePermission("finance:payment:list")
    @GetMapping("/aggregate/by-month")
    public Result<List<Map<String, Object>>> aggregateByMonth(@RequestParam String initiationId) {
        return Result.ok(service.aggregateByMonth(initiationId));
    }
}
