package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.server.service.ProjectContractService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectContractVO;
import com.njydsz.project.domain.dto.post.ProjectContractPostDTO;
import com.njydsz.project.domain.dto.put.ProjectContractPutDTO;

@RestController
@RequestMapping("/api/v1/project/project/contract")
@RequiredArgsConstructor
public class ProjectContractController {
    private final ProjectContractService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectContractVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectContractVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectContract> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectContractListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectContract")
    public BaseResponse<Boolean> save(@RequestBody ProjectContractPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectContract")
    public BaseResponse<Boolean> update(@RequestBody ProjectContractPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectContract")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
