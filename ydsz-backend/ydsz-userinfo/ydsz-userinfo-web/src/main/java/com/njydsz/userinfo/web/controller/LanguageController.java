package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.server.service.LanguageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;

/**
 * 语言 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/language")
@Tag(name = "语言管理", description = "语言 CRUD")
@RequiredArgsConstructor
public class LanguageController {

    private final LanguageService service;

    // ============================== CRUD 端点 ==============================

    @GetMapping("/page")
    @Operation(summary = "分页查询")
    public PageResponse<List<LanguageVO>> page(LanguagePageQuery query) {
        PageResult<LanguageVO> result = service.page(query);
        return PageResponse.success(
                result.getTotal(),
                (long) result.getPageNum(),
                (long) result.getPageSize(),
                result.getRecords());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询")
    public BaseResponse<LanguageVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @GetMapping("/list")
    @Operation(summary = "查询全部语言列表")
    public BaseResponse<List<LanguageVO>> list() {
        return BaseResponse.success(service.list());
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建语言: ' + #dto.languageName")
    @Idempotent(key = "ydsz:userinfo:LanguageController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.language.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建语言")
    public BaseResponse<String> save(@Valid @RequestBody LanguagePostDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新语言: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:LanguageController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.language.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新语言")
    public BaseResponse<Boolean> update(@Valid @RequestBody LanguagePutDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除语言: ' + #id")
    @RateLimit(resource = "userinfo.language.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:LanguageController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除语言")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
    /**
     * 将 PostDTO 转换为 SaveDTO。
     */
    private LanguageSaveDTO toSaveDTO(LanguagePostDTO dto) {
        LanguageSaveDTO saveDTO = new LanguageSaveDTO();
        saveDTO.setLanguageCode(dto.getLanguageCode());
        saveDTO.setLanguageName(dto.getLanguageName());
        saveDTO.setIsDefault(dto.getIsDefault());
        saveDTO.setSortOrder(dto.getSortOrder());
        saveDTO.setStatus(dto.getStatus());
        return saveDTO;
    }

    /**
     * 将 PutDTO 转换为 SaveDTO。
     */
    private LanguageSaveDTO toSaveDTO(LanguagePutDTO dto) {
        LanguageSaveDTO saveDTO = new LanguageSaveDTO();
        saveDTO.setId(dto.getId());
        saveDTO.setLanguageCode(dto.getLanguageCode());
        saveDTO.setLanguageName(dto.getLanguageName());
        saveDTO.setIsDefault(dto.getIsDefault());
        saveDTO.setSortOrder(dto.getSortOrder());
        saveDTO.setStatus(dto.getStatus());
        return saveDTO;
    }
}
