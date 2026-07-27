package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.server.service.ProjectPaymentService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectPaymentVO;
import com.njydsz.project.domain.dto.post.ProjectPaymentPostDTO;
import com.njydsz.project.domain.dto.put.ProjectPaymentPutDTO;

@RestController
@RequestMapping("/api/v1/project/project/payment")
@RequiredArgsConstructor
public class ProjectPaymentController {
    private final ProjectPaymentService service;

    @GetMapping("/{id}")
    public BaseResponse<ProjectPaymentVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<ProjectPaymentVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectPayment> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectPaymentListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectPayment")
    public BaseResponse<Boolean> save(@RequestBody ProjectPaymentPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectPayment")
    public BaseResponse<Boolean> update(@RequestBody ProjectPaymentPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectPayment")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
