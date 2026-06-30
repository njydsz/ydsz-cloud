package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import com.njydsz.pmis.project.service.ContractTemplateService;
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

import java.util.List;

/**
 * 合同模板 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同模板管理")
@RestController
@RequestMapping("/api/v1/project/contract-template")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService service;

    @Operation(summary = "创建合同模板")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ContractTemplateCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PutMapping("/status")
    public R<Void> changeStatus(@Valid @RequestBody ContractTemplateStatusDTO dto) {
        service.changeStatus(dto);
        return R.ok();
    }

    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "模板详情")
    @GetMapping("/{id}")
    public R<ContractTemplateDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<ContractTemplateDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return R.ok(service.page(page, size, keyword, contractType, status));
    }

    @Operation(summary = "按合同类型查询模板")
    @GetMapping("/list-by-type")
    public R<List<ContractTemplateDO>> listByType(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status) {
        return R.ok(service.listByType(contractType, status));
    }
}
