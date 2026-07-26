package com.njydsz.system.web.controller;

import java.util.List;

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
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.server.service.DictItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 字典项 Controller。
 *
 * @author ydsz-team
 */
@Tag(name = "字典项", description = "字典项 CRUD + 按类型查询 + 树形查询")
@RestController
@RequestMapping("/api/v1/dict/item")
@RequiredArgsConstructor
public class DictItemController {

    private final DictItemService service;

    @Operation(summary = "分页查询字典项")
    @GetMapping("/page")
    public PageResponse<List<DictItemVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        IPage<DictItemVO> page = service.page(pageNum, pageSize);
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, page.getRecords());
    }

    @Operation(summary = "按 ID 查询字典项")
    @GetMapping("/{id}")
    public BaseResponse<DictItemVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Operation(summary = "按类型编码和字典项编码查询")
    @GetMapping("/lookup")
    public BaseResponse<DictItemVO> lookup(
            @Parameter(description = "字典类型编码") @RequestParam String typeCode,
            @Parameter(description = "字典项编码") @RequestParam String itemCode) {
        return BaseResponse.success(service.getByTypeAndCode(typeCode, itemCode));
    }

    @Operation(summary = "按类型编码查询启用的字典项列表")
    @GetMapping("/type/{typeCode}")
    public BaseResponse<List<DictItemVO>> listByType(@PathVariable String typeCode) {
        return BaseResponse.success(service.listEnabledByTypeCode(typeCode));
    }

    @Operation(summary = "按父级 ID 查询子字典项列表（树形字典）")
    @GetMapping("/children/{parentId}")
    public BaseResponse<List<DictItemVO>> listChildren(@PathVariable String parentId) {
        return BaseResponse.success(service.listChildren(parentId));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建字典项: ' + #dto.typeCode + '/' + #dto.itemCode")
    @Operation(summary = "创建字典项")
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody DictItemDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新字典项: ' + #dto.typeCode + '/' + #dto.itemCode")
    @Operation(summary = "更新字典项")
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody DictItemDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除字典项: ' + #id")
    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
