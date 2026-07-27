package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.common.web.controller.BaseCrudController;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.server.service.DictService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 字典类型 Controller。
 *
 * <p>继承 {@link BaseCrudController} 获得标准 CRUD 端点，
 * 额外提供全量列表查询等业务端点。
 *
 * @author ydsz-team
 */
@Tag(name = "字典类型", description = "字典类型 CRUD + 全量列表")
@RestController
@RequestMapping("/api/v1/dict/type")
public class DictController extends BaseCrudController<DictTypeDO, DictTypeDTO, DictTypeVO, DictPageQuery, String> {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @Override
    protected BaseCrudService<DictTypeDO, DictTypeDTO, DictTypeVO, DictPageQuery, String> getService() {
        return dictService;
    }

    // ============================== 覆写基类方法（添加审计 + 幂等 + 限流注解） ==============================

    @Override
    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建字典类型: ' + #dto.typeCode")
    @Operation(summary = "创建字典类型")
    @RateLimit(resource = "system.dict.save", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:save:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody DictTypeDTO dto) {
        return super.save(dto);
    }

    @Override
    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新字典类型: ' + #dto.typeCode")
    @Operation(summary = "更新字典类型")
    @RateLimit(resource = "system.dict.update", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody DictTypeDTO dto) {
        return super.update(dto);
    }

    @Override
    @Audit(module = "字典管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除字典类型: ' + #id")
    @Operation(summary = "删除字典类型")
    @RateLimit(resource = "system.dict.remove", threshold = 50)
    @Idempotent(key = "ydsz:system:DictController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return super.remove(id);
    }

    // ============================== 业务扩展端点 ==============================

    @Operation(summary = "查询全部字典类型")
    @GetMapping("/all")
    public BaseResponse<List<DictTypeVO>> listAll() {
        return BaseResponse.success(dictService.listAll());
    }
}
