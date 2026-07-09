package com.njydsz.pmis.project.controller.contract;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.contract.ContractChangeDTO;
import com.njydsz.pmis.project.entity.contract.ContractChangeDO;
import com.njydsz.pmis.project.service.contract.ContractChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同变更 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同变更")
@RestController
@RequestMapping("/contract/change")
@RequiredArgsConstructor
@Validated
public class ContractChangeController {

    /** 合同变更服务 */
    private final ContractChangeService service;

    /**
     * 提交合同变更申请。
     *
     * @param dto 变更申请参数
     * @return 变更记录 ID
     */
    @Operation(summary = "提交变更申请")
    @PrePermission("project:contract-change:create")
    @Idempotent(key = "contract-change:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> apply(@Valid @RequestBody ContractChangeDTO dto) {
        return Result.ok(service.apply(dto));
    }

    /**
     * 提交变更进入审批流。
     *
     * @param id 变更 ID
     * @return 空结果
     */
    @Operation(summary = "提交审批")
    @PrePermission("project:contract-change:approve")
    @Idempotent(key = "contract-change:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable String id) {
        service.submit(id);
        return Result.ok();
    }

    /**
     * 审批通过。
     *
     * @param id           变更 ID
     * @param approverId   审批人 ID
     * @param approverName 审批人名称
     * @return 空结果
     */
    @Operation(summary = "审批通过")
    @PrePermission("project:contract-change:approve")
    @Idempotent(key = "contract-change:approve", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable String id,
                           @RequestParam String approverId,
                           @RequestParam String approverName) {
        service.approve(id, approverId, approverName);
        return Result.ok();
    }

    /**
     * 驳回变更。
     *
     * @param id           变更 ID
     * @param approverId   审批人 ID
     * @param approverName 审批人名称
     * @param reason       驳回原因，可空
     * @return 空结果
     */
    @Operation(summary = "驳回")
    @PrePermission("project:contract-change:approve")
    @Idempotent(key = "contract-change:reject", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable String id,
                          @RequestParam String approverId,
                          @RequestParam String approverName,
                          @RequestParam(required = false) String reason) {
        service.reject(id, approverId, approverName, reason);
        return Result.ok();
    }

    /**
     * 查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更实体
     */
    @Operation(summary = "变更详情")
    @PrePermission("project:contract-change:list")
    @GetMapping("/{id}")
    public Result<ContractChangeDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询合同变更列表。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param contractId 合同 ID，可空
     * @param status     状态码，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("project:contract-change:list")
    @GetMapping("/page")
    public Result<Page<ContractChangeDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String contractId,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, contractId, status));
    }

    /**
     * 按合同查询变更记录列表。
     *
     * @param contractId 合同 ID
     * @return 变更记录列表
     */
    @Operation(summary = "按合同列出")
    @PrePermission("project:contract-change:list")
    @GetMapping("/list")
    public Result<List<ContractChangeDO>> listByContract(@RequestParam String contractId) {
        return Result.ok(service.listByContract(contractId));
    }
}
