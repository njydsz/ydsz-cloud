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
import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.server.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 公司 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Tag(name = "公司管理", description = "公司 CRUD")
public class CompanyController {

    private final CompanyService service;

    @GetMapping("/list")
    @Operation(summary = "查询全部公司列表")
    public BaseResponse<List<CompanyVO>> list() {
        return BaseResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询公司")
    public BaseResponse<CompanyVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建公司")
    public BaseResponse<String> create(@Valid @RequestBody CompanySaveDTO dto) {
        return BaseResponse.success(service.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新公司")
    public BaseResponse<Boolean> update(@Valid @RequestBody CompanySaveDTO dto) {
        return BaseResponse.success(service.update(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公司")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
