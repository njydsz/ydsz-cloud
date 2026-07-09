package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import com.njydsz.pmis.project.service.ContractTemplateService;
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

import java.util.List;

/**
 * 合同模板 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同模板管理")
@RestController
@RequestMapping("/contract/template")
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
    @PrePermission("project:contract-template:create")
    @Idempotent(key = "contract-template:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody ContractTemplateCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 模板状态迁移。
     *
     * @param dto 状态迁移参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @PrePermission("project:contract-template:publish")
    @Idempotent(key = "contract-template:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody ContractTemplateStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 删除模板（逻辑删除）。
     *
     * @param id 模板 ID
     * @return 空结果
     */
    @Operation(summary = "删除模板")
    @PrePermission("project:contract-template:delete")
    @Idempotent(key = "contract-template:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "合同模板", action = "删除模板", bizType = "CONTRACT_TEMPLATE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    @Operation(summary = "模板详情")
    @PrePermission("project:contract-template:list")
    @GetMapping("/{id}")
    public Result<ContractTemplateDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
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
    @PrePermission("project:contract-template:list")
    @GetMapping("/page")
    public Result<Page<ContractTemplateDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, keyword, contractType, status));
    }

    /**
     * 按合同类型查询模板列表。
     *
     * @param contractType 合同类型，可空
     * @param status       模板状态，可空
     * @return 模板列表
     */
    @Operation(summary = "按合同类型查询模板")
    @PrePermission("project:contract-template:list")
    @GetMapping("/list-by-type")
    public Result<List<ContractTemplateDO>> listByType(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.listByType(contractType, status));
    }
}
