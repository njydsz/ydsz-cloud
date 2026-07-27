package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.server.service.ProjectContractTemplateService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractTemplateVO;
import com.njydsz.project.domain.dto.post.ProjectContractTemplatePostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractTemplatePutDTO;

@RestController
@RequestMapping("/api/v1/project/project/contract/template")
@RequiredArgsConstructor
public class ProjectContractTemplateController {
    private final ProjectContractTemplateService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectContractTemplateVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectContractTemplateVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContractTemplate> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractTemplateListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContractTemplate")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractTemplatePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContractTemplate")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractTemplatePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContractTemplate")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
