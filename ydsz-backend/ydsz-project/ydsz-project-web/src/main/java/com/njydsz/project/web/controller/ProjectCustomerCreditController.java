package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.server.service.ProjectCustomerCreditService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectCustomerCreditVO;
import com.njydsz.project.domain.dto.put.ProjectCustomerCreditPutDTO;
import com.njydsz.project.domain.dto.post.ProjectCustomerCreditPostDTO;

@RestController
@RequestMapping("/api/v1/project/project/customer/credit")
@RequiredArgsConstructor
public class ProjectCustomerCreditController {
    private final ProjectCustomerCreditService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectCustomerCreditVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectCustomerCreditVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectCustomerCredit> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectCustomerCreditListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", description="Create ProjectCustomerCredit")
    public BaseResponse<Boolean> save(@RequestBody ProjectCustomerCreditPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", description="Update ProjectCustomerCredit")
    public BaseResponse<Boolean> update(@RequestBody ProjectCustomerCreditPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", description="Delete ProjectCustomerCredit")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
