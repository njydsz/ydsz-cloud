package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.server.service.ProjectContractSupplementService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractSupplementVO;
import com.njydsz.project.domain.dto.post.ProjectContractSupplementPostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractSupplementPutDTO;

@RestController
@RequestMapping("/api/v1/project/project/contract/supplement")
@RequiredArgsConstructor
public class ProjectContractSupplementController {
    private final ProjectContractSupplementService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectContractSupplementVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectContractSupplementVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractSupplement> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractSupplementListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ProjectContractSupplement")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractSupplementPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ProjectContractSupplement")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractSupplementPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ProjectContractSupplement")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
