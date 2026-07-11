package com.njydsz.pmis.sales.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.sales.domain.dto.ContractCreateDTO;
import com.njydsz.pmis.sales.domain.dto.ContractStatusDTO;
import com.njydsz.pmis.project.entity.contract.ContractDO;
import com.njydsz.pmis.sales.server.service.contract.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 合同主数据 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同管理", description = "合同管理相关接口")
@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
@Validated
public class ContractController {

    /** 合同服务 */
    private final ContractService service;

    /**
     * 创建合同。
     *
     * @param dto 合同创建参数
     * @return 合同 ID
     */
    @Operation(summary = "创建合同")
    @PrePermission("project:contract:create")
    @Idempotent(key = "contract:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody ContractCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 合同状态迁移。
     *
     * @param dto 状态迁移参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @PrePermission("project:contract:status")
    @Idempotent(key = "contract:update", ttlSeconds = 5, message = "请勿重复提交")
    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(@Parameter(description = "合同ID") @PathVariable String id,
                                     @Valid @RequestBody ContractStatusDTO dto) {
        dto.setId(id);
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 删除合同（逻辑删除）。
     *
     * @param id 合同 ID
     * @return 空结果
     */
    @Operation(summary = "删除合同")
    @PrePermission("project:contract:delete")
    @Idempotent(key = "contract:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "合同管理", action = "删除合同", bizType = "CONTRACT")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "合同ID") @PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询合同详情。
     *
     * @param id 合同 ID
     * @return 合同实体
     */
    @Operation(summary = "合同详情")
    @PrePermission("project:contract:list")
    @GetMapping("/{id}")
    public Result<ContractDO> get(@Parameter(description = "合同ID") @PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询合同列表。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编号/名称），可空
     * @param status       状态码，可空
     * @param contractType 合同类型，可空
     * @param riskLevel    风险等级，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("project:contract:list")
    @GetMapping("/page")
    public Result<Page<ContractDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "合同类型") @RequestParam(required = false) String contractType,
            @Parameter(description = "风险等级") @RequestParam(required = false) String riskLevel) {
        return Result.ok(service.page(page, size, keyword, status, contractType, riskLevel));
    }

    /**
     * 重新评估合同风险等级。
     *
     * @param id 合同 ID
     * @return 风险等级码
     */
    @Operation(summary = "重新评估风险等级")
    @PrePermission("project:contract:evaluate")
    @Idempotent(key = "contract:evaluateRisk", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/evaluateRisk")
    public Result<String> evaluateRisk(@Parameter(description = "合同ID") @PathVariable String id) {
        return Result.ok(service.evaluateRisk(id));
    }

    /**
     * 按状态聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种状态对应的数量列表
     */
    @Operation(summary = "按状态聚合")
    @PrePermission("project:contract:list")
    @GetMapping("/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateByStatus(@Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        return Result.ok(service.aggregateByStatus(tenantId));
    }

    /**
     * 按风险等级聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种风险等级对应的数量列表
     */
    @Operation(summary = "按风险等级聚合")
    @PrePermission("project:contract:list")
    @GetMapping("/aggregate/risk")
    public Result<List<Map<String, Object>>> aggregateByRisk(@Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        return Result.ok(service.aggregateByRisk(tenantId));
    }
}
