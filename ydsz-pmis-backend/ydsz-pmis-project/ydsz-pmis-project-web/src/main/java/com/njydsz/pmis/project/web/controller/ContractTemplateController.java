package com.njydsz.pmis.project.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.audit.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.project.domain.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.domain.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.domain.entity.ContractTemplateDO;
import com.njydsz.pmis.project.server.service.contract.ContractTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 合同模板 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同模板管理")
@RestController
@RequestMapping("/api/project/contract/template")
@RequiredArgsConstructor
@Validated
public class ContractTemplateController {

    /** 合同模板服务 */
    private final ContractTemplateService service;

    /**
     * 创建合同模板。
     *
     * @param dto 模板创建参数
     * @return 模板 ID
     */
    @Operation(summary = "创建合同模板")
    @AuthApiPermission(apiCodes = "project:contractTemplate:create")
    @Idempotent(key = "contractTemplate:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody ContractTemplateCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 模板状态迁移。
     *
     * @param dto 状态迁移参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @AuthApiPermission(apiCodes = "project:contractTemplate:publish")
    @Idempotent(key = "contractTemplate:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody ContractTemplateStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除模板（逻辑删除）。
     *
     * @param id 模板 ID
     * @return 空结果
     */
    @Operation(summary = "删除模板")
    @AuthApiPermission(apiCodes = "project:contractTemplate:delete")
    @Idempotent(key = "contractTemplate:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "合同模板", action = "删除模板", bizType = "CONTRACT_TEMPLATE")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    @Operation(summary = "模板详情")
    @AuthApiPermission(apiCodes = "project:contractTemplate:list")
    @GetMapping("/{id}")
    public BaseResponse<ContractTemplateDO> get(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询合同模板。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编码/名称），可空
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apiCodes = "project:contractTemplate:list")
    @GetMapping("/page")
    public BaseResponse<Page<ContractTemplateDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(service.page(page, size, keyword, contractType, status));
    }

    /**
     * 按合同类型查询模板列表。
     *
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 模板列表
     */
    @Operation(summary = "按合同类型查询模板")
    @AuthApiPermission(apiCodes = "project:contractTemplate:list")
    @GetMapping("/listByType")
    public BaseResponse<List<ContractTemplateDO>> listByType(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(service.listByType(contractType, status));
    }
}
