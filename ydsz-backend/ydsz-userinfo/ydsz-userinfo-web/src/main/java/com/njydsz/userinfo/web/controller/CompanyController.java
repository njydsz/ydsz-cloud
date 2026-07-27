package com.njydsz.userinfo.web.controller;

import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
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
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.userinfo.domain.dto.post.CompanyPostDTO;
import com.njydsz.userinfo.domain.dto.put.CompanyPutDTO;

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

    @RateLimit(resource = "userinfo.company.create", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:CompanyController:create:lock", ttlSeconds = 5)
    @PostMapping
    @Operation(summary = "创建公司")
    public BaseResponse<String> create(@Valid @RequestBody CompanyPostDTO dto) {
        return BaseResponse.success(service.create(toSaveDTO(dto)));
    }

    @Audit(module = "公司管理", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新公司: ' + #dto.id")
    @Idempotent(key = "ydsz:userinfo:CompanyController:update:lock", ttlSeconds = 5)
    @RateLimit(resource = "userinfo.company.update", threshold = 50)
    @PutMapping
    @Operation(summary = "更新公司")
    public BaseResponse<Boolean> update(@Valid @RequestBody CompanyPutDTO dto) {
        return BaseResponse.success(service.update(toSaveDTO(dto)));
    }

    @Audit(module = "公司管理", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除公司: ' + #id")
    @RateLimit(resource = "userinfo.company.remove", threshold = 50)
    @Idempotent(key = "ydsz:userinfo:CompanyController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Operation(summary = "删除公司")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
    /**
     * 将 PostDTO 转换为 SaveDTO。
     */
    private CompanySaveDTO toSaveDTO(CompanyPostDTO dto) {
        CompanySaveDTO saveDTO = new CompanySaveDTO();
        saveDTO.setCompanyName(dto.getCompanyName());
        saveDTO.setCompanyCode(dto.getCompanyCode());
        saveDTO.setParentId(dto.getParentId());
        saveDTO.setContactPerson(dto.getContactPerson());
        saveDTO.setContactPhone(dto.getContactPhone());
        saveDTO.setAddress(dto.getAddress());
        saveDTO.setStatus(dto.getStatus());
        return saveDTO;
    }

    /**
     * 将 PutDTO 转换为 SaveDTO。
     */
    private CompanySaveDTO toSaveDTO(CompanyPutDTO dto) {
        CompanySaveDTO saveDTO = new CompanySaveDTO();
        saveDTO.setId(dto.getId());
        saveDTO.setCompanyName(dto.getCompanyName());
        saveDTO.setCompanyCode(dto.getCompanyCode());
        saveDTO.setParentId(dto.getParentId());
        saveDTO.setContactPerson(dto.getContactPerson());
        saveDTO.setContactPhone(dto.getContactPhone());
        saveDTO.setAddress(dto.getAddress());
        saveDTO.setStatus(dto.getStatus());
        return saveDTO;
    }
}
