package com.njydsz.system.web.controller;

import java.util.List;

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
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 字典类型 Controller。
 *
 * @author ydsz-team
 */
@Tag(name = "字典类型", description = "字典类型 CRUD")
@RestController
@RequestMapping("/api/v1/dict/type")
@RequiredArgsConstructor
public class DictController {

    private final DictService service;

    @Operation(summary = "分页查询字典类型（支持搜索过滤）")
    @GetMapping("/page")
    public PageResponse<List<DictTypeVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "类型名称模糊搜索") @RequestParam(required = false) String typeName,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        IPage<DictTypeVO> page = service.page(pageNum, pageSize, typeName, status);
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, page.getRecords());
    }

    @Operation(summary = "按 ID 查询字典类型")
    @GetMapping("/{id}")
    public BaseResponse<DictTypeVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建字典类型: ' + #dto.typeCode")
    @Operation(summary = "创建字典类型")
    @SentinelRateLimit(resource = "system.dict.save", threshold = 50)
    @SentinelRateLimit(resource = "system.dict.save", threshold = 50)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody DictTypeDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新字典类型: ' + #dto.typeCode")
    @Operation(summary = "更新字典类型")
    @SentinelRateLimit(resource = "system.dict.update", threshold = 50)
    @SentinelRateLimit(resource = "system.dict.update", threshold = 50)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody DictTypeDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除字典类型: ' + #id")
    @Operation(summary = "删除字典类型")
    @SentinelRateLimit(resource = "system.dict.remove", threshold = 50)
    @SentinelRateLimit(resource = "system.dict.remove", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
