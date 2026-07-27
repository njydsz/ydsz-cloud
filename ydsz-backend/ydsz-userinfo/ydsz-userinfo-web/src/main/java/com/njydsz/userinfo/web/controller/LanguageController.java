package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
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
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.server.service.LanguageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 语言 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/language")
@RequiredArgsConstructor
@Tag(name = "语言管理", description = "语言 CRUD")
public class LanguageController {

    private final LanguageService service;

    @GetMapping("/list")
    @Operation(summary = "查询全部语言列表")
    public BaseResponse<List<LanguageVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询语言")
    public BaseResponse<LanguageVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建语言: ' + #dto.languageName")
    @Idempotent(key = "ydsz:userinfo:LanguageController:create:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "userinfo.language.create", threshold = 50)
    @PostMapping
    @Operation(summary = "创建语言")
    public BaseResponse<String> create(@Valid @RequestBody LanguageSaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新语言: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:LanguageController:update:lock", ttlSeconds = 5)
    @SentinelRateLimit(resource = "userinfo.language.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新语言")
    public BaseResponse<Boolean> update(@Valid @RequestBody LanguageSaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @Audit(module = "语言管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除语言: ' + #id")
    @SentinelRateLimit(resource = "userinfo.language.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:LanguageController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除语言")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
