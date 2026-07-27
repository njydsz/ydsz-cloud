package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

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
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典类型 Controller。
 *
 * <p>提供字典类型 CRUD + 全量列表查询等业务端点。
 *
 * @author ydsz-team
 */
@Tag(name = "字典类型", description = "字典类型 CRUD + 全量列表")
@RestController
@RequestMapping("/api/v1/dict/type")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    // ============================== CRUD 端点 ==============================

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public PageResponse<List<DictTypeVO>> page(DictPageQuery query) {
        PageResult<DictTypeVO> result = dictService.page(query);
        return PageResponse.success(
                result.getTotal(),
                (long) result.getPageNum(),
                (long) result.getPageSize(),
                result.getRecords());
    }

    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public BaseResponse<DictTypeVO> getById(@PathVariable String id) {
        return BaseResponse.success(dictService.getById(id));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建字典类型: ' + #dto.typeCode")
    @Operation(summary = "创建字典类型")
    @RateLimit(resource = "system.dict.save", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:save:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody DictTypeDTO dto) {
        return BaseResponse.success(dictService.save(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新字典类型: ' + #dto.typeCode")
    @Operation(summary = "更新字典类型")
    @RateLimit(resource = "system.dict.update", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody DictTypeDTO dto) {
        return BaseResponse.success(dictService.updateById(dto));
    }

    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除字典类型: ' + #id")
    @Operation(summary = "删除字典类型")
    @RateLimit(resource = "system.dict.remove", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(dictService.removeById(id));
    }

    // ============================== 业务扩展端点 ==============================

    @Operation(summary = "查询全部字典类型")
    @GetMapping("/all")
    public BaseResponse<List<DictTypeVO>> listAll() {
        return BaseResponse.success(dictService.listAll());
    }
}