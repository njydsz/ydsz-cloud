package com.njydsz.project.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.project.domain.dto.ContractSupplementDTO;
import com.njydsz.project.domain.entity.ContractSupplementDO;
import com.njydsz.project.server.service.contract.ContractSupplementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 合同补充协议 Controller
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "合同补充协议")
@RestController
@RequestMapping("/api/project/contract/supplement")
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
    @Idempotent(key = "contractSupplement:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody ContractSupplementDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    /**
     * 删除补充协议（逻辑删除）。
     *
     * @param id 补充协议 ID
     * @return 空结果
     */
    @Operation(summary = "删除补充协议")
    @Idempotent(key = "contractSupplement:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.success();
    }

    /**
     * 查询补充协议详情。
     *
     * @param id 补充协议 ID
     * @return 补充协议实体
     */
    @Operation(summary = "补充协议详情")
    @GetMapping("/{id}")
    public BaseResponse<ContractSupplementDO> get(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    /**
     * 按合同查询补充协议列表。
     *
     * @param contractId 合同 ID
     * @return 补充协议列表
     */
    @Operation(summary = "按合同列出")
    @GetMapping("/list")
    public BaseResponse<List<ContractSupplementDO>> listByContract(@RequestParam String contractId) {
        return BaseResponse.success(service.listByContract(contractId));
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
    public BaseResponse<Page<ContractSupplementDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String contractId) {
        return BaseResponse.success(service.page(page, size, contractId));
    }
}
