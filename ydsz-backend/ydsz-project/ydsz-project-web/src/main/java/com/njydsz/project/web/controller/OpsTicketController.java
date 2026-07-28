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

/**
 * 运维工单 Controller
 *
 * <p>提供运维工单的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/ops/ticket")
@RequiredArgsConstructor
public class OpsTicketController {

    private final OpsTicketService service;

    /**
     * 按 ID 查询运维工单
     *
     * @param id 工单主键 ID
     * @return 工单视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<OpsTicketVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询运维工单列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页工单视图对象
     */
    @GetMapping("/page")
    public PageResponse<OpsTicketVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<OpsTicket> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.opsTicketListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建运维工单
     *
     * @param dto 工单创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create OpsTicket")
    public BaseResponse<Boolean> save(@RequestBody OpsTicketPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新运维工单
     *
     * @param dto 工单更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update OpsTicket")
    public BaseResponse<Boolean> update(@RequestBody OpsTicketPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除运维工单
     *
     * @param id 工单主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete OpsTicket")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
