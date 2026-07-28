package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectInvoice;
import com.njydsz.project.server.service.ProjectInvoiceService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectInvoiceVO;
import com.njydsz.project.domain.dto.put.ProjectInvoicePutDTO;
import com.njydsz.project.domain.dto.post.ProjectInvoicePostDTO;

/**
 * 项目发票 Controller
 *
 * <p>提供项目发票的 CRUD 接口，包括分页查询、按 ID 查询、创建、更新和删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/invoice")
@RequiredArgsConstructor
public class ProjectInvoiceController {

    private final ProjectInvoiceService service;

    /**
     * 按 ID 查询发票详情
     *
     * @param id 发票主键 ID
     * @return 发票视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectInvoiceVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询发票列表
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页发票视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectInvoiceVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectInvoice> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectInvoiceListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建发票
     *
     * @param dto 发票创建入参
     * @return 是否创建成功
     */
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectInvoice")
    public BaseResponse<Boolean> save(@RequestBody ProjectInvoicePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新发票
     *
     * @param dto 发票更新入参
     * @return 是否更新成功
     */
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectInvoice")
    public BaseResponse<Boolean> update(@RequestBody ProjectInvoicePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除发票
     *
     * @param id 发票主键 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectInvoice")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
