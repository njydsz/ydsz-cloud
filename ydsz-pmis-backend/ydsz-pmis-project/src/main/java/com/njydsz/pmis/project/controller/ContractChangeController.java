package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.ContractChangeDTO;
import com.njydsz.pmis.project.entity.ContractChangeDO;
import com.njydsz.pmis.project.service.ContractChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/project/contract/change")
@RequiredArgsConstructor
public class ContractChangeController {

    private final ContractChangeService service;

    @Operation(summary = "提交变更申请")
    @PostMapping
    public R<Long> apply(@Valid @RequestBody ContractChangeDTO dto) {
        return R.ok(service.apply(dto));
    }

    @Operation(summary = "提交审批")
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return R.ok();
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id,
                           @RequestParam Long approverId,
                           @RequestParam String approverName) {
        service.approve(id, approverId, approverName);
        return R.ok();
    }

    @Operation(summary = "驳回")
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestParam Long approverId,
                          @RequestParam String approverName,
                          @RequestParam(required = false) String reason) {
        service.reject(id, approverId, approverName, reason);
        return R.ok();
    }

    @Operation(summary = "变更详情")
    @GetMapping("/{id}")
    public R<ContractChangeDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<ContractChangeDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String status) {
        return R.ok(service.page(page, size, contractId, status));
    }

    @Operation(summary = "按合同列出")
    @GetMapping("/list")
    public R<List<ContractChangeDO>> listByContract(@RequestParam Long contractId) {
        return R.ok(service.listByContract(contractId));
    }
}
