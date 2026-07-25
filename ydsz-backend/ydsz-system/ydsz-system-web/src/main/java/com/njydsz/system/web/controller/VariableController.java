package com.njydsz.system.web.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import com.njydsz.system.domain.entity.VariableDO;
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
@Tag(name = "系统变量", description = "系统变量 CRUD")
@RestController
@RequestMapping("/api/v1/variable")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService service;

    @Operation(summary = "分页查询系统变量")
    @GetMapping("/page")
    public PageResponse<List<VariableVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        IPage<VariableDO> page = service.page(pageNum, pageSize);
        List<VariableVO> vos = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, vos);
    }

    @Operation(summary = "按 ID 查询系统变量")
    @GetMapping("/{id}")
    public BaseResponse<VariableVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建变量: ' + #dto.variableKey")
    @Operation(summary = "创建系统变量")
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody VariableDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新变量: ' + #dto.variableKey")
    @Operation(summary = "更新系统变量")
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody VariableDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "系统变量", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除变量: ' + #id")
    @Operation(summary = "删除系统变量")
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }

    private VariableVO toVO(VariableDO entity) {
        if (entity == null) {
            return null;
        }
        VariableVO vo = new VariableVO();
        vo.setId(entity.getId());
        vo.setVariableKey(entity.getVariableKey());
        vo.setVariableValue(entity.getVariableValue());
        vo.setValueType(entity.getValueType());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
