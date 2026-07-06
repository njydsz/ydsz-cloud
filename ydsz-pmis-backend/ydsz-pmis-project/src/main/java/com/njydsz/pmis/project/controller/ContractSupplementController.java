package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;
import com.njydsz.pmis.project.service.ContractSupplementService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同补充协议 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同补充协议")
@RestController
@RequestMapping("/api/v1/project/contract/supplement")
@RequiredArgsConstructor
@Validated
public class ContractSupplementController {

    /** 合同补充协议服务 */
    private final ContractSupplementService service;

    /**
     * 创建合同补充协议。
     *
     * @param dto 补充协议参数
     * @return 补充协议 ID
     */
    @Operation(summary = "创建补充协议")
    @Idempotent(key = "contract-supplement:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ContractSupplementDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 删除补充协议（逻辑删除）。
     *
     * @param id 补充协议 ID
     * @return 空结果
     */
    @Operation(summary = "删除补充协议")
    @OperationLog(module = "合同管理", action = "删除补充协议", bizType = "CONTRACT_SUPPLEMENT")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询补充协议详情。
     *
     * @param id 补充协议 ID
     * @return 补充协议实体
     */
    @Operation(summary = "补充协议详情")
    @GetMapping("/{id}")
    public Result<ContractSupplementDO> get(@PathVariable @Min(1) Long id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 按合同查询补充协议列表。
     *
     * @param contractId 合同 ID
     * @return 补充协议列表
     */
    @Operation(summary = "按合同列出")
    @GetMapping("/list")
    public Result<List<ContractSupplementDO>> listByContract(@RequestParam Long contractId) {
        return Result.ok(service.listByContract(contractId));
    }

    /**
     * 分页查询补充协议。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param contractId 合同 ID，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<Page<ContractSupplementDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long contractId) {
        return Result.ok(service.page(page, size, contractId));
    }
}
