package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.server.service.VariableService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 系统变量 Controller。
 *
 * @author ydsz-team
 */
@Tag(name = "系统变量", description = "系统变量 CRUD + 按 key 查询")
@RestController
@RequestMapping("/api/v1/variable")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService service;

    @Operation(summary = "分页查询系统变量（支持搜索过滤）")
    @GetMapping("/page")
    public PageResponse<List<VariableVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "变量键模糊搜索") @RequestParam(required = false) String variableKey,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        IPage<VariableVO> page = service.page(pageNum, pageSize, variableKey, status);
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, page.getRecords());
    }

    @Operation(summary = "按 ID 查询系统变量")
    @GetMapping("/{id}")
    public BaseResponse<VariableVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Operation(summary = "按变量键查询变量值")
    @GetMapping("/key/{variableKey}")
    public BaseResponse<String> getByKey(@PathVariable String variableKey) {
        return BaseResponse.success(service.getVariableValue(variableKey));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建变量: ' + #dto.variableKey")
    @Operation(summary = "创建系统变量")
    @SentinelRateLimit(resource = "system.variable.save", threshold = 50)
    @Idempotent(key = 'system:variable:save', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.variable.save", threshold = 50)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody VariableDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新变量: ' + #dto.variableKey")
    @Operation(summary = "更新系统变量")
    @SentinelRateLimit(resource = "system.variable.update", threshold = 50)
    @Idempotent(key = 'system:variable:update', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.variable.update", threshold = 50)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody VariableDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除变量: ' + #id")
    @Operation(summary = "删除系统变量")
    @SentinelRateLimit(resource = "system.variable.remove", threshold = 50)
    @Idempotent(key = 'system:variable:remove', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.variable.remove", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
