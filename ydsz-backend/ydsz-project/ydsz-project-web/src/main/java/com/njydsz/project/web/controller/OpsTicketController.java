package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.server.service.OpsTicketService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.OpsTicketVO;
import com.njydsz.project.domain.dto.post.OpsTicketPostDTO;
import com.njydsz.project.domain.dto.put.OpsTicketPutDTO;

@RestController
@RequestMapping("/api/v1/project/ops/ticket")
@RequiredArgsConstructor
public class OpsTicketController {
    private final OpsTicketService service;

    @GetMapping("/{id}")
    public BaseResponse<OpsTicketVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    @GetMapping("/page")
    public PageResponse<OpsTicketVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<OpsTicket> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.opsTicketListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create OpsTicket")
    public BaseResponse<Boolean> save(@RequestBody OpsTicketPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update OpsTicket")
    public BaseResponse<Boolean> update(@RequestBody OpsTicketPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete OpsTicket")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
