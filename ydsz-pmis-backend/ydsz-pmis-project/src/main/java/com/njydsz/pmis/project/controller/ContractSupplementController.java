package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;
import com.njydsz.pmis.project.service.ContractSupplementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ContractSupplementController {

    private final ContractSupplementService service;

    @Operation(summary = "创建补充协议")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ContractSupplementDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "删除补充协议")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "补充协议详情")
    @GetMapping("/{id}")
    public Result<ContractSupplementDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "按合同列出")
    @GetMapping("/list")
    public Result<List<ContractSupplementDO>> listByContract(@RequestParam Long contractId) {
        return Result.ok(service.listByContract(contractId));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<Page<ContractSupplementDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long contractId) {
        return Result.ok(service.page(page, size, contractId));
    }
}
