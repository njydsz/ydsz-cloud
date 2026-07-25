package com.njydsz.userinfo.web.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.server.service.LanguageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public BaseResponse<List<LanguageDO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询语言")
    public BaseResponse<LanguageDO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建语言")
    public BaseResponse<String> save(@RequestBody LanguageDO entity) {
        return BaseResponse.success(service.save(entity));
    }

    @PutMapping
    @Operation(summary = "更新语言")
    public BaseResponse<Boolean> update(@RequestBody LanguageDO entity) {
        return BaseResponse.success(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除语言")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
