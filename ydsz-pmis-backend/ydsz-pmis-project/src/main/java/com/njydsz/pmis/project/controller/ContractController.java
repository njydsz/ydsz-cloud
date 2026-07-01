package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.dto.ContractStatusDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.service.ContractService;
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
import java.util.Map;

/**
 * 合同主数据 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "合同管理")
@RestController
@RequestMapping("/api/v1/project/contract")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService service;

    @Operation(summary = "创建合同")
    @PrePermission("project:contract:create")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ContractCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PrePermission("project:contract:status")
    @PutMapping("/status")
    public R<Void> changeStatus(@Valid @RequestBody ContractStatusDTO dto) {
        service.changeStatus(dto);
        return R.ok();
    }

    @Operation(summary = "删除合同")
    @PrePermission("project:contract:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "合同详情")
    @PrePermission("project:contract:list")
    @GetMapping("/{id}")
    public R<ContractDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @PrePermission("project:contract:list")
    @GetMapping("/page")
    public R<Page<ContractDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String riskLevel) {
        return R.ok(service.page(page, size, keyword, status, contractType, riskLevel));
    }

    @Operation(summary = "重新评估风险等级")
    @PrePermission("project:contract:evaluate")
    @PostMapping("/{id}/evaluate-risk")
    public R<String> evaluateRisk(@PathVariable Long id) {
        return R.ok(service.evaluateRisk(id));
    }

    @Operation(summary = "按状态聚合")
    @PrePermission("project:contract:list")
    @GetMapping("/aggregate/status")
    public R<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByStatus(tenantId));
    }

    @Operation(summary = "按风险等级聚合")
    @PrePermission("project:contract:list")
    @GetMapping("/aggregate/risk")
    public R<List<Map<String, Object>>> aggregateByRisk(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByRisk(tenantId));
    }
}
